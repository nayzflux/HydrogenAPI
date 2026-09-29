package fr.nayz.hydrogen.api.account;

import java.util.EnumSet;
import java.util.Set;

public enum PermissionNode {
    FRIEND_COMMAND("hydrogen.command.friend", PermissionPlatform.VELOCITY),
    PARTY_COMMAND("hydrogen.command.party", PermissionPlatform.VELOCITY),
    MATCHMAKING_DEBUG("hydrogen.admin.matchmaking", PermissionPlatform.PAPER),
    ACCOUNT_INSPECT("hydrogen.admin.account", PermissionPlatform.VELOCITY),
    QUEUE_INSPECT("hydrogen.admin.queue", PermissionPlatform.VELOCITY),
    RANK_MANAGE("hydrogen.admin.rank", PermissionPlatform.VELOCITY);

    private final String node;
    private final Set<PermissionPlatform> platforms;

    PermissionNode(String node, PermissionPlatform first, PermissionPlatform... others) {
        this.node = node;
        EnumSet<PermissionPlatform> supported = EnumSet.of(first, others);
        this.platforms = Set.copyOf(supported);
    }

    public String node() {
        return node;
    }

    public boolean appliesTo(PermissionPlatform platform) {
        return platforms.contains(platform);
    }
}
