package fr.nayz.hydrogen.api.account;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RankTest {
    @Test
    void inheritedPermissionsStayOnTheirPlatform() {
        for (Rank rank : List.of(Rank.PLAYER, Rank.VIP, Rank.VIP_PLUS, Rank.MODERATOR)) {
            assertEquals(Set.of("hydrogen.command.friend", "hydrogen.command.party"),
                    rank.permissionNodes(PermissionPlatform.VELOCITY));
            assertTrue(rank.permissionNodes(PermissionPlatform.PAPER).isEmpty());
        }
        assertEquals(Set.of("hydrogen.command.friend", "hydrogen.command.party",
                        "hydrogen.admin.account", "hydrogen.admin.queue", "hydrogen.admin.rank"),
                Rank.ADMIN.permissionNodes(PermissionPlatform.VELOCITY));
        assertEquals(Set.of("hydrogen.admin.matchmaking"),
                Rank.ADMIN.permissionNodes(PermissionPlatform.PAPER));
        assertTrue(Rank.VIP.includedRanks().contains(Rank.PLAYER));
        assertTrue(Rank.VIP_PLUS.includedRanks().contains(Rank.VIP));
        assertTrue(Rank.MODERATOR.includedRanks().contains(Rank.PLAYER));
        assertTrue(Rank.ADMIN.includedRanks().contains(Rank.MODERATOR));
    }

    @Test
    void appearanceMatchesRankDefinitions() {
        assertEquals("", Rank.PLAYER.prefix());
        assertEquals("[VIP]", Rank.VIP.prefix());
        assertEquals("[VIP+]", Rank.VIP_PLUS.prefix());
        assertEquals("[Modérateur]", Rank.MODERATOR.prefix());
        assertEquals("[Admin]", Rank.ADMIN.prefix());
        assertEquals(RankColor.GRAY, Rank.PLAYER.chatColor());
        assertEquals(RankColor.GRAY, Rank.VIP.chatColor());
        assertEquals(RankColor.GRAY, Rank.VIP_PLUS.chatColor());
        assertEquals(RankColor.GOLD, Rank.MODERATOR.chatColor());
        assertEquals(RankColor.GOLD, Rank.ADMIN.chatColor());
        assertFalse(PermissionNode.FRIEND_COMMAND.appliesTo(PermissionPlatform.PAPER));
    }
}
