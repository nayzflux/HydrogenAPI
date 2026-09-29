package fr.nayz.hydrogen.paper.scoreboards;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link ScoreboardService}.
 * <p>
 * Manages the full lifecycle: board registration, per-player board assignment,
 * player join/quit cleanup, and the global ticker.
 * <p>
 * Exposed to consumer plugins through {@link fr.nayz.hydrogen.paper.HydrogenPaperApi}.
 */
public final class ScoreboardServiceImpl implements ScoreboardService {

    private final BoardRegistry registry = new BoardRegistry();
    private final Map<UUID, PlayerBoard> playerBoards = new ConcurrentHashMap<>();
    private final ScoreboardTicker ticker;

    public ScoreboardServiceImpl() {
        this.ticker = new ScoreboardTicker(registry, playerBoards);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts the global ticker. Call from {@code onEnable}.
     *
     * @param plugin the owning plugin
     */
    public void enable(JavaPlugin plugin) {
        ticker.start(plugin);
    }

    /**
     * Stops the ticker and closes all player boards. Call from {@code onDisable}.
     */
    public void disable() {
        ticker.stop();
        playerBoards.values().forEach(PlayerBoard::close);
        playerBoards.clear();
    }

    // ── Player join/quit ─────────────────────────────────────────────────────

    /**
     * Creates a {@link PlayerBoard} for the joining player.
     * Called from the centralized join listener.
     *
     * @param player the player who joined
     */
    public void handleJoin(Player player) {
        playerBoards.put(player.getUniqueId(), new PlayerBoard(player));
    }

    /**
     * Closes and removes the {@link PlayerBoard} for the quitting player.
     * Called from the centralized quit listener.
     *
     * @param player the player who quit
     */
    public void handleQuit(Player player) {
        PlayerBoard pb = playerBoards.remove(player.getUniqueId());
        if (pb != null) {
            pb.close();
        }
    }

    // ── ScoreboardService API ───────────────────────────────────────────────

    @Override
    public void registerBoard(String id, Board board) {
        registry.register(id, board);
    }

    @Override
    public void unregisterBoard(String id) {
        registry.unregister(id);
    }

    @Override
    public void setBoard(Player player, String boardId) {
        if (!registry.contains(boardId)) {
            throw new IllegalArgumentException("No board registered with id: " + boardId);
        }

        PlayerBoard pb = playerBoards.get(player.getUniqueId());
        if (pb == null) return;

        // No-op if already on this board
        if (boardId.equals(pb.getActiveBoardId())) return;

        pb.setActiveBoardId(boardId);
        // Force immediate refresh on next tick so content appears instantly
        pb.resetRefreshCounter();
    }

    @Override
    public void clearBoard(Player player) {
        PlayerBoard pb = playerBoards.get(player.getUniqueId());
        if (pb != null) {
            pb.setActiveBoardId(null);
            // Close and recreate the FastBoard so the sidebar disappears
            pb.close();
            playerBoards.put(player.getUniqueId(), new PlayerBoard(player));
        }
    }

    @Override
    public Optional<String> getActiveBoard(Player player) {
        PlayerBoard pb = playerBoards.get(player.getUniqueId());
        return pb != null ? Optional.ofNullable(pb.getActiveBoardId()) : Optional.empty();
    }
}

