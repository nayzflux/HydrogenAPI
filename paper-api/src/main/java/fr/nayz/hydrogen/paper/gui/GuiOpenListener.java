package fr.nayz.hydrogen.paper.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;

public final class GuiOpenListener implements Listener {
    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof GUI gui) {
            gui.onOpen(event);
        }
    }
}
