package fr.nayz.hydrogen.paper.scoreboards;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ScoreboardQuitListener implements Listener {
    private final ScoreboardServiceImpl service;

    public ScoreboardQuitListener(ScoreboardServiceImpl service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuit(event.getPlayer());
    }
}
