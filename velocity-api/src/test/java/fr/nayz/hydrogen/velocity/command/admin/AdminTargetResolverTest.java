package fr.nayz.hydrogen.velocity.command.admin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdminTargetResolverTest {
    @Test
    void rejectsUnknownAndAmbiguousNames() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(first, AdminTargetResolver.unique("Nino", List.of(first)));

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> AdminTargetResolver.unique("Nino", List.of()));
        assertTrue(unknown.getMessage().contains("Aucun compte"));

        IllegalArgumentException ambiguous = assertThrows(IllegalArgumentException.class,
                () -> AdminTargetResolver.unique("Nino", List.of(first, second)));
        assertTrue(ambiguous.getMessage().contains(first.toString()));
        assertTrue(ambiguous.getMessage().contains(second.toString()));
    }
}
