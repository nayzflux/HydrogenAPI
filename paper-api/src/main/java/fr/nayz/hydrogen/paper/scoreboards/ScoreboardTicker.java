package fr.nayz.hydrogen.paper.scoreboards;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * Single plugin-wide scheduler task that iterates all active {@link PlayerBoard}s
 * every server tick.
 * <p>
 * One global task scales far better than a per-player {@code BukkitRunnable} and is
 * significantly easier to profile. Each {@link Board} controls its own refresh interval
 * via {@link Board#refreshInterval()}, so the ticker itself runs every tick but most
 * boards will short-circuit inside {@link PlayerBoard#tick(Board)}.
 */
final class ScoreboardTicker {

    private final BoardRegistry registry;
    private final Map<UUID, PlayerBoard> playerBoards;
    private BukkitTask task;

    ScoreboardTicker(BoardRegistry registry, Map<UUID, PlayerBoard> playerBoards) {
        this.registry = registry;
        this.playerBoards = playerBoards;
    }

    /**
     * Starts the ticker. Should be called once from {@code onEnable}.
     *
     * @param plugin the owning plugin instance
     */
    void start(JavaPlugin plugin) {
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    /**
     * Stops the ticker. Should be called from {@code onDisable}.
     */
    void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void tick() {
        for (PlayerBoard pb : playerBoards.values()) {
            if (pb.isDeleted()) continue;

            String boardId = pb.getActiveBoardId();
            if (boardId == null) continue;

            registry.get(boardId).ifPresent(def -> pb.tick(def));
        }
    }
}

