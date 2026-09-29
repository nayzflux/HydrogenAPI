package fr.nayz.hydrogen.paper.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An abstract base class representing a GUI inventory menu.
 * Uses the native {@link InventoryHolder} approach to map inventories to their click actions,
 * allowing dynamic titles and custom parameters per-player without memory leaks or complex mapping.
 */
public abstract class GUI implements InventoryHolder {

    protected final Inventory inventory;
    protected final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    /**
     * Creates a new GUI with the specified size and title.
     *
     * @param size  the size of the inventory (must be a multiple of 9, between 9 and 54)
     * @param title the title of the GUI, using Adventure Component API
     */
    protected GUI(int size, @NotNull Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    /**
     * Populates the inventory with items and registers button actions.
     * Implementations should place items using {@link #setItem(int, ItemStack)}
     * and {@link #setButton(int, ItemStack, Consumer)}.
     */
    public abstract void populate();

    /**
     * Refreshes the GUI by clearing all slots and actions, then re-populating.
     * This is useful when the GUI state changes and needs to be redrawn.
     */
    public void refresh() {
        this.inventory.clear();
        this.actions.clear();
        populate();
    }

    /**
     * Opens the GUI for the specified player.
     *
     * @param player the player to open the GUI for
     */
    public void open(@NotNull Player player) {
        player.openInventory(this.inventory);
    }

    /**
     * Sets an item in a specific slot without any click action.
     *
     * @param slot the inventory slot
     * @param item the item stack
     */
    protected void setItem(int slot, ItemStack item) {
        this.inventory.setItem(slot, item);
        this.actions.remove(slot);
    }

    /**
     * Sets an item in a specific slot and registers a click handler for it.
     *
     * @param slot   the inventory slot
     * @param item   the item stack
     * @param action the callback to run when the slot is clicked
     */
    protected void setButton(int slot, ItemStack item, @NotNull Consumer<InventoryClickEvent> action) {
        this.inventory.setItem(slot, item);
        this.actions.put(slot, action);
    }

    /**
     * Fills all empty slots in the inventory with a specific item (e.g., background filler).
     *
     * @param item the filler item stack
     */
    protected void fill(ItemStack item) {
        for (int i = 0; i < this.inventory.getSize(); i++) {
            if (this.inventory.getItem(i) == null) {
                setItem(i, item.clone());
            }
        }
    }

    /**
     * Handles inventory click events. Cancels the event by default and executes registered slot actions.
     *
     * @param event the inventory click event
     */
    public void handleClick(@NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(this.inventory)) {
            Consumer<InventoryClickEvent> action = this.actions.get(event.getSlot());
            if (action != null) {
                action.accept(event);
            }
        }
    }

    /**
     * Called when the GUI inventory is opened. Can be overridden by subclasses.
     *
     * @param event the inventory open event
     */
    public void onOpen(@NotNull InventoryOpenEvent event) {}

    /**
     * Called when the GUI inventory is closed. Can be overridden by subclasses.
     *
     * @param event the inventory close event
     */
    public void onClose(@NotNull InventoryCloseEvent event) {}

    @Override
    @NotNull
    public Inventory getInventory() {
        return this.inventory;
    }
}

