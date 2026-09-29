package fr.nayz.hydrogen.core.matchmaking;

import fr.nayz.hydrogen.api.matchmaking.QueueState;

import java.util.UUID;

public record QueueLease(QueueState state, UUID token) {
}
