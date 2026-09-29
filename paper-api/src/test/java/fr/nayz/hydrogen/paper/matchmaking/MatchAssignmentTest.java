package fr.nayz.hydrogen.paper.matchmaking;

import fr.nayz.endercloud.core.model.SessionAssignment;
import fr.nayz.hydrogen.api.matchmaking.MatchmakingQueue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MatchAssignmentTest {
    @Test
    void convertsFrozenPartyIdsWithoutExposingCloudModels() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        FakeCloud cloud = new FakeCloud();
        cloud.assignment(new SessionAssignment(
                "session-1", "skywars-duo", null, 1, 2, 2,
                false, false, List.of(), List.of(), List.of(
                        new SessionAssignment.AssignedPlayer(first.toString(), partyId.toString(), "ticket-1", null),
                        new SessionAssignment.AssignedPlayer(second.toString(), partyId.toString(), "ticket-1", null)
                )
        ));

        MatchAssignment assignment = new EnderCloudMatchmakingService(cloud, null, null)
                .currentAssignment().orElseThrow();

        assertEquals("session-1", assignment.sessionId());
        assertEquals(MatchmakingQueue.SKYWARS_DUO, assignment.queue());
        assertEquals(Map.of(first, partyId.toString(), second, partyId.toString()),
                assignment.partyIds());
        assertThrows(UnsupportedOperationException.class,
                () -> assignment.partyIds().put(UUID.randomUUID(), "other"));
    }
}
