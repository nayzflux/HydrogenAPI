package fr.nayz.hydrogen.velocity.rank;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import fr.nayz.hydrogen.api.account.PermissionPlatform;
import fr.nayz.hydrogen.api.account.Rank;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class VelocityRankManager implements AutoCloseable {
    private final ProxyServer proxy;
    private final CoreHydrogenApi core;
    private final Logger logger;
    private final Map<UUID, Set<String>> permissions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> refreshTokens = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private ScheduledTask refreshTask;

    public VelocityRankManager(ProxyServer proxy, CoreHydrogenApi core, Logger logger) {
        this.proxy = proxy;
        this.core = core;
        this.logger = logger;
    }

    public void start(Object plugin) {
        core.events().onRankChange(value -> {
            try {
                UUID playerId = UUID.fromString(value);
                if (permissions.containsKey(playerId)) {
                    loadRank(playerId);
                }
            } catch (IllegalArgumentException exception) {
                logger.warn("Invalid rank event: {}", value);
            }
        });
        refreshTask = proxy.getScheduler().buildTask(plugin, this::refreshOnlineRanks)
                .delay(60, TimeUnit.SECONDS)
                .repeat(60, TimeUnit.SECONDS)
                .schedule();
    }

    public EventTask checkLogin(LoginEvent event) {
        Player player = event.getPlayer();
        return EventTask.resumeWhenComplete(
                core.accounts().getOrCreate(player.getUniqueId()).handle((account, error) -> {
                    if (error != null) {
                        logger.warn("Account loading failed for {}", player.getUniqueId(), error);
                        event.setResult(ResultedEvent.ComponentResult.denied(Component.text(
                                "Your account is temporarily unavailable. Please reconnect.")));
                    } else {
                        UUID playerId = player.getUniqueId();
                        refreshTokens.put(playerId, sequence.incrementAndGet());
                        cacheRank(playerId, account.rank());
                    }
                    return null;
                })
        );
    }

    public void setupPermissions(PermissionsSetupEvent event) {
        if (!(event.getSubject() instanceof Player player)) {
            return;
        }
        PermissionProvider previous = event.getProvider();
        event.setProvider(subject -> {
            var fallback = previous.createFunction(subject);
            return permission -> {
                Set<String> granted = permissions.getOrDefault(player.getUniqueId(), Set.of());
                return granted.contains(permission) ? Tristate.TRUE : fallback.getPermissionValue(permission);
            };
        });
    }

    public void loadRank(UUID playerId) {
        long token = sequence.incrementAndGet();
        refreshTokens.put(playerId, token);
        core.rankReader().getMany(Set.of(playerId)).whenComplete((ranks, error) -> {
            if (error != null) {
                logger.warn("Rank loading failed for {}", playerId, error);
                return;
            }
            applyIfCurrent(playerId, ranks.get(playerId), token);
        });
    }

    private void refreshOnlineRanks() {
        Map<UUID, Long> requested = new HashMap<>();
        for (Player player : proxy.getAllPlayers()) {
            UUID playerId = player.getUniqueId();
            long token = sequence.incrementAndGet();
            refreshTokens.put(playerId, token);
            requested.put(playerId, token);
        }
        if (requested.isEmpty()) {
            return;
        }
        core.rankReader().getMany(requested.keySet()).whenComplete((ranks, error) -> {
            if (error != null) {
                logger.warn("Rank refresh failed", error);
                return;
            }
            for (Map.Entry<UUID, Rank> entry : ranks.entrySet()) {
                UUID playerId = entry.getKey();
                applyIfCurrent(playerId, entry.getValue(), requested.get(playerId));
            }
        });
    }

    private void applyIfCurrent(UUID playerId, Rank rank, long token) {
        if (rank == null || !Long.valueOf(token).equals(refreshTokens.get(playerId))) {
            return;
        }
        if (proxy.getPlayer(playerId).isPresent()) {
            cacheRank(playerId, rank);
        }
    }

    void cacheRank(UUID playerId, Rank rank) {
        permissions.put(playerId, rank.permissionNodes(PermissionPlatform.VELOCITY));
    }

    public void remove(UUID playerId) {
        permissions.remove(playerId);
        refreshTokens.remove(playerId);
    }

    @Override
    public void close() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        permissions.clear();
        refreshTokens.clear();
    }
}
