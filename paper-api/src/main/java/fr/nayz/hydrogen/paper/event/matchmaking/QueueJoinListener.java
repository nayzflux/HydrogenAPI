package fr.nayz.hydrogen.paper.event.matchmaking;

import fr.nayz.hydrogen.paper.matchmaking.QueueInventoryManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class QueueJoinListener implements Listener {
    private final QueueInventoryManager inventory;

    public QueueJoinListener(QueueInventoryManager inventory) {
        this.inventory = inventory;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        inventory.joined(event.getPlayer());
    }
}
