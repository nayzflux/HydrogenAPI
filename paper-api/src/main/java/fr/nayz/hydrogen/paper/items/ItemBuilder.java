package fr.nayz.hydrogen.paper.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public final class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        item = new ItemStack(material, amount);
        meta = item.getItemMeta();
    }

    public ItemBuilder name(String value) {
        meta.displayName(format(value));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        meta.lore(lines.stream().map(this::format).toList());
        return this;
    }

    public ItemBuilder glow() {
        meta.setEnchantmentGlintOverride(true);
        return this;
    }

    public <T, Z> ItemBuilder persistentData(
            NamespacedKey key,
            PersistentDataType<T, Z> type,
            Z value
    ) {
        meta.getPersistentDataContainer().set(key, type, value);
        return this;
    }

    public ItemBuilder skullOwner(UUID playerId) {
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
        }
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    private Component format(String value) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(value.replace('§', '&'))
                .decoration(TextDecoration.ITALIC, false);
    }
}
