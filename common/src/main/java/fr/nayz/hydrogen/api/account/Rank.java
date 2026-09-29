package fr.nayz.hydrogen.api.account;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum Rank {
    PLAYER("", RankColor.GRAY, RankColor.GRAY,
            Set.of(PermissionNode.FRIEND_COMMAND, PermissionNode.PARTY_COMMAND), Set.of()),
    VIP("[VIP]", RankColor.YELLOW, RankColor.GRAY, Set.of(), Set.of(PLAYER)),
    VIP_PLUS("[VIP+]", RankColor.AQUA, RankColor.GRAY, Set.of(), Set.of(VIP)),
    MODERATOR("[Modérateur]", RankColor.BLUE, RankColor.GOLD, Set.of(), Set.of(PLAYER)),
    ADMIN("[Admin]", RankColor.RED, RankColor.GOLD,
            Set.of(PermissionNode.MATCHMAKING_DEBUG, PermissionNode.ACCOUNT_INSPECT,
                    PermissionNode.QUEUE_INSPECT, PermissionNode.RANK_MANAGE),
            Set.of(MODERATOR));

    private final String prefix;
    private final RankColor prefixColor;
    private final RankColor chatColor;
    private final Set<PermissionNode> ownPermissions;
    private final Set<Rank> includedRanks;

    Rank(
            String prefix,
            RankColor prefixColor,
            RankColor chatColor,
            Set<PermissionNode> ownPermissions,
            Set<Rank> includedRanks
    ) {
        this.prefix = prefix;
        this.prefixColor = prefixColor;
        this.chatColor = chatColor;
        this.ownPermissions = Set.copyOf(ownPermissions);
        this.includedRanks = Set.copyOf(includedRanks);
    }

    public String prefix() {
        return prefix;
    }

    public RankColor prefixColor() {
        return prefixColor;
    }

    public RankColor chatColor() {
        return chatColor;
    }

    public Set<PermissionNode> ownPermissions() {
        return ownPermissions;
    }

    public Set<Rank> includedRanks() {
        return includedRanks;
    }

    public Set<PermissionNode> permissions() {
        EnumSet<PermissionNode> result = EnumSet.noneOf(PermissionNode.class);
        collectPermissions(result, EnumSet.noneOf(Rank.class));
        return Set.copyOf(result);
    }

    public Set<String> permissionNodes(PermissionPlatform platform) {
        return permissions().stream()
                .filter(permission -> permission.appliesTo(platform))
                .map(PermissionNode::node)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void collectPermissions(Set<PermissionNode> result, Set<Rank> visited) {
        if (!visited.add(this)) {
            return;
        }
        result.addAll(ownPermissions);
        for (Rank included : includedRanks) {
            included.collectPermissions(result, visited);
        }
    }
}
