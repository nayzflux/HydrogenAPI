package fr.nayz.hydrogen.api.party;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Party(UUID id, UUID leader, List<UUID> members, boolean membersCanInvite, Instant expiresAt) {
}
