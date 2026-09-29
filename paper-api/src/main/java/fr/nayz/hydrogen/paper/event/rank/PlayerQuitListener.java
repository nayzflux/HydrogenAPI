package fr.nayz.hydrogen.paper.event.rank;

import fr.nayz.hydrogen.paper.rank.PaperRankManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerQuitListener implements Listener {
    private final PaperRankManager ranks;

    public PlayerQuitListener(PaperRankManager ranks) {
        this.ranks = ranks;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ranks.onQuit(event.getPlayer());
    }
}
