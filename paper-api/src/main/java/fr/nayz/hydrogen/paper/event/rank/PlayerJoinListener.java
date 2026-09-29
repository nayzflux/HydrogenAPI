package fr.nayz.hydrogen.paper.event.rank;

import fr.nayz.hydrogen.paper.rank.PaperRankManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerJoinListener implements Listener {
    private final PaperRankManager ranks;

    public PlayerJoinListener(PaperRankManager ranks) {
        this.ranks = ranks;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ranks.onJoin(event.getPlayer());
    }
}
