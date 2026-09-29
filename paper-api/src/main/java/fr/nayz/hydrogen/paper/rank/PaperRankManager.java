package fr.nayz.hydrogen.paper.rank;

import fr.nayz.hydrogen.api.account.PermissionPlatform;
import fr.nayz.hydrogen.api.account.Rank;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class PaperRankManager implements AutoCloseable {
    private final JavaPlugin plugin;
    private final CoreHydrogenApi core;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Map<UUID, Rank> activeRanks = new ConcurrentHashMap<>();
    private final Map<UUID, Rank> preparedRanks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> refreshTokens = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private BukkitTask refreshTask;

    public PaperRankManager(JavaPlugin plugin, CoreHydrogenApi core) {
        this.plugin = plugin;
        this.core = core;
    }

    public void start() {
        core.events().onRankChange(value -> {
            try {
                UUID playerId = UUID.fromString(value);
                if (refreshTokens.containsKey(playerId)) {
                    refreshRank(playerId);
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid rank event: " + value);
            }
        });
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin,
                this::refreshOnlineRanks, 1200L, 1200L);
    }

    public void prepare(AsyncPlayerPreLoginEvent event) {
        UUID playerId = event.getUniqueId();
        try {
            Rank rank = core.accounts().getOrCreate(playerId)
                    .get(5, TimeUnit.SECONDS)
                    .rank();
            preparedRanks.put(playerId, rank);
        } catch (Exception exception) {
            plugin.getLogger().warning("Account loading failed for " + playerId
                    + ": " + exception.getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("Your account is temporarily unavailable. Please reconnect."));
        }
    }

    public void onJoin(Player player) {
        UUID playerId = player.getUniqueId();
        refreshTokens.put(playerId, sequence.incrementAndGet());
        Rank prepared = preparedRanks.remove(playerId);
        if (prepared == null) {
            refreshRank(playerId);
            return;
        }
        applyRank(player, prepared);
    }

    public void onQuit(Player player) {
        UUID playerId = player.getUniqueId();
        preparedRanks.remove(playerId);
        refreshTokens.remove(playerId);
        activeRanks.remove(playerId);
        PermissionAttachment attachment = attachments.remove(playerId);
        if (attachment != null) {
            player.removeAttachment(attachment);
        }
    }

    public Rank rankFor(UUID playerId) {
        return activeRanks.getOrDefault(playerId, Rank.PLAYER);
    }

    private void refreshRank(UUID playerId) {
        long token = sequence.incrementAndGet();
        refreshTokens.put(playerId, token);
        core.rankReader().getMany(List.of(playerId)).whenComplete((ranks, error) -> {
            if (error != null) {
                plugin.getLogger().warning("Rank loading failed for " + playerId
                        + ": " + error.getMessage());
                return;
            }
            Rank rank = ranks.get(playerId);
            if (rank != null) {
                applyOnMainThread(playerId, rank, token);
            }
        });
    }

    private void refreshOnlineRanks() {
        Map<UUID, Long> requested = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
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
                plugin.getLogger().warning("Rank refresh failed: " + error.getMessage());
                return;
            }
            for (Map.Entry<UUID, Rank> entry : ranks.entrySet()) {
                applyOnMainThread(entry.getKey(), entry.getValue(), requested.get(entry.getKey()));
            }
        });
    }

    private void applyOnMainThread(UUID playerId, Rank rank, long token) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!Long.valueOf(token).equals(refreshTokens.get(playerId))) {
                return;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                applyRank(player, rank);
            }
        });
    }

    private void applyRank(Player player, Rank rank) {
        UUID playerId = player.getUniqueId();
        if (rank == activeRanks.get(playerId)) {
            return;
        }
        PermissionAttachment previous = attachments.remove(playerId);
        if (previous != null) {
            player.removeAttachment(previous);
        }
        if (!rank.permissionNodes(PermissionPlatform.PAPER).isEmpty()) {
            PermissionAttachment attachment = player.addAttachment(plugin);
            for (String permission : rank.permissionNodes(PermissionPlatform.PAPER)) {
                attachment.setPermission(permission, true);
            }
            attachments.put(playerId, attachment);
        }
        activeRanks.put(playerId, rank);
        player.updateCommands();
    }

    @Override
    public void close() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        for (Map.Entry<UUID, PermissionAttachment> entry : attachments.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.removeAttachment(entry.getValue());
            }
        }
        attachments.clear();
        activeRanks.clear();
        preparedRanks.clear();
        refreshTokens.clear();
    }
}
