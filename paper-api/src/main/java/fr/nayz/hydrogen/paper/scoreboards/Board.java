package fr.nayz.hydrogen.paper.scoreboards;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Defines a scoreboard layout.
 * <p>
 * Consumers register a {@code Board} definition once via {@link ScoreboardService#registerBoard(String, Board)},
 * and the system renders it per-player at the configured refresh interval.
 * <p>
 * Implementation notes:
 * <ul>
 *   <li>{@link #title(Player)} and {@link #lines(Player)} are called on the main thread (or the owning
 *       region thread on Folia) — keep them fast.</li>
 *   <li>Return the <em>exact</em> number of lines you want; the backing library (FastBoard) handles
 *       dynamic line-count changes natively, so no padding is needed.</li>
 * </ul>
 */
public interface Board {

    /**
     * Returns the title component shown at the top of the scoreboard for the given player.
     *
     * @param player the player viewing the board
     * @return the title component
     */
    Component title(Player player);

    /**
     * Returns the lines (top-to-bottom) to display on the scoreboard for the given player.
     *
     * @param player the player viewing the board
     * @return an immutable or unmodifiable list of line components
     */
    List<Component> lines(Player player);

    /**
     * How often this board's content should be recomputed, in server ticks.
     * <p>
     * Defaults to {@code 20} (once per second). Boards displaying static or slowly-changing
     * data should use higher values; animation-heavy boards may go as low as {@code 1}.
     *
     * @return the refresh interval in ticks
     */
    default int refreshInterval() {
        return 20;
    }
}

