package fr.nayz.hydrogen.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import fr.nayz.hydrogen.api.account.PermissionNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SocialCommandPermissionTest {
    @Test
    void eachCommandChecksItsOwnNode() {
        SocialCommandSupport support = new SocialCommandSupport(null, null, null, null);
        FriendCommand friend = new FriendCommand(support);
        PartyCommand party = new PartyCommand(support, new PartyInviteLimiter());

        SimpleCommand.Invocation friendOnly = invocation(player(PermissionNode.FRIEND_COMMAND.node()));
        SimpleCommand.Invocation partyOnly = invocation(player(PermissionNode.PARTY_COMMAND.node()));
        assertTrue(friend.hasPermission(friendOnly));
        assertFalse(party.hasPermission(friendOnly));
        assertFalse(friend.hasPermission(partyOnly));
        assertTrue(party.hasPermission(partyOnly));
    }

    private static SimpleCommand.Invocation invocation(CommandSource source) {
        return (SimpleCommand.Invocation) Proxy.newProxyInstance(
                SimpleCommand.Invocation.class.getClassLoader(),
                new Class<?>[]{SimpleCommand.Invocation.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("source")) {
                        return source;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Player player(String permission) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("hasPermission")) {
                        return permission.equals(arguments[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
