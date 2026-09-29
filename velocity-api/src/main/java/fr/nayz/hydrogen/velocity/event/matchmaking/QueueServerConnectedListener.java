package fr.nayz.hydrogen.velocity.event.matchmaking;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import fr.nayz.hydrogen.velocity.matchmaking.VelocityQueueMessenger;

public final class QueueServerConnectedListener {
    private final VelocityQueueMessenger messenger;

    public QueueServerConnectedListener(VelocityQueueMessenger messenger) {
        this.messenger = messenger;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        messenger.serverConnected(event.getPlayer(), event.getServer().getServerInfo().getName());
    }
}
