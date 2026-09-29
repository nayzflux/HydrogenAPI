package fr.nayz.hydrogen.paper.event.matchmaking;

import fr.nayz.hydrogen.paper.matchmaking.QueueInventoryManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class QueueSwapListener implements Listener {
    private final QueueInventoryManager inventory;

    public QueueSwapListener(QueueInventoryManager inventory) {
        this.inventory = inventory;
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (inventory.isLocked(event.getPlayer().getUniqueId())
                || inventory.isControl(event.getMainHandItem())
                || inventory.isControl(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }
}
