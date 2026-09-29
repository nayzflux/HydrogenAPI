package fr.nayz.hydrogen.paper.scoreboards;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ScoreboardJoinListener implements Listener {
    private final ScoreboardServiceImpl service;

    public ScoreboardJoinListener(ScoreboardServiceImpl service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        service.handleJoin(event.getPlayer());
    }
}
