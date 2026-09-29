package fr.nayz.hydrogen.paper.event.matchmaking;

import fr.nayz.hydrogen.paper.matchmaking.QueueInventoryManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class QueueDragListener implements Listener {
    private final QueueInventoryManager inventory;

    public QueueDragListener(QueueInventoryManager inventory) {
        this.inventory = inventory;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (inventory.isLocked(event.getWhoClicked().getUniqueId())
                || inventory.isControl(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }
}
