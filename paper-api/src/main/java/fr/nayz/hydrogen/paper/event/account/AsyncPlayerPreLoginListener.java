package fr.nayz.hydrogen.paper.event.account;

import fr.nayz.hydrogen.paper.rank.PaperRankManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class AsyncPlayerPreLoginListener implements Listener {
    private final PaperRankManager ranks;

    public AsyncPlayerPreLoginListener(PaperRankManager ranks) {
        this.ranks = ranks;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        ranks.prepare(event);
    }
}
