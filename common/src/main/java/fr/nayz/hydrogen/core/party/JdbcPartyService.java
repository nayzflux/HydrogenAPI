package fr.nayz.hydrogen.core.party;

import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.api.party.Party;
import fr.nayz.hydrogen.api.party.PartyInvitation;
import fr.nayz.hydrogen.api.party.PartyService;
import fr.nayz.hydrogen.core.database.Database;
import fr.nayz.hydrogen.core.database.Sql;
import fr.nayz.hydrogen.core.redis.RedisCoordinator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcPartyService implements PartyService {
    private static final int MAX_MEMBERS = 12;
    private final Database database;
    private final RedisCoordinator redis;

    public JdbcPartyService(Database database, RedisCoordinator redis) {
        this.database = database;
        this.redis = redis;
    }

    @Override
    public CompletableFuture<Party> create(UUID leader) {
        return database.transaction(connection -> {
            cleanup(connection);
            UUID partyId = UUID.randomUUID();
            try (PreparedStatement statement = Sql.prepare(connection,
                    "INSERT INTO hydrogen_parties(party_id, leader_id, expires_at) "
                            + "VALUES (?, ?, now() + interval '24 hours')", partyId, leader)) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = Sql.prepare(connection,
                    "INSERT INTO hydrogen_party_members(player_id, party_id) VALUES (?, ?)", leader, partyId)) {
                statement.executeUpdate();
            }
            return load(connection, partyId);
        }).thenApply(party -> {
            redis.publish("party", party.id().toString());
            return party;
        });
    }

    @Override
    public CompletableFuture<Optional<Party>> getForPlayer(UUID playerId) {
        return database.read(connection -> {
            UUID partyId = findForPlayer(connection, playerId);
            return partyId == null ? Optional.empty() : Optional.of(load(connection, partyId));
        });
    }

    @Override
    public CompletableFuture<Optional<Party>> get(UUID partyId) {
        return database.read(connection -> {
            try (PreparedStatement statement = Sql.prepare(connection,
                    "SELECT 1 FROM hydrogen_parties WHERE party_id = ? AND expires_at > now()", partyId);
                 ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(load(connection, partyId)) : Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<List<PartyInvitation>> invitations(UUID invitee) {
        return database.read(connection -> {
            List<PartyInvitation> invitations = new ArrayList<>();
            try (PreparedStatement statement = Sql.prepare(connection,
                    "SELECT i.party_id, i.inviter_id, i.expires_at FROM hydrogen_party_invitations i "
                            + "JOIN hydrogen_parties p ON p.party_id = i.party_id "
                            + "WHERE i.invitee_id = ? AND i.expires_at > now() AND p.expires_at > now() "
                            + "ORDER BY i.expires_at, i.inviter_id",
                    invitee);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    invitations.add(new PartyInvitation(Sql.uuid(rows, "party_id"),
                            Sql.uuid(rows, "inviter_id"), invitee,
                            rows.getTimestamp("expires_at").toInstant()));
                }
            }
            return List.copyOf(invitations);
        });
    }

    @Override
    public CompletableFuture<PartyInvitation> invite(UUID inviter, UUID invitee) {
        Sql.require(!inviter.equals(invitee), HydrogenErrorReason.INVALID_INPUT, "Cannot invite yourself");
        return database.transaction(connection -> {
            cleanup(connection);
            UUID partyId = requirePartyForPlayer(connection, inviter);
            Party party = lockAndLoad(connection, partyId);
            Sql.require(party.membersCanInvite() || party.leader().equals(inviter),
                    HydrogenErrorReason.FORBIDDEN, "Only the party leader can invite");
            Sql.require(party.members().size() < MAX_MEMBERS, HydrogenErrorReason.CONFLICT,
                    "Party is full");
            Sql.require(findForPlayer(connection, invitee) == null, HydrogenErrorReason.CONFLICT,
                    "Player already belongs to a party");
            checkInviteLimit(connection, inviter, invitee);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "INSERT INTO hydrogen_party_invitations(party_id, inviter_id, invitee_id, expires_at) "
                            + "VALUES (?, ?, ?, now() + interval '10 minutes') "
                            + "ON CONFLICT (inviter_id, invitee_id) DO UPDATE SET "
                            + "party_id = EXCLUDED.party_id, expires_at = EXCLUDED.expires_at "
                            + "RETURNING expires_at", partyId, inviter, invitee);
                 ResultSet rows = statement.executeQuery()) {
                rows.next();
                advanceInviteLimit(connection, inviter, invitee);
                return new PartyInvitation(partyId, inviter, invitee,
                        rows.getTimestamp(1).toInstant());
            }
        }).thenApply(invitation -> {
            redis.publish("party", invitation.partyId().toString());
            redis.publishSocial("party-invite", invitee, inviter);
            return invitation;
        });
    }

    @Override
    public CompletableFuture<Party> accept(UUID invitee, UUID partyId) {
        return accept(invitee, partyId, null);
    }

    @Override
    public CompletableFuture<Party> accept(UUID invitee, UUID partyId, UUID inviter) {
        return database.transaction(connection -> {
            cleanup(connection);
            Party party = lockAndLoad(connection, partyId);
            Sql.require(party.members().size() < MAX_MEMBERS, HydrogenErrorReason.CONFLICT,
                    "Party is full");
            Sql.require(findForPlayer(connection, invitee) == null, HydrogenErrorReason.CONFLICT,
                    "Player already belongs to a party");
            String query = "DELETE FROM hydrogen_party_invitations "
                    + "WHERE party_id = ? AND invitee_id = ? AND expires_at > now()";
            if (inviter != null) {
                query += " AND inviter_id = ?";
            }
            try (PreparedStatement statement = inviter == null
                    ? Sql.prepare(connection, query, partyId, invitee)
                    : Sql.prepare(connection, query, partyId, invitee, inviter)) {
                Sql.require(statement.executeUpdate() > 0, HydrogenErrorReason.NOT_FOUND,
                        "Invitation expired or missing");
            }
            try (PreparedStatement statement = Sql.prepare(connection,
                    "INSERT INTO hydrogen_party_members(player_id, party_id) VALUES (?, ?)", invitee, partyId)) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = Sql.prepare(connection,
                    "DELETE FROM hydrogen_party_invitations WHERE invitee_id = ?", invitee)) {
                statement.executeUpdate();
            }
            touch(connection, partyId);
            return load(connection, partyId);
        }).thenApply(this::publish);
    }

    @Override
    public CompletableFuture<Void> decline(UUID invitee, UUID partyId, UUID inviter) {
        return database.transaction(connection -> {
            try (PreparedStatement statement = Sql.prepare(connection,
                    "DELETE FROM hydrogen_party_invitations WHERE party_id = ? "
                            + "AND invitee_id = ? AND inviter_id = ? AND expires_at > now()",
                    partyId, invitee, inviter)) {
                Sql.require(statement.executeUpdate() == 1, HydrogenErrorReason.NOT_FOUND,
                        "Invitation expired or missing");
            }
            return null;
        }).thenRun(() -> redis.publishSocial("party-declined", inviter, invitee));
    }

    @Override
    public CompletableFuture<Party> setMembersCanInvite(UUID leader, boolean enabled) {
        return database.transaction(connection -> {
            UUID partyId = requirePartyForPlayer(connection, leader);
            Party party = lockAndLoad(connection, partyId);
            requireLeader(party, leader);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "UPDATE hydrogen_parties SET members_can_invite = ? WHERE party_id = ?",
                    enabled, partyId)) {
                statement.executeUpdate();
            }
            touch(connection, partyId);
            return load(connection, partyId);
        }).thenApply(this::publish);
    }

    @Override
    public CompletableFuture<Void> leave(UUID playerId) {
        return database.transaction(connection -> {
            UUID partyId = requirePartyForPlayer(connection, playerId);
            Party party = lockAndLoad(connection, partyId);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "DELETE FROM hydrogen_party_members WHERE player_id = ?", playerId)) {
                statement.executeUpdate();
            }
            if (party.members().size() == 1) {
                deleteParty(connection, partyId);
            } else if (party.leader().equals(playerId)) {
                UUID replacement = party.members().stream()
                        .filter(member -> !member.equals(playerId))
                        .findFirst()
                        .orElseThrow();
                changeLeader(connection, partyId, replacement);
                touch(connection, partyId);
            } else {
                touch(connection, partyId);
            }
            return party;
        }).thenAccept(party -> {
            redis.publish("party", party.id().toString());
            for (UUID member : party.members()) {
                redis.publishSocial("party-change", member, playerId);
            }
        });
    }

    @Override
    public CompletableFuture<Party> kick(UUID leader, UUID member) {
        Sql.require(!leader.equals(member), HydrogenErrorReason.INVALID_INPUT,
                "Use leave to remove the leader");
        return database.transaction(connection -> {
            UUID partyId = requirePartyForPlayer(connection, leader);
            Party party = lockAndLoad(connection, partyId);
            requireLeader(party, leader);
            Sql.require(party.members().contains(member), HydrogenErrorReason.NOT_FOUND,
                    "Member not in party");
            try (PreparedStatement statement = Sql.prepare(connection,
                    "DELETE FROM hydrogen_party_members WHERE player_id = ?", member)) {
                statement.executeUpdate();
            }
            touch(connection, partyId);
            return load(connection, partyId);
        }).thenApply(party -> {
            redis.publishSocial("party-removed", member, leader);
            return publish(party);
        });
    }

    @Override
    public CompletableFuture<Party> transferLeader(UUID leader, UUID newLeader) {
        return database.transaction(connection -> {
            UUID partyId = requirePartyForPlayer(connection, leader);
            Party party = lockAndLoad(connection, partyId);
            requireLeader(party, leader);
            Sql.require(party.members().contains(newLeader), HydrogenErrorReason.NOT_FOUND,
                    "New leader not in party");
            changeLeader(connection, partyId, newLeader);
            touch(connection, partyId);
            return load(connection, partyId);
        }).thenApply(this::publish);
    }

    @Override
    public CompletableFuture<Void> disband(UUID leader) {
        return database.transaction(connection -> {
            UUID partyId = requirePartyForPlayer(connection, leader);
            Party party = lockAndLoad(connection, partyId);
            requireLeader(party, leader);
            deleteParty(connection, partyId);
            return party;
        }).thenAccept(party -> {
            redis.publish("party", party.id().toString());
            for (UUID member : party.members()) {
                redis.publishSocial("party-disbanded", member, leader);
            }
        });
    }

    @Override
    public CompletableFuture<Void> renew(UUID playerId) {
        return database.transaction(connection -> {
            UUID partyId = findForPlayer(connection, playerId);
            if (partyId != null) {
                lockAndLoad(connection, partyId);
                touch(connection, partyId);
            }
            return null;
        });
    }

    private Party publish(Party party) {
        redis.publish("party", party.id().toString());
        for (UUID member : party.members()) {
            redis.publishSocial("party-change", member, party.leader());
        }
        return party;
    }

    private static void checkInviteLimit(Connection connection, UUID inviter, UUID invitee)
            throws SQLException {
        try (PreparedStatement statement = Sql.prepare(connection,
                "INSERT INTO hydrogen_party_invite_limits(inviter_id, invitee_id, last_sent_at, step) "
                        + "VALUES (?, ?, now() - interval '1 hour', 0) ON CONFLICT DO NOTHING",
                inviter, invitee)) {
            statement.executeUpdate();
        }
        try (PreparedStatement statement = Sql.prepare(connection,
                "SELECT step, EXTRACT(EPOCH FROM (now() - last_sent_at)) AS elapsed "
                        + "FROM hydrogen_party_invite_limits WHERE inviter_id = ? AND invitee_id = ? FOR UPDATE",
                inviter, invitee);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            double elapsed = rows.getDouble("elapsed");
            int step = elapsed >= 600 ? 0 : rows.getInt("step");
            int delay = switch (step) {
                case 1 -> 2;
                case 2 -> 5;
                case 3 -> 10;
                case 4 -> 30;
                default -> 0;
            };
            Sql.require(elapsed >= delay, HydrogenErrorReason.CONFLICT,
                    "Invitation cooldown: wait " + Math.ceil(delay - elapsed) + " seconds");
        }
    }

    private static void advanceInviteLimit(Connection connection, UUID inviter, UUID invitee)
            throws SQLException {
        try (PreparedStatement statement = Sql.prepare(connection,
                "UPDATE hydrogen_party_invite_limits SET "
                        + "step = CASE WHEN last_sent_at < now() - interval '10 minutes' "
                        + "THEN 1 ELSE LEAST(4, step + 1) END, last_sent_at = now() "
                        + "WHERE inviter_id = ? AND invitee_id = ?", inviter, invitee)) {
            statement.executeUpdate();
        }
    }

    private static void cleanup(Connection connection) throws SQLException {
        try (PreparedStatement statement = Sql.prepare(connection,
                "DELETE FROM hydrogen_parties WHERE expires_at <= now()")) {
            statement.executeUpdate();
        }
    }

    private static UUID findForPlayer(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = Sql.prepare(connection,
                "SELECT m.party_id FROM hydrogen_party_members m "
                        + "JOIN hydrogen_parties p ON p.party_id = m.party_id "
                        + "WHERE m.player_id = ? AND p.expires_at > now()", playerId);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? Sql.uuid(rows, "party_id") : null;
        }
    }

    private static UUID requirePartyForPlayer(Connection connection, UUID playerId) throws SQLException {
        UUID partyId = findForPlayer(connection, playerId);
        Sql.require(partyId != null, HydrogenErrorReason.NOT_FOUND, "Player has no party");
        return partyId;
    }

    private static Party lockAndLoad(Connection connection, UUID partyId) throws SQLException {
        try (PreparedStatement statement = Sql.prepare(connection,
                "SELECT party_id FROM hydrogen_parties WHERE party_id = ? AND expires_at > now() FOR UPDATE",
                partyId);
             ResultSet rows = statement.executeQuery()) {
            Sql.require(rows.next(), HydrogenErrorReason.NOT_FOUND, "Party not found or expired");
        }
        return load(connection, partyId);
    }

    private static Party load(Connection connection, UUID partyId) throws SQLException {
        UUID leader;
        Instant expiresAt;
        boolean membersCanInvite;
        try (PreparedStatement statement = Sql.prepare(connection,
                "SELECT leader_id, members_can_invite, expires_at FROM hydrogen_parties WHERE party_id = ?", partyId);
             ResultSet rows = statement.executeQuery()) {
            Sql.require(rows.next(), HydrogenErrorReason.NOT_FOUND, "Party not found");
            leader = Sql.uuid(rows, "leader_id");
            membersCanInvite = rows.getBoolean("members_can_invite");
            expiresAt = rows.getTimestamp("expires_at").toInstant();
        }
        List<UUID> members = new ArrayList<>();
        try (PreparedStatement statement = Sql.prepare(connection,
                "SELECT player_id FROM hydrogen_party_members WHERE party_id = ? ORDER BY joined_at, player_id",
                partyId);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                members.add(Sql.uuid(rows, "player_id"));
            }
        }
        return new Party(partyId, leader, List.copyOf(members), membersCanInvite, expiresAt);
    }

    private static void requireLeader(Party party, UUID leader) {
        Sql.require(party.leader().equals(leader), HydrogenErrorReason.FORBIDDEN,
                "Only the party leader can do this");
    }

    private static void touch(Connection connection, UUID partyId) throws SQLException {
        try (PreparedStatement statement = Sql.prepare(connection,
                "UPDATE hydrogen_parties SET expires_at = now() + interval '24 hours' WHERE party_id = ?",
                partyId)) {
            statement.executeUpdate();
        }
    }

    private static void changeLeader(Connection connection, UUID partyId, UUID newLeader) throws SQLException {
        try (PreparedStatement statement = Sql.prepare(connection,
                "UPDATE hydrogen_parties SET leader_id = ? WHERE party_id = ?", newLeader, partyId)) {
            statement.executeUpdate();
        }
    }

    private static void deleteParty(Connection connection, UUID partyId) throws SQLException {
        try (PreparedStatement statement = Sql.prepare(connection,
                "DELETE FROM hydrogen_parties WHERE party_id = ?", partyId)) {
            statement.executeUpdate();
        }
    }
}
