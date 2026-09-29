package fr.nayz.hydrogen.paper.event.matchmaking;

import fr.nayz.hydrogen.paper.matchmaking.QueueInventoryManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class QueueInteractListener implements Listener {
    private final QueueInventoryManager inventory;

    public QueueInteractListener(QueueInventoryManager inventory) {
        this.inventory = inventory;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!inventory.isControl(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            inventory.use(event.getPlayer(), event.getItem());
        }
    }
}
