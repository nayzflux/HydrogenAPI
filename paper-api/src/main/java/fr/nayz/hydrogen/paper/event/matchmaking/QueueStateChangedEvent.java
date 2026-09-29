package fr.nayz.hydrogen.paper.event.matchmaking;

import fr.nayz.hydrogen.api.matchmaking.QueueState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class QueueStateChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Optional<QueueState> previous;
    private final Optional<QueueState> current;

    public QueueStateChangedEvent(
            Player player,
            Optional<QueueState> previous,
            Optional<QueueState> current
    ) {
        this.player = player;
        this.previous = previous;
        this.current = current;
    }

    public Player player() {
        return player;
    }

    public Optional<QueueState> previous() {
        return previous;
    }

    public Optional<QueueState> current() {
        return current;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
