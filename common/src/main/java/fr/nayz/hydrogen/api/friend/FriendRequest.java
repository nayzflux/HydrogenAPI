package fr.nayz.hydrogen.api.friend;

import java.time.Instant;
import java.util.UUID;

public record FriendRequest(UUID sender, UUID recipient, Instant createdAt) {
}
