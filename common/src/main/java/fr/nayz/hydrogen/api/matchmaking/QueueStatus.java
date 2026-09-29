package fr.nayz.hydrogen.api.matchmaking;

public enum QueueStatus {
    JOINING,
    QUEUED,
    VERIFYING,
    LEAVING,
    MATCH_FOUND;

    public boolean hasQueueInventory() {
        return this != MATCH_FOUND;
    }
}
