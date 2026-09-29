package fr.nayz.hydrogen.paper.command;

import fr.nayz.hydrogen.api.account.PermissionNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MatchmakingCommandTest {
    private final MatchmakingCommand command = new MatchmakingCommand(null, null, null);

    @Test
    void deniesPlayersWithoutTheAdminNode() {
        List<String> messages = new ArrayList<>();
        Player player = player(false, messages);

        assertTrue(command.onCommand(player, null, "matchmaking", new String[]{"join", "skywars-solo"}));
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().startsWith("Erreur: "));
        assertTrue(messages.getFirst().contains("Permission"));
        assertTrue(command.onTabComplete(player, null, "matchmaking", new String[]{"j"}).isEmpty());
    }

    @Test
    void suggestsKnownQueuesAndRejectsUnknownOnes() {
        List<String> messages = new ArrayList<>();
        Player player = player(true, messages);

        assertEquals(List.of("skywars-solo", "skywars-duo"),
                command.onTabComplete(player, null, "matchmaking", new String[]{"join", "skywars-"}));
        assertTrue(command.onCommand(player, null, "matchmaking", new String[]{"join", "unknown"}));
        assertTrue(messages.getFirst().contains("File inconnue"));
    }

    private static Player player(boolean allowed, List<String> messages) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("hasPermission")) {
                        assertEquals(PermissionNode.MATCHMAKING_DEBUG.node(), arguments[0]);
                        return allowed;
                    }
                    if (method.getName().equals("sendMessage")) {
                        messages.add(PlainTextComponentSerializer.plainText()
                                .serialize((Component) arguments[0]));
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
