package fr.nayz.hydrogen.velocity.event.presence;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.Subscribe;
import fr.nayz.hydrogen.velocity.presence.VelocityPresenceManager;
import fr.nayz.hydrogen.velocity.rank.VelocityRankManager;

public final class DisconnectListener {
    private final VelocityPresenceManager presence;
    private final VelocityRankManager ranks;

    public DisconnectListener(VelocityPresenceManager presence, VelocityRankManager ranks) {
        this.presence = presence;
        this.ranks = ranks;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        presence.disconnect(event.getPlayer());
        ranks.remove(event.getPlayer().getUniqueId());
    }
}
