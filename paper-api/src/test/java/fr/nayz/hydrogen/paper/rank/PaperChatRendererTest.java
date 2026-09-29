package fr.nayz.hydrogen.paper.rank;

import fr.nayz.hydrogen.api.account.Rank;
import fr.nayz.hydrogen.paper.event.rank.RankChatListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PaperChatRendererTest {
    @Test
    void rendersEveryRankFromTheCachedValue() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        for (Rank rank : Rank.values()) {
            PaperChatRenderer renderer = new PaperChatRenderer(id -> {
                assertEquals(playerId, id);
                return rank;
            });
            Component rendered = renderer.render(player, Component.text("Nino"), Component.text("salut"));
            String prefix = rank.prefix().isEmpty() ? "" : rank.prefix() + " ";
            assertEquals(prefix + "Nino » salut",
                    PlainTextComponentSerializer.plainText().serialize(rendered));

            int nameIndex = rank.prefix().isEmpty() ? 0 : 2;
            assertEquals(TextColor.color(rank.chatColor().rgb()),
                    rendered.children().get(nameIndex).color());
            assertEquals(TextColor.color(rank.chatColor().rgb()),
                    rendered.children().get(nameIndex + 2).color());
            if (!rank.prefix().isEmpty()) {
                assertEquals(TextColor.color(rank.prefixColor().rgb()),
                        rendered.children().getFirst().color());
            }
        }
    }

    @Test
    void installsTheRendererBeforeNormalPriorityListeners() throws Exception {
        EventHandler handler = RankChatListener.class.getMethod("onChat",
                        io.papermc.paper.event.player.AsyncChatEvent.class)
                .getAnnotation(EventHandler.class);
        assertEquals(EventPriority.LOWEST, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    @Test
    void teamColorChangesOnlyTheName() {
        Player player = player(UUID.randomUUID());
        PaperChatRenderer renderer = new PaperChatRenderer(id -> Rank.ADMIN);

        Component rendered = renderer.render(
                player,
                Component.text("Nino"),
                Component.text("salut"),
                TextColor.color(0x00AA00)
        );

        assertEquals("[Admin] Nino » salut",
                PlainTextComponentSerializer.plainText().serialize(rendered));
        assertEquals(TextColor.color(0x00AA00), rendered.children().get(2).color());
        assertEquals(TextColor.color(Rank.ADMIN.chatColor().rgb()),
                rendered.children().get(4).color());
    }

    private static Player player(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getUniqueId")) {
                        return playerId;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
