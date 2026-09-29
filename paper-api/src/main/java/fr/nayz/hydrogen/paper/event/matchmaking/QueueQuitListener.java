package fr.nayz.hydrogen.paper.event.matchmaking;

import fr.nayz.hydrogen.paper.matchmaking.QueueInventoryManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class QueueQuitListener implements Listener {
    private final QueueInventoryManager inventory;

    public QueueQuitListener(QueueInventoryManager inventory) {
        this.inventory = inventory;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inventory.quit(event.getPlayer().getUniqueId());
    }
}
