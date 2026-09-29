package fr.nayz.hydrogen.paper.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class GuiCloseListener implements Listener {
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GUI gui) {
            gui.onClose(event);
        }
    }
}
