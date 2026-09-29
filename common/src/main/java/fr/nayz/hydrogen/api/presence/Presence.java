package fr.nayz.hydrogen.api.presence;

import java.time.Instant;
import java.util.UUID;

public record Presence(UUID playerId, String proxyId, String serverId, Instant lastSeen) {
}
