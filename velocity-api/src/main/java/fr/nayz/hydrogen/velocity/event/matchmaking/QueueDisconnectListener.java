package fr.nayz.hydrogen.velocity.event.matchmaking;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import fr.nayz.hydrogen.velocity.matchmaking.VelocityQueueMessenger;

public final class QueueDisconnectListener {
    private final VelocityQueueMessenger messenger;

    public QueueDisconnectListener(VelocityQueueMessenger messenger) {
        this.messenger = messenger;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        messenger.disconnected(event.getPlayer().getUniqueId());
    }
}
