package fr.nayz.hydrogen.api.party;

import java.time.Instant;
import java.util.UUID;

public record PartyInvitation(UUID partyId, UUID inviter, UUID invitee, Instant expiresAt) {
}
