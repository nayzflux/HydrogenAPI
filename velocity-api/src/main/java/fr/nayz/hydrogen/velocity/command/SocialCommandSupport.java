package fr.nayz.hydrogen.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.nayz.hydrogen.api.HydrogenException;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.velocity.presence.VelocityPresenceManager;
import fr.nayz.hydrogen.velocity.message.VelocityMessages;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class SocialCommandSupport {
    private final ProxyServer proxy;
    private final CoreHydrogenApi core;
    private final Logger logger;
    private final VelocityPresenceManager presence;

    public SocialCommandSupport(ProxyServer proxy, CoreHydrogenApi core, Logger logger,
                                VelocityPresenceManager presence) {
        this.proxy = proxy;
        this.core = core;
        this.logger = logger;
        this.presence = presence;
    }

    public Player player(CommandSource source) {
        if (source instanceof Player player) {
            return player;
        }
        error(source, "Commande réservée aux joueurs.");
        return null;
    }

    public CoreHydrogenApi core() {
        return core;
    }

    public CompletableFuture<Void> ready(Player player) {
        return presence.ready(player.getUniqueId());
    }

    public boolean connected(Player player) {
        return proxy.getPlayer(player.getUniqueId())
                .filter(current -> current == player)
                .isPresent();
    }

    public void send(CommandSource source, String message) {
        VelocityMessages.neutral(source, message);
    }

    public void success(CommandSource source, String message) {
        VelocityMessages.success(source, message);
    }

    public void error(CommandSource source, String message) {
        VelocityMessages.error(source, message);
    }

    public void important(CommandSource source, String message) {
        VelocityMessages.important(source, message);
    }

    public void finish(Player player, CompletableFuture<?> operation, String success) {
        operation.whenComplete((result, error) -> {
            if (error == null) {
                success(player, success);
            } else {
                error(player, message(error));
            }
        });
    }

    public void show(Player player, CompletableFuture<?> operation) {
        operation.exceptionally(error -> {
            error(player, message(error));
            return null;
        });
    }

    public CompletableFuture<UUID> online(String name) {
        try {
            return core.directory().online(name).thenApply(found -> found.orElseThrow(
                    () -> new CommandFailure("Ce joueur n'est pas connecté au réseau.")));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<UUID> related(String argument, Collection<UUID> candidates) {
        if (candidates.isEmpty()) {
            return CompletableFuture.failedFuture(new CommandFailure("Aucun joueur correspondant."));
        }
        return core.directory().names(candidates)
                .thenApply(names -> CandidateResolver.resolve(argument, candidates, names));
    }

    public CompletableFuture<Void> list(Player player, String title, List<UUID> ids, int page) {
        return core.directory().names(ids).thenAccept(names -> {
            int pages = Math.max(1, (ids.size() + 9) / 10);
            if (page > pages) {
                throw new CommandFailure("Cette page n'existe pas. Dernière page : " + pages + ".");
            }
            important(player, title + " (" + page + "/" + pages + ") :");
            if (ids.isEmpty()) {
                send(player, "Aucune entrée.");
                return;
            }
            int start = (page - 1) * 10;
            int end = Math.min(ids.size(), start + 10);
            for (UUID id : ids.subList(start, end)) {
                send(player, "- " + display(id, names));
            }
        });
    }

    public String display(UUID id, Map<UUID, String> names) {
        return names.getOrDefault(id, id.toString()) + " (" + id + ")";
    }

    public int page(String[] arguments, int index) {
        if (arguments.length <= index) {
            return 1;
        }
        if (arguments.length > index + 1) {
            throw new CommandFailure("Trop d'arguments.");
        }
        try {
            int page = Integer.parseInt(arguments[index]);
            if (page > 0) {
                return page;
            }
        } catch (NumberFormatException ignored) {
            // Report one consistent error below.
        }
        throw new CommandFailure("Le numéro de page doit être un entier positif.");
    }

    public void requireArguments(String[] arguments, int count, String usage) {
        if (arguments.length != count) {
            throw new CommandFailure("Utilisation : " + usage);
        }
    }

    public String message(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof CommandFailure) {
            return cause.getMessage();
        }
        if (cause instanceof HydrogenException hydrogen) {
            if (hydrogen.getMessage().startsWith("Invitation cooldown:")) {
                return "Patientez avant de renvoyer une invitation à ce joueur.";
            }
            return switch (hydrogen.reason()) {
                case NOT_FOUND -> "Cette demande, invitation ou groupe n'existe plus.";
                case CONFLICT -> "Cette action est impossible dans l'état actuel.";
                case FORBIDDEN -> "Vous n'avez pas le droit de faire cette action.";
                case INVALID_INPUT -> "L'argument fourni est invalide.";
                case UNAVAILABLE -> "Le service est momentanément indisponible. Réessayez.";
                case INSUFFICIENT_COINS -> "Solde insuffisant.";
            };
        }
        logger.error("Social command failed", cause);
        return "Une erreur inattendue est survenue.";
    }

    public List<String> suggestions(String[] arguments, List<String> options) {
        if (arguments.length > 1) {
            return List.of();
        }
        String prefix = arguments.length == 0 ? "" : arguments[0].toLowerCase(java.util.Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(prefix)) {
                suggestions.add(option);
            }
        }
        return suggestions;
    }
}
