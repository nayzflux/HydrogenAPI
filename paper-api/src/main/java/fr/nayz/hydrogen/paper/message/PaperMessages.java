package fr.nayz.hydrogen.paper.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

public final class PaperMessages {
    private PaperMessages() {
    }

    public static void success(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    public static void error(CommandSender sender, String message) {
        String detail = message.startsWith("Erreur: ") ? message : "Erreur: " + message;
        sender.sendMessage(Component.text(detail, NamedTextColor.RED));
    }

    public static void important(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.YELLOW));
    }

    public static void neutral(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GRAY));
    }

    public static void helpHeader(CommandSender sender, String title) {
        helpDivider(sender);
        sender.sendMessage(Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    public static void helpEntry(CommandSender sender, String command, String description) {
        sender.sendMessage(Component.text("• ", NamedTextColor.DARK_GRAY)
                .append(Component.text(command, NamedTextColor.YELLOW))
                .append(Component.text(" : ", NamedTextColor.DARK_GRAY))
                .append(Component.text(description, NamedTextColor.GRAY)));
    }

    public static void helpFooter(CommandSender sender) {
        helpDivider(sender);
    }

    private static void helpDivider(CommandSender sender) {
        sender.sendMessage(Component.text("--------------------------------", NamedTextColor.GRAY,
                TextDecoration.BOLD, TextDecoration.STRIKETHROUGH));
    }
}
