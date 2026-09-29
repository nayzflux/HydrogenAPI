package fr.nayz.hydrogen.velocity.rank;

import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import fr.nayz.hydrogen.api.account.PermissionNode;
import fr.nayz.hydrogen.api.account.Rank;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class VelocityRankManagerTest {
    @Test
    void grantsRankNodesAndPreservesTheExistingProvider() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        PermissionProvider previous = subject -> permission -> {
            if (permission.equals("other.plugin.command")) {
                return Tristate.TRUE;
            }
            return Tristate.FALSE;
        };
        PermissionsSetupEvent event = new PermissionsSetupEvent(player, previous);
        VelocityRankManager ranks = new VelocityRankManager(null, null, null);

        ranks.setupPermissions(event);
        var permissions = event.getProvider().createFunction(player);
        assertEquals(Tristate.FALSE,
                permissions.getPermissionValue(PermissionNode.FRIEND_COMMAND.node()));

        ranks.cacheRank(playerId, Rank.PLAYER);
        assertEquals(Tristate.TRUE,
                permissions.getPermissionValue(PermissionNode.FRIEND_COMMAND.node()));
        assertEquals(Tristate.TRUE,
                permissions.getPermissionValue(PermissionNode.PARTY_COMMAND.node()));
        assertEquals(Tristate.TRUE,
                permissions.getPermissionValue("other.plugin.command"));

        ranks.remove(playerId);
        assertEquals(Tristate.FALSE,
                permissions.getPermissionValue(PermissionNode.FRIEND_COMMAND.node()));
        assertEquals(Tristate.TRUE,
                permissions.getPermissionValue("other.plugin.command"));
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
