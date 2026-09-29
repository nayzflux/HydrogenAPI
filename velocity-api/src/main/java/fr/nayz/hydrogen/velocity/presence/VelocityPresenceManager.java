package fr.nayz.hydrogen.velocity.presence;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.UUID;

public final class VelocityPresenceManager implements AutoCloseable {
    private final ProxyServer proxy;
    private final CoreHydrogenApi core;
    private final Logger logger;
    private final String proxyId;
    private final Map<UUID, UUID> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingLeaves = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> loginCleanup = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private ScheduledTask heartbeat;
    private ScheduledTask partyRenewal;

    public VelocityPresenceManager(ProxyServer proxy, CoreHydrogenApi core, Logger logger, String proxyId) {
        this.proxy = proxy;
        this.core = core;
        this.logger = logger;
        this.proxyId = proxyId;
    }

    public void start(Object plugin) {
        heartbeat = proxy.getScheduler().buildTask(plugin, this::renewOnlinePlayers)
                .delay(15, TimeUnit.SECONDS)
                .repeat(15, TimeUnit.SECONDS)
                .schedule();
        partyRenewal = proxy.getScheduler().buildTask(plugin, this::renewParties)
                .delay(5, TimeUnit.MINUTES)
                .repeat(5, TimeUnit.MINUTES)
                .schedule();
    }

    public CompletableFuture<Void> postLogin(Player player) {
        if (closed.get()) {
            return CompletableFuture.completedFuture(null);
        }
        UUID playerId = player.getUniqueId();
        UUID sessionId = UUID.randomUUID();
        sessions.put(playerId, sessionId);
        core.directory().remember(playerId, player.getUsername())
                .exceptionally(error -> logFailure("Name update", playerId, error));
        core.presence().claim(playerId, proxyId, "connecting", sessionId)
                .thenCompose(ignored -> core.directory().claim(playerId, player.getUsername(), sessionId))
                .exceptionally(error -> logFailure("Presence claim", playerId, error));
        CompletableFuture<Void> partyCleanup = core.parties().getForPlayer(playerId)
                .thenCompose(found -> found.isPresent()
                        ? core.parties().leave(playerId)
                        : CompletableFuture.completedFuture(null));
        CompletableFuture<Void> queueCleanup = core.queueStore().clearOnDisconnect(playerId);
        CompletableFuture<Void> cleanup = CompletableFuture.allOf(partyCleanup, queueCleanup)
                .exceptionally(error -> logFailure("Stale social or queue cleanup", playerId, error));
        loginCleanup.put(playerId, cleanup);
        return cleanup;
    }

    public CompletableFuture<Void> ready(UUID playerId) {
        return loginCleanup.getOrDefault(playerId, CompletableFuture.completedFuture(null));
    }

    public void serverConnected(Player player, String serverId) {
        UUID playerId = player.getUniqueId();
        UUID sessionId = sessions.get(playerId);
        if (sessionId != null) {
            core.presence().update(playerId, proxyId, serverId, sessionId)
                    .exceptionally(error -> logFailure("Presence update", playerId, error));
        }
    }

    public void disconnect(Player player) {
        UUID playerId = player.getUniqueId();
        UUID sessionId = sessions.remove(playerId);
        if (sessionId == null) {
            return;
        }
        CompletableFuture<Void> cleanup = loginCleanup.remove(playerId);
        CompletableFuture<Void> clearPresenceAndQueue = core.events()
                .clearPresenceIfOwned(playerId, sessionId)
                .thenCompose(owned -> owned
                        ? core.queueStore().clearOnDisconnect(playerId)
                        : CompletableFuture.completedFuture(null))
                .exceptionally(error -> logFailure("Presence or queue clear", playerId, error));
        CompletableFuture<Void> clearName = core.directory().clear(playerId, player.getUsername(), sessionId)
                .exceptionally(error -> logFailure("Name clear", playerId, error));
        CompletableFuture<Void> leaveParty = (cleanup == null
                ? CompletableFuture.<Void>completedFuture(null)
                : cleanup.handle((ignored, error) -> null))
                .thenCompose(ignored -> leaveParty(playerId));
        CompletableFuture<Void> completed = CompletableFuture.allOf(
                clearPresenceAndQueue, clearName, leaveParty);
        pendingLeaves.put(playerId, completed);
        completed.whenComplete((ignored, error) -> pendingLeaves.remove(playerId, completed));
    }

    private void renewOnlinePlayers() {
        for (Player player : proxy.getAllPlayers()) {
            UUID playerId = player.getUniqueId();
            UUID sessionId = sessions.get(playerId);
            if (sessionId == null) {
                continue;
            }
            String serverId = player.getCurrentServer()
                    .map(connection -> connection.getServer().getServerInfo().getName())
                    .orElse("connecting");
            core.presence().update(playerId, proxyId, serverId, sessionId)
                    .exceptionally(error -> logFailure("Presence heartbeat", playerId, error));
            core.directory().renew(playerId, player.getUsername(), sessionId)
                    .exceptionally(error -> logFailure("Name heartbeat", playerId, error));
        }
    }

    private void renewParties() {
        for (Player player : proxy.getAllPlayers()) {
            UUID playerId = player.getUniqueId();
            core.parties().renew(playerId)
                    .exceptionally(error -> logFailure("Party renewal", playerId, error));
        }
    }

    private Void logFailure(String action, UUID playerId, Throwable error) {
        logger.warn("{} failed for {}", action, playerId, error);
        return null;
    }

    private CompletableFuture<Void> leaveParty(UUID playerId) {
        return core.parties().getForPlayer(playerId)
                .thenCompose(found -> found.isPresent()
                        ? core.parties().leave(playerId)
                        : CompletableFuture.completedFuture(null))
                .exceptionally(error -> logFailure("Party leave", playerId, error));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (heartbeat != null) {
            heartbeat.cancel();
        }
        if (partyRenewal != null) {
            partyRenewal.cancel();
        }
        for (Player player : proxy.getAllPlayers()) {
            disconnect(player);
        }
        CompletableFuture<?>[] leaves = pendingLeaves.values().toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(leaves).orTimeout(5, TimeUnit.SECONDS).join();
        } catch (CompletionException exception) {
            logger.warn("Timed out or failed while leaving parties at shutdown", exception);
        }
    }
}
