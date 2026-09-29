package fr.nayz.hydrogen.velocity.event.presence;

import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import fr.nayz.hydrogen.velocity.presence.VelocityPresenceManager;

public final class PostLoginListener {
    private final VelocityPresenceManager presence;

    public PostLoginListener(VelocityPresenceManager presence) {
        this.presence = presence;
    }

    @Subscribe
    public EventTask onPostLogin(PostLoginEvent event) {
        var cleanup = presence.postLogin(event.getPlayer());
        return EventTask.resumeWhenComplete(cleanup);
    }
}
