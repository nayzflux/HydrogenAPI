package fr.nayz.hydrogen.paper.scoreboards;

import fr.mrmicky.fastboard.adventure.FastBoard;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Per-player wrapper around a single {@link FastBoard} instance.
 * <p>
 * Design rules baked into this class:
 * <ul>
 *   <li><strong>One FastBoard per player for their whole session.</strong>
 *       Never {@code new FastBoard()} + {@code delete()} on every board switch.
 *       {@link #close()} is called only on quit.</li>
 *   <li><strong>Never clear-then-set.</strong>
 *       Content is updated directly; no intermediate {@code updateLines(List.of())} call
 *       that would cause self-inflicted flicker.</li>
 *   <li><strong>Dirty-check before touching FastBoard</strong>,
 *       even though FastBoard also diffs internally — this avoids the CPU cost of
 *       rebuilding Component lists and string formatting when nothing changed.</li>
 * </ul>
 */
final class PlayerBoard {

    private final FastBoard board;
    private final Player player;

    private String activeBoardId;
    private List<Component> lastLines = List.of();
    private Component lastTitle;
    private int ticksSinceRefresh;

    PlayerBoard(Player player) {
        this.player = player;
        this.board = new FastBoard(player);
    }

    /**
     * Called every server tick by the global {@link ScoreboardTicker}.
     * <p>
     * Respects the board's {@link Board#refreshInterval()} — only recomputes
     * and diffs content when enough ticks have elapsed.
     *
     * @param def the active board definition
     */
    void tick(Board def) {
        if (++ticksSinceRefresh < def.refreshInterval()) {
            return;
        }
        ticksSinceRefresh = 0;

        Component newTitle = def.title(player);
        List<Component> newLines = def.lines(player);

        // Dirty-check BEFORE touching FastBoard: avoids rebuilding
        // Adventure components / calling into packets when nothing changed
        if (!newTitle.equals(lastTitle)) {
            board.updateTitle(newTitle);
            lastTitle = newTitle;
        }

        if (!newLines.equals(lastLines)) {
            board.updateLines(newLines);
            lastLines = List.copyOf(newLines);
        }
    }

    /**
     * Resets the refresh counter so the next tick forces an immediate recompute.
     * Called when the player switches boards to avoid stale content from the previous board
     * lingering until the next refresh cycle.
     */
    void resetRefreshCounter() {
        this.ticksSinceRefresh = Integer.MAX_VALUE - 1;
    }

    /**
     * Returns the identifier of the board this player is currently viewing.
     *
     * @return the active board id, or {@code null} if cleared
     */
    String getActiveBoardId() {
        return activeBoardId;
    }

    /**
     * Sets the active board identifier. Does <em>not</em> trigger an immediate render —
     * that happens on the next tick.
     *
     * @param boardId the new board id, or {@code null} to clear
     */
    void setActiveBoardId(String boardId) {
        this.activeBoardId = boardId;
    }

    Player getPlayer() {
        return player;
    }

    /**
     * Returns whether the underlying FastBoard has been deleted (player quit, plugin disabled, etc.).
     */
    boolean isDeleted() {
        return board.isDeleted();
    }

    /**
     * Deletes the underlying FastBoard. Called once on player quit to prevent
     * packet and memory leaks.
     */
    void close() {
        if (!board.isDeleted()) {
            board.delete();
        }
    }
}

