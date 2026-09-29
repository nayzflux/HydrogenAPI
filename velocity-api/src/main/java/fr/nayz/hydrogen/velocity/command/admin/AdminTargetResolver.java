package fr.nayz.hydrogen.velocity.command.admin;

import fr.nayz.hydrogen.core.directory.PlayerDirectory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AdminTargetResolver {
    private final PlayerDirectory directory;

    public AdminTargetResolver(PlayerDirectory directory) {
        this.directory = directory;
    }

    public CompletableFuture<UUID> resolve(String argument) {
        try {
            return CompletableFuture.completedFuture(UUID.fromString(argument));
        } catch (IllegalArgumentException ignored) {
            // Un pseudo connu peut aussi désigner un joueur déconnecté.
        }
        try {
            return directory.knownByName(argument).thenApply(matches -> unique(argument, matches));
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    static UUID unique(String name, List<UUID> matches) {
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Aucun compte connu pour " + name + ".");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Pseudo ambigu. Utilisez un UUID : "
                    + String.join(", ", matches.stream().map(UUID::toString).toList()));
        }
        return matches.getFirst();
    }
}
