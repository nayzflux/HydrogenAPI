package fr.nayz.hydrogen.paper.scoreboards;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of named {@link Board} definitions.
 * <p>
 * Board definitions are registered once (typically in a plugin's {@code onEnable}) and
 * looked up by the ticker every server tick, so a {@link ConcurrentHashMap} is used
 * to allow lock-free reads on the hot path.
 */
final class BoardRegistry {

    private final Map<String, Board> boards = new ConcurrentHashMap<>();

    /**
     * Registers (or replaces) a board definition.
     *
     * @param id    a unique identifier
     * @param board the board definition
     */
    void register(String id, Board board) {
        boards.put(id, board);
    }

    /**
     * Removes a board definition.
     *
     * @param id the identifier to remove
     */
    void unregister(String id) {
        boards.remove(id);
    }

    /**
     * Returns the board definition for the given id, or {@link Optional#empty()} if not registered.
     *
     * @param id the board identifier
     * @return an optional containing the board, if registered
     */
    Optional<Board> get(String id) {
        return Optional.ofNullable(boards.get(id));
    }

    /**
     * Returns whether a board with the given id is registered.
     *
     * @param id the board identifier
     * @return true if registered
     */
    boolean contains(String id) {
        return boards.containsKey(id);
    }
}

