package fr.nayz.hydrogen.velocity.event.presence;

import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.Subscribe;
import fr.nayz.hydrogen.velocity.presence.VelocityPresenceManager;

public final class ServerConnectedListener {
    private final VelocityPresenceManager presence;

    public ServerConnectedListener(VelocityPresenceManager presence) {
        this.presence = presence;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        presence.serverConnected(event.getPlayer(), event.getServer().getServerInfo().getName());
    }
}
