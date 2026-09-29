package fr.nayz.hydrogen.paper.event.rank;

import fr.nayz.hydrogen.paper.rank.PaperChatRenderer;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class RankChatListener implements Listener {
    private final ChatRenderer renderer;

    public RankChatListener(PaperChatRenderer renderer) {
        this.renderer = ChatRenderer.viewerUnaware(renderer);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.renderer(renderer);
    }
}
