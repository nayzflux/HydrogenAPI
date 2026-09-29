package fr.nayz.hydrogen.velocity.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CandidateResolverTest {
    @Test
    void resolvesNamesWithoutCaseAndOffersUuidForAmbiguousNames() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<UUID> candidates = List.of(first, second);
        Map<UUID, String> names = Map.of(first, "Joueur", second, "JOUEUR");

        CommandFailure failure = assertThrows(CommandFailure.class,
                () -> CandidateResolver.resolve("joueur", candidates, names));
        assertTrue(failure.getMessage().contains(first.toString()));
        assertTrue(failure.getMessage().contains(second.toString()));
        assertEquals(first, CandidateResolver.resolve(first.toString(), candidates, names));
    }

    @Test
    void onlyAcceptsRecordedCandidates() {
        UUID friend = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        assertThrows(CommandFailure.class,
                () -> CandidateResolver.resolve(stranger.toString(), List.of(friend),
                        Map.of(friend, "Friend")));
    }
}
