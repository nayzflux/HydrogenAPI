package fr.nayz.hydrogen.paper.scoreboards;

import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Public facade for the scoreboard system.
 * <p>
 * Consumers obtain this service through {@link fr.nayz.hydrogen.paper.HydrogenPaperApi}:
 * <pre>{@code
 * HydrogenPaperApi hydrogen = Bukkit.getServicesManager()
 *     .load(HydrogenPaperApi.class);
 * ScoreboardService service = hydrogen.scoreboards();
 * }</pre>
 * <p>
 * Board definitions are registered once (typically in {@code onEnable}), and players
 * are assigned to a board by its string identifier.
 */
public interface ScoreboardService {

    /**
     * Registers a named board definition.
     * <p>
     * If a board with the same {@code id} is already registered, it is silently replaced.
     *
     * @param id    a unique identifier for this board (e.g. {@code "hub_lobby"}, {@code "bedwars_ingame"})
     * @param board the board definition to register
     */
    void registerBoard(String id, Board board);

    /**
     * Removes a previously registered board definition.
     * <p>
     * Players currently viewing this board will keep their last-rendered state
     * until they are assigned a new board or their board is cleared.
     *
     * @param id the board identifier to remove
     */
    void unregisterBoard(String id);

    /**
     * Assigns a player to a registered board.
     * <p>
     * The player's scoreboard will be updated on the next tick cycle.
     * Calling this when the player already has the same board is a no-op.
     *
     * @param player  the player to assign
     * @param boardId the identifier of a previously registered board
     * @throws IllegalArgumentException if no board is registered with the given {@code boardId}
     */
    void setBoard(Player player, String boardId);

    /**
     * Removes the player's active scoreboard, hiding it entirely.
     *
     * @param player the player whose board should be cleared
     */
    void clearBoard(Player player);

    /**
     * Returns the identifier of the board currently assigned to the given player,
     * or {@link Optional#empty()} if the player has no active board.
     *
     * @param player the player to query
     * @return an optional containing the active board id
     */
    Optional<String> getActiveBoard(Player player);
}

