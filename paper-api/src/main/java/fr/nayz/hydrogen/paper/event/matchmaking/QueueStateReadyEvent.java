package fr.nayz.hydrogen.paper.event.matchmaking;

import fr.nayz.hydrogen.api.matchmaking.QueueState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class QueueStateReadyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Optional<QueueState> state;

    public QueueStateReadyEvent(Player player, Optional<QueueState> state) {
        this.player = player;
        this.state = state;
    }

    public Player player() {
        return player;
    }

    public Optional<QueueState> state() {
        return state;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
