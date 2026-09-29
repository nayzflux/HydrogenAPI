package fr.nayz.hydrogen.api.party;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyService {
    CompletableFuture<Party> create(UUID leader);

    CompletableFuture<Optional<Party>> getForPlayer(UUID playerId);

    CompletableFuture<Optional<Party>> get(UUID partyId);

    CompletableFuture<List<PartyInvitation>> invitations(UUID invitee);

    CompletableFuture<PartyInvitation> invite(UUID leader, UUID invitee);

    CompletableFuture<Party> accept(UUID invitee, UUID partyId);

    CompletableFuture<Party> accept(UUID invitee, UUID partyId, UUID inviter);

    CompletableFuture<Void> decline(UUID invitee, UUID partyId, UUID inviter);

    CompletableFuture<Party> setMembersCanInvite(UUID leader, boolean enabled);

    CompletableFuture<Void> leave(UUID playerId);

    CompletableFuture<Party> kick(UUID leader, UUID member);

    CompletableFuture<Party> transferLeader(UUID leader, UUID newLeader);

    CompletableFuture<Void> disband(UUID leader);

    CompletableFuture<Void> renew(UUID playerId);
}
