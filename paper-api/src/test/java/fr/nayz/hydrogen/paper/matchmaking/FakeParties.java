package fr.nayz.hydrogen.paper.matchmaking;

import fr.nayz.hydrogen.api.party.Party;
import fr.nayz.hydrogen.api.party.PartyInvitation;
import fr.nayz.hydrogen.api.party.PartyService;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class FakeParties implements PartyService {
    private final Party party;

    FakeParties(Party party) {
        this.party = party;
    }

    @Override
    public CompletableFuture<Optional<Party>> getForPlayer(UUID playerId) {
        return CompletableFuture.completedFuture(Optional.of(party));
    }

    @Override
    public CompletableFuture<Optional<Party>> get(UUID partyId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<PartyInvitation>> invitations(UUID invitee) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Party> create(UUID leader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<PartyInvitation> invite(UUID leader, UUID invitee) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Party> accept(UUID invitee, UUID partyId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Party> accept(UUID invitee, UUID partyId, UUID inviter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> decline(UUID invitee, UUID partyId, UUID inviter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Party> setMembersCanInvite(UUID leader, boolean enabled) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> leave(UUID playerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Party> kick(UUID leader, UUID member) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Party> transferLeader(UUID leader, UUID newLeader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> disband(UUID leader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> renew(UUID playerId) {
        throw new UnsupportedOperationException();
    }
}
