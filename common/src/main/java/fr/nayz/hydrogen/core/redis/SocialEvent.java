package fr.nayz.hydrogen.core.redis;

import java.util.UUID;

public record SocialEvent(String kind, UUID target, UUID actor) {
}
