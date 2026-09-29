package fr.nayz.hydrogen.paper.event.matchmaking;

import fr.nayz.hydrogen.paper.matchmaking.QueueInventoryManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class QueueDropListener implements Listener {
    private final QueueInventoryManager inventory;

    public QueueDropListener(QueueInventoryManager inventory) {
        this.inventory = inventory;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (inventory.isLocked(event.getPlayer().getUniqueId())
                || inventory.isControl(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }
}
