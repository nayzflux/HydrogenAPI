package fr.nayz.hydrogen.core.matchmaking;

import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.api.HydrogenException;
import fr.nayz.hydrogen.api.matchmaking.MatchmakingQueue;
import fr.nayz.hydrogen.api.matchmaking.QueueState;
import fr.nayz.hydrogen.api.matchmaking.QueueStateService;
import fr.nayz.hydrogen.api.matchmaking.QueueStatus;
import fr.nayz.hydrogen.core.database.Database;
import fr.nayz.hydrogen.core.redis.RedisCoordinator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class JdbcQueueStateService implements QueueStateService {
    private final Database database;
    private final RedisCoordinator redis;

    public JdbcQueueStateService(Database database, RedisCoordinator redis) {
        this.database = database;
        this.redis = redis;
    }

    @Override
    public CompletableFuture<Optional<QueueState>> get(UUID playerId) {
        return database.read(connection -> {
            UUID ticketId = ticketForPlayer(connection, playerId);
            return ticketId == null ? Optional.empty() : Optional.of(readTicket(connection, ticketId));
        });
    }

    @Override
    public CompletableFuture<Map<UUID, QueueState>> getMany(Collection<UUID> playerIds) {
        List<UUID> ids = playerIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return database.read(connection -> {
            Map<UUID, UUID> ticketByPlayer = new HashMap<>();
            Map<UUID, QueueState> tickets = new HashMap<>();
            Map<UUID, Set<UUID>> members = new HashMap<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT requested.player_id, ticket.ticket_id, ticket.queue_group_id, "
                            + "ticket.party_id, ticket.endercloud_entry_id, ticket.entered_at, "
                            + "ticket.status, member.player_id "
                            + "FROM hydrogen_queue_member requested "
                            + "JOIN hydrogen_queue_ticket ticket ON ticket.ticket_id = requested.ticket_id "
                            + "JOIN hydrogen_queue_member member ON member.ticket_id = ticket.ticket_id "
                            + "WHERE requested.player_id = ANY (?)")) {
                java.sql.Array requested = connection.createArrayOf("uuid", ids.toArray(UUID[]::new));
                query.setArray(1, requested);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        UUID playerId = rows.getObject(1, UUID.class);
                        UUID ticketId = rows.getObject(2, UUID.class);
                        ticketByPlayer.put(playerId, ticketId);
                        if (!tickets.containsKey(ticketId)) {
                            QueueState state = new QueueState(
                                    ticketId,
                                    MatchmakingQueue.fromGroupId(rows.getString(3)),
                                    rows.getString(4),
                                    List.of(),
                                    rows.getString(5),
                                    rows.getTimestamp(6).toInstant(),
                                    QueueStatus.valueOf(rows.getString(7)));
                            tickets.put(ticketId, state);
                        }
                        members.computeIfAbsent(ticketId, ignored -> new HashSet<>())
                                .add(rows.getObject(8, UUID.class));
                    }
                }
                requested.free();
            }
            Map<UUID, QueueState> result = new HashMap<>();
            for (Map.Entry<UUID, UUID> entry : ticketByPlayer.entrySet()) {
                QueueState base = tickets.get(entry.getValue());
                result.put(entry.getKey(), new QueueState(
                        base.ticketId(), base.queue(), base.partyId(),
                        members.get(base.ticketId()).stream().sorted().toList(),
                        base.enderCloudEntryId(), base.enteredAt(), base.status()));
            }
            return Map.copyOf(result);
        });
    }

    public CompletableFuture<QueueState> createOrFind(MatchmakingQueue queue, String partyId, List<UUID> players) {
        List<UUID> members = players.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        if (members.isEmpty() || members.size() > 12) {
            return CompletableFuture.failedFuture(new HydrogenException(
                    HydrogenErrorReason.CONFLICT, "A queue requires between one and twelve players"));
        }
        return database.transaction(connection -> {
            for (UUID playerId : members) {
                lockPlayer(connection, playerId);
            }
            Set<UUID> existing = new HashSet<>();
            for (UUID playerId : members) {
                UUID ticketId = ticketForPlayer(connection, playerId);
                if (ticketId != null) {
                    existing.add(ticketId);
                }
            }
            if (existing.size() == 1) {
                QueueState current = readTicket(connection, existing.iterator().next());
                if (current.status() != QueueStatus.MATCH_FOUND
                        && current.queue() == queue
                        && current.partyId().equals(partyId)
                        && new HashSet<>(current.members()).equals(new HashSet<>(members))) {
                    return current;
                }
            }
            for (UUID ticketId : existing) {
                QueueState current = readTicket(connection, ticketId);
                if (current.status() != QueueStatus.MATCH_FOUND) {
                    throw new HydrogenException(HydrogenErrorReason.CONFLICT,
                            "A player already has a different active queue ticket");
                }
                deleteTicket(connection, ticketId);
            }
            UUID ticketId = UUID.randomUUID();
            Instant now = Instant.now();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hydrogen_queue_ticket(ticket_id, queue_group_id, party_id, status, entered_at, "
                            + "updated_at, confirmation_due_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                insert.setObject(1, ticketId);
                insert.setString(2, queue.groupId());
                insert.setString(3, partyId);
                insert.setString(4, QueueStatus.JOINING.name());
                insert.setTimestamp(5, Timestamp.from(now));
                insert.setTimestamp(6, Timestamp.from(now));
                insert.setTimestamp(7, Timestamp.from(now));
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hydrogen_queue_member(player_id, ticket_id) VALUES (?, ?)")) {
                for (UUID playerId : members) {
                    insert.setObject(1, playerId);
                    insert.setObject(2, ticketId);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            return readTicket(connection, ticketId);
        }).thenApply(state -> {
            publish(state);
            return state;
        });
    }

    public CompletableFuture<Optional<QueueLease>> claimConfirmation(UUID ticketId) {
        return database.transaction(connection -> {
            UUID token = UUID.randomUUID();
            Instant now = Instant.now();
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hydrogen_queue_ticket SET lease_token = ?, lease_until = ?, "
                            + "confirmation_due_at = ?, status = ?, updated_at = ? "
                            + "WHERE ticket_id = ? AND status IN ('JOINING', 'QUEUED', 'VERIFYING') "
                            + "AND confirmation_due_at <= ? AND (lease_until IS NULL OR lease_until <= ?)")) {
                update.setObject(1, token);
                update.setTimestamp(2, Timestamp.from(now.plusSeconds(45)));
                update.setTimestamp(3, Timestamp.from(now.plusSeconds(60)));
                update.setString(4, QueueStatus.VERIFYING.name());
                update.setTimestamp(5, Timestamp.from(now));
                update.setObject(6, ticketId);
                update.setTimestamp(7, Timestamp.from(now));
                update.setTimestamp(8, Timestamp.from(now));
                if (update.executeUpdate() == 0) {
                    return Optional.<QueueLease>empty();
                }
            }
            return Optional.of(new QueueLease(readTicket(connection, ticketId), token));
        }).thenApply(lease -> {
            lease.ifPresent(value -> publish(value.state()));
            return lease;
        });
    }

    public CompletableFuture<QueueState> confirm(QueueLease lease, String entryId, boolean selected) {
        return database.transaction(connection -> {
            QueueState previous = readTicket(connection, lease.state().ticketId());
            Instant enteredAt = previous.enteredAt();
            if (previous.enderCloudEntryId() != null && !previous.enderCloudEntryId().equals(entryId)) {
                enteredAt = Instant.now();
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hydrogen_queue_ticket SET endercloud_entry_id = ?, status = ?, entered_at = ?, "
                            + "updated_at = ?, lease_token = NULL, lease_until = NULL "
                            + "WHERE ticket_id = ? AND lease_token = ?")) {
                update.setString(1, entryId);
                update.setString(2, selected ? QueueStatus.MATCH_FOUND.name() : QueueStatus.QUEUED.name());
                update.setTimestamp(3, Timestamp.from(enteredAt));
                update.setTimestamp(4, Timestamp.from(Instant.now()));
                update.setObject(5, lease.state().ticketId());
                update.setObject(6, lease.token());
                if (update.executeUpdate() == 0) {
                    return previous;
                }
            }
            return readTicket(connection, lease.state().ticketId());
        }).thenApply(state -> {
            publish(state);
            return state;
        });
    }

    public CompletableFuture<Void> confirmationFailed(QueueLease lease) {
        return database.transaction(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hydrogen_queue_ticket SET lease_token = NULL, lease_until = NULL, "
                            + "status = 'VERIFYING', updated_at = ? WHERE ticket_id = ? AND lease_token = ?")) {
                update.setTimestamp(1, Timestamp.from(Instant.now()));
                update.setObject(2, lease.state().ticketId());
                update.setObject(3, lease.token());
                update.executeUpdate();
            }
            return null;
        }).thenAccept(ignored -> publish(lease.state()));
    }

    public CompletableFuture<Void> confirmationRejected(QueueLease lease) {
        return database.transaction(connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT lease_token FROM hydrogen_queue_ticket WHERE ticket_id = ? FOR UPDATE")) {
                query.setObject(1, lease.state().ticketId());
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next() || !lease.token().equals(rows.getObject(1, UUID.class))) {
                        return false;
                    }
                }
            }
            deleteTicket(connection, lease.state().ticketId());
            return true;
        }).thenAccept(deleted -> {
            if (deleted) {
                publish(lease.state());
            }
        });
    }

    public CompletableFuture<Optional<QueueLease>> claimLeave(UUID playerId) {
        return database.transaction(connection -> {
            UUID ticketId = ticketForPlayer(connection, playerId);
            if (ticketId == null) {
                return Optional.<QueueLease>empty();
            }
            UUID token = UUID.randomUUID();
            Instant now = Instant.now();
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hydrogen_queue_ticket SET lease_token = ?, lease_until = ?, status = 'LEAVING', "
                            + "updated_at = ? WHERE ticket_id = ? AND status <> 'MATCH_FOUND' "
                            + "AND (lease_until IS NULL OR lease_until <= ?)")) {
                update.setObject(1, token);
                update.setTimestamp(2, Timestamp.from(now.plusSeconds(45)));
                update.setTimestamp(3, Timestamp.from(now));
                update.setObject(4, ticketId);
                update.setTimestamp(5, Timestamp.from(now));
                if (update.executeUpdate() == 0) {
                    return Optional.<QueueLease>empty();
                }
            }
            return Optional.of(new QueueLease(readTicket(connection, ticketId), token));
        }).thenApply(lease -> {
            lease.ifPresent(value -> publish(value.state()));
            return lease;
        });
    }

    public CompletableFuture<Void> finishLeave(QueueLease lease, boolean accepted) {
        return database.transaction(connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT lease_token FROM hydrogen_queue_ticket WHERE ticket_id = ? FOR UPDATE")) {
                query.setObject(1, lease.state().ticketId());
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next() || !lease.token().equals(rows.getObject(1, UUID.class))) {
                        return null;
                    }
                }
            }
            if (accepted) {
                deleteTicket(connection, lease.state().ticketId());
            } else {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE hydrogen_queue_ticket SET lease_token = NULL, lease_until = NULL, "
                                + "status = 'VERIFYING', updated_at = ? WHERE ticket_id = ?")) {
                    update.setTimestamp(1, Timestamp.from(Instant.now()));
                    update.setObject(2, lease.state().ticketId());
                    update.executeUpdate();
                }
            }
            return null;
        }).thenAccept(ignored -> publish(lease.state()));
    }

    public CompletableFuture<Void> markFound(UUID ticketId, String entryId) {
        return database.transaction(connection -> {
            QueueState previous = readTicket(connection, ticketId);
            if (!entryId.equals(previous.enderCloudEntryId())
                    || previous.status() == QueueStatus.MATCH_FOUND) {
                return null;
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hydrogen_queue_ticket SET status = 'MATCH_FOUND', updated_at = ?, "
                            + "lease_token = NULL, lease_until = NULL WHERE ticket_id = ?")) {
                update.setTimestamp(1, Timestamp.from(Instant.now()));
                update.setObject(2, ticketId);
                update.executeUpdate();
            }
            return previous;
        }).thenAccept(state -> {
            if (state != null) {
                publish(state);
            }
        });
    }

    public CompletableFuture<Void> clearOnDisconnect(UUID playerId) {
        return database.transaction(connection -> {
            lockPlayer(connection, playerId);
            UUID ticketId = ticketForPlayer(connection, playerId);
            if (ticketId == null) {
                return null;
            }
            QueueState state = readTicket(connection, ticketId);
            deleteTicket(connection, ticketId);
            return state;
        }).thenAccept(state -> {
            if (state != null) {
                publish(state);
            }
        });
    }

    public CompletableFuture<Boolean> claimFoundNotice(UUID ticketId, UUID playerId) {
        return database.transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hydrogen_queue_found_notice(ticket_id, player_id) "
                            + "SELECT ticket.ticket_id, member.player_id "
                            + "FROM hydrogen_queue_ticket ticket "
                            + "JOIN hydrogen_queue_member member ON member.ticket_id = ticket.ticket_id "
                            + "WHERE ticket.ticket_id = ? AND member.player_id = ? "
                            + "AND ticket.status = 'MATCH_FOUND' "
                            + "ON CONFLICT DO NOTHING")) {
                insert.setObject(1, ticketId);
                insert.setObject(2, playerId);
                return insert.executeUpdate() == 1;
            }
        });
    }

    private void publish(QueueState state) {
        for (UUID member : state.members()) {
            redis.publish("queue", member.toString());
        }
    }

    private static void lockPlayer(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            lock.setLong(1, playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits());
            lock.execute();
        }
    }

    private static UUID ticketForPlayer(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT ticket_id FROM hydrogen_queue_member WHERE player_id = ?")) {
            query.setObject(1, playerId);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? rows.getObject(1, UUID.class) : null;
            }
        }
    }

    private static QueueState readTicket(Connection connection, UUID ticketId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT queue_group_id, party_id, endercloud_entry_id, entered_at, status "
                        + "FROM hydrogen_queue_ticket WHERE ticket_id = ?")) {
            query.setObject(1, ticketId);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    throw new HydrogenException(HydrogenErrorReason.NOT_FOUND, "Queue ticket no longer exists");
                }
                MatchmakingQueue queue = MatchmakingQueue.fromGroupId(rows.getString(1));
                String partyId = rows.getString(2);
                String entryId = rows.getString(3);
                Instant enteredAt = rows.getTimestamp(4).toInstant();
                QueueStatus status = QueueStatus.valueOf(rows.getString(5));
                List<UUID> members = new ArrayList<>();
                try (PreparedStatement memberQuery = connection.prepareStatement(
                        "SELECT player_id FROM hydrogen_queue_member WHERE ticket_id = ? ORDER BY player_id")) {
                    memberQuery.setObject(1, ticketId);
                    try (ResultSet memberRows = memberQuery.executeQuery()) {
                        while (memberRows.next()) {
                            members.add(memberRows.getObject(1, UUID.class));
                        }
                    }
                }
                return new QueueState(ticketId, queue, partyId, members, entryId, enteredAt, status);
            }
        }
    }

    private static void deleteTicket(Connection connection, UUID ticketId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM hydrogen_queue_ticket WHERE ticket_id = ?")) {
            delete.setObject(1, ticketId);
            delete.executeUpdate();
        }
    }
}
