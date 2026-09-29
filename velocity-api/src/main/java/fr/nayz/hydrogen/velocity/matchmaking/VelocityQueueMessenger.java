package fr.nayz.hydrogen.velocity.matchmaking;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import fr.nayz.hydrogen.api.matchmaking.QueueState;
import fr.nayz.hydrogen.api.matchmaking.QueueStatus;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class VelocityQueueMessenger implements AutoCloseable {
    private final ProxyServer proxy;
    private final CoreHydrogenApi core;
    private final Logger logger;
    private final Map<UUID, UUID> announcedMatches = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingServerChecks = new ConcurrentHashMap<>();
    private final AtomicLong nextServerCheck = new AtomicLong();
    private Object plugin;
    private ScheduledTask reminderTask;

    public VelocityQueueMessenger(ProxyServer proxy, CoreHydrogenApi core, Logger logger) {
        this.proxy = proxy;
        this.core = core;
        this.logger = logger;
    }

    VelocityQueueMessenger(ProxyServer proxy, CoreHydrogenApi core, Logger logger, Object plugin) {
        this(proxy, core, logger);
        this.plugin = plugin;
    }

    public void start(Object plugin) {
        this.plugin = plugin;
        reminderTask = proxy.getScheduler().buildTask(plugin, this::remindOnlinePlayers)
                .delay(15, TimeUnit.SECONDS)
                .repeat(15, TimeUnit.SECONDS)
                .schedule();
        core.events().onQueueChange(this::changed);
    }

    public void serverConnected(Player player, String serverId) {
        UUID playerId = player.getUniqueId();
        long checkId = nextServerCheck.incrementAndGet();
        pendingServerChecks.put(playerId, checkId);
        proxy.getScheduler().buildTask(plugin,
                        () -> checkServerChange(player, serverId, checkId))
                .delay(3, TimeUnit.SECONDS)
                .schedule();
    }

    private void checkServerChange(Player player, String serverId, long checkId) {
        UUID playerId = player.getUniqueId();
        if (!sameServerAndCheck(player, serverId, checkId)) {
            pendingServerChecks.remove(playerId, checkId);
            return;
        }
        core.queueStates().get(playerId).whenComplete((found, error) -> {
            if (!pendingServerChecks.remove(playerId, checkId)
                    || !isConnectedTo(player, serverId)) {
                return;
            }
            if (error != null) {
                logger.warn("Queue state read failed after server change for {}", playerId, error);
                return;
            }
            if (found.isEmpty()) {
                announcedMatches.remove(playerId);
                return;
            }
            QueueState state = found.get();
            if (state.status() == QueueStatus.MATCH_FOUND) {
                announceFound(player, state);
            } else if (!state.ticketId().equals(announcedMatches.get(playerId))) {
                player.sendMessage(Component.text("File conservée après le changement de serveur : ", NamedTextColor.YELLOW)
                        .append(Component.text(state.queue().displayName(), NamedTextColor.YELLOW)));
            }
        });
    }

    private boolean sameServerAndCheck(Player player, String serverId, long checkId) {
        return checkId == pendingServerChecks.getOrDefault(player.getUniqueId(), -1L)
                && isConnectedTo(player, serverId);
    }

    private boolean isConnectedTo(Player player, String serverId) {
        return player.isActive() && player.getCurrentServer()
                .map(connection -> connection.getServer().getServerInfo().getName())
                .filter(serverId::equals)
                .isPresent();
    }

    public void disconnected(UUID playerId) {
        announcedMatches.remove(playerId);
        pendingServerChecks.remove(playerId);
    }

    private void changed(UUID playerId) {
        proxy.getPlayer(playerId).ifPresent(player ->
                core.queueStates().get(playerId).whenComplete((found, error) -> {
                    if (error != null) {
                        logger.warn("Queue notification lookup failed for {}", playerId, error);
                        return;
                    }
                    if (found.isPresent() && found.get().status() == QueueStatus.MATCH_FOUND) {
                        pendingServerChecks.remove(playerId);
                        announceFound(player, found.get());
                    }
                }));
    }

    private void remindOnlinePlayers() {
        Collection<Player> players = proxy.getAllPlayers();
        if (players.isEmpty()) {
            return;
        }
        List<UUID> ids = players.stream().map(Player::getUniqueId).toList();
        core.queueStates().getMany(ids).whenComplete((states, error) -> {
            if (error != null) {
                logger.warn("Queue reminder batch failed", error);
                return;
            }
            for (Player player : players) {
                if (!player.isActive()) {
                    continue;
                }
                if (pendingServerChecks.containsKey(player.getUniqueId())) {
                    continue;
                }
                QueueState state = states.get(player.getUniqueId());
                if (state == null) {
                    announcedMatches.remove(player.getUniqueId());
                    continue;
                }
                if (state.status() == QueueStatus.MATCH_FOUND) {
                    announceFound(player, state);
                    continue;
                }
                if (state.ticketId().equals(announcedMatches.get(player.getUniqueId()))) {
                    continue;
                }
                player.sendMessage(reminder(state));
            }
        });
    }

    private Component reminder(QueueState state) {
        Duration elapsed = state.elapsedAt(Instant.now());
        String waiting = String.format("%02d:%02d", elapsed.toMinutes(), elapsed.toSecondsPart());
        Component message = Component.text("File ", NamedTextColor.YELLOW)
                .append(Component.text(state.queue().displayName(), NamedTextColor.YELLOW))
                .append(Component.text(" • attente " + waiting, NamedTextColor.GRAY));
        if (state.status() == QueueStatus.VERIFYING || state.status() == QueueStatus.JOINING) {
            return message.append(Component.text(" • vérification en cours", NamedTextColor.YELLOW));
        }
        return message;
    }

    private void announceFound(Player player, QueueState state) {
        if (!player.isActive()) {
            return;
        }
        UUID previous = announcedMatches.putIfAbsent(player.getUniqueId(), state.ticketId());
        if (previous != null && previous.equals(state.ticketId())) {
            return;
        }
        if (previous != null) {
            announcedMatches.put(player.getUniqueId(), state.ticketId());
        }
        core.queueStore().claimFoundNotice(state.ticketId(), player.getUniqueId())
                .whenComplete((claimed, error) -> {
                    if (error != null) {
                        announcedMatches.remove(player.getUniqueId(), state.ticketId());
                        logger.warn("Match-found announcement claim failed for {}",
                                player.getUniqueId(), error);
                        return;
                    }
                    if (claimed && player.isActive()) {
                        player.sendMessage(Component.text("Partie trouvée ! ", NamedTextColor.GREEN)
                                .append(Component.text(state.queue().displayName(), NamedTextColor.GREEN)));
                    }
                });
    }

    @Override
    public void close() {
        if (reminderTask != null) {
            reminderTask.cancel();
        }
        announcedMatches.clear();
        pendingServerChecks.clear();
    }
}
