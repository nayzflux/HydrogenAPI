package fr.nayz.hydrogen.velocity.message;

import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class VelocityMessages {
    private VelocityMessages() {
    }

    public static void success(CommandSource source, String message) {
        source.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    public static void error(CommandSource source, String message) {
        String detail = message.startsWith("Erreur: ") ? message : "Erreur: " + message;
        source.sendMessage(Component.text(detail, NamedTextColor.RED));
    }

    public static void important(CommandSource source, String message) {
        source.sendMessage(Component.text(message, NamedTextColor.YELLOW));
    }

    public static void neutral(CommandSource source, String message) {
        source.sendMessage(Component.text(message, NamedTextColor.GRAY));
    }

    public static void helpHeader(CommandSource source, String title) {
        helpDivider(source);
        source.sendMessage(Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    public static void helpEntry(CommandSource source, String command, String description) {
        source.sendMessage(Component.text("• ", NamedTextColor.DARK_GRAY)
                .append(Component.text(command, NamedTextColor.YELLOW))
                .append(Component.text(" : ", NamedTextColor.DARK_GRAY))
                .append(Component.text(description, NamedTextColor.GRAY)));
    }

    public static void helpFooter(CommandSource source) {
        helpDivider(source);
    }

    private static void helpDivider(CommandSource source) {
        source.sendMessage(Component.text("--------------------------------", NamedTextColor.GRAY,
                TextDecoration.BOLD, TextDecoration.STRIKETHROUGH));
    }
}
