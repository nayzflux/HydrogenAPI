package fr.nayz.hydrogen.api.matchmaking;

public enum MatchmakingQueue {
    SKYWARS_SOLO("skywars-solo", "SkyWars Solo"),
    SKYWARS_DUO("skywars-duo", "SkyWars Duo");

    private final String groupId;
    private final String displayName;

    MatchmakingQueue(String groupId, String displayName) {
        this.groupId = groupId;
        this.displayName = displayName;
    }

    public String groupId() {
        return groupId;
    }

    public String displayName() {
        return displayName;
    }

    public static MatchmakingQueue fromGroupId(String groupId) {
        for (MatchmakingQueue queue : values()) {
            if (queue.groupId.equals(groupId)) {
                return queue;
            }
        }
        throw new IllegalArgumentException("Unknown matchmaking group: " + groupId);
    }
}
