package fr.nayz.hydrogen.paper.event.matchmaking;

import fr.nayz.hydrogen.paper.matchmaking.QueueInventoryManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class QueueClickListener implements Listener {
    private final QueueInventoryManager inventory;

    public QueueClickListener(QueueInventoryManager inventory) {
        this.inventory = inventory;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (inventory.isLocked(event.getWhoClicked().getUniqueId())
                || inventory.isControl(event.getCurrentItem())
                || inventory.isControl(event.getCursor())) {
            event.setCancelled(true);
        }
    }
}
