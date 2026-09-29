package fr.nayz.hydrogen.velocity.command;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CandidateResolver {
    private CandidateResolver() {
    }

    static UUID resolve(String argument, Collection<UUID> candidates, Map<UUID, String> names) {
        try {
            UUID id = UUID.fromString(argument);
            if (candidates.contains(id)) {
                return id;
            }
        } catch (IllegalArgumentException ignored) {
            // The argument can be a name.
        }
        List<UUID> matches = candidates.stream()
                .filter(id -> argument.equalsIgnoreCase(names.get(id)))
                .distinct()
                .toList();
        if (matches.isEmpty()) {
            throw new CommandFailure("Aucun joueur correspondant. Utilisez le pseudo affiché ou son UUID.");
        }
        if (matches.size() > 1) {
            throw new CommandFailure("Pseudo ambigu. Utilisez un UUID : "
                    + String.join(", ", matches.stream().map(UUID::toString).toList()));
        }
        return matches.getFirst();
    }
}
