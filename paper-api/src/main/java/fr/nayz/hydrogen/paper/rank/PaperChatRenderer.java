package fr.nayz.hydrogen.paper.rank;

import fr.nayz.hydrogen.api.account.Rank;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Function;

public final class PaperChatRenderer implements ChatRenderer.ViewerUnaware {
    private final Function<UUID, Rank> ranks;

    public PaperChatRenderer(Function<UUID, Rank> ranks) {
        this.ranks = ranks;
    }

    @Override
    public Component render(Player source, Component sourceDisplayName, Component message) {
        return render(source, sourceDisplayName, message, null);
    }

    public Component render(
            Player source,
            Component sourceDisplayName,
            Component message,
            TextColor nameColorOverride
    ) {
        Rank rank = ranks.apply(source.getUniqueId());
        TextColor chatColor = TextColor.color(rank.chatColor().rgb());
        TextColor nameColor = nameColorOverride == null ? chatColor : nameColorOverride;
        Component name = sourceDisplayName.color(nameColor);
        Component result = Component.empty();
        if (!rank.prefix().isEmpty()) {
            TextColor prefixColor = TextColor.color(rank.prefixColor().rgb());
            result = result.append(Component.text(rank.prefix(), prefixColor))
                    .append(Component.space());
        }
        return result.append(name)
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(message.colorIfAbsent(chatColor));
    }
}
