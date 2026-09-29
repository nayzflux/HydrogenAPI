package fr.nayz.hydrogen.velocity.command.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import fr.nayz.hydrogen.api.account.Account;
import fr.nayz.hydrogen.api.account.PermissionNode;
import fr.nayz.hydrogen.api.account.Rank;
import fr.nayz.hydrogen.api.matchmaking.QueueState;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.velocity.message.VelocityMessages;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class HydrogenCommand implements SimpleCommand {
    private final CoreHydrogenApi core;
    private final AdminTargetResolver targets;
    private final Logger logger;

    public HydrogenCommand(CoreHydrogenApi core, Logger logger) {
        this.core = core;
        this.targets = new AdminTargetResolver(core.directory());
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!hasPermission(invocation)) {
            VelocityMessages.error(source, "Permission manquante pour cette commande.");
            return;
        }
        String[] arguments = invocation.arguments();
        if (arguments.length == 0) {
            help(source);
            return;
        }

        switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "account" -> account(source, arguments);
            case "queue" -> queue(source, arguments);
            case "rank" -> rank(source, arguments);
            default -> help(source);
        }
    }

    private void account(CommandSource source, String[] arguments) {
        if (!allowed(source, PermissionNode.ACCOUNT_INSPECT)) {
            return;
        }
        if (arguments.length != 2) {
            VelocityMessages.error(source, "Utilisation : /hydrogen account <pseudo|uuid>");
            return;
        }
        CompletableFuture<String> operation = targets.resolve(arguments[1])
                .thenCompose(playerId -> core.accounts().find(playerId))
                .thenApply(found -> found.map(this::describeAccount)
                        .orElse("Aucun compte Hydrogen pour ce joueur."));
        reply(source, operation, false);
    }

    private void queue(CommandSource source, String[] arguments) {
        if (!allowed(source, PermissionNode.QUEUE_INSPECT)) {
            return;
        }
        if (arguments.length != 2) {
            VelocityMessages.error(source, "Utilisation : /hydrogen queue <pseudo|uuid>");
            return;
        }
        CompletableFuture<String> operation = targets.resolve(arguments[1])
                .thenCompose(playerId -> core.queueStates().get(playerId))
                .thenApply(found -> found.map(this::describeQueue)
                        .orElse("Ce joueur n'a pas de ticket Hydrogen actif."));
        reply(source, operation, false);
    }

    private void rank(CommandSource source, String[] arguments) {
        if (!allowed(source, PermissionNode.RANK_MANAGE)) {
            return;
        }
        if (arguments.length != 3) {
            VelocityMessages.error(source, "Utilisation : /hydrogen rank <pseudo|uuid> <grade>");
            return;
        }
        Rank rank;
        try {
            rank = Rank.valueOf(arguments[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            VelocityMessages.error(source, "Grade inconnu : " + String.join(", ",
                    Arrays.stream(Rank.values()).map(Enum::name).toList()));
            return;
        }
        CompletableFuture<String> operation = targets.resolve(arguments[1])
                .thenCompose(playerId -> updateRank(playerId, rank))
                .thenApply(account -> "Grade de " + account.playerId() + " : " + account.rank() + ".");
        reply(source, operation, true);
    }

    private CompletableFuture<Account> updateRank(UUID playerId, Rank rank) {
        return core.accounts().find(playerId).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Aucun compte Hydrogen pour ce joueur."));
            }
            return core.accounts().setRank(playerId, rank);
        });
    }

    private String describeAccount(Account account) {
        return "Compte " + account.playerId() + " | grade " + account.rank()
                + " | niveau " + account.level() + " | coins " + account.coins()
                + " | niveaux de jeu " + account.gameLevels();
    }

    private String describeQueue(QueueState state) {
        return "File " + state.queue().displayName() + " | état " + state.status()
                + " | attente " + state.elapsedAt(Instant.now()).toSeconds() + " s"
                + " | membres " + state.members() + " | ticket Hydrogen " + state.ticketId()
                + " | ticket EnderCloud " + state.enderCloudEntryId();
    }

    private boolean allowed(CommandSource source, PermissionNode permission) {
        if (source.hasPermission(permission.node())) {
            return true;
        }
        VelocityMessages.error(source, "Permission manquante pour cette action.");
        return false;
    }

    private void reply(CommandSource source, CompletableFuture<String> operation, boolean mutation) {
        operation.whenComplete((message, error) -> {
            if (error == null) {
                if (mutation) {
                    VelocityMessages.success(source, message);
                } else {
                    VelocityMessages.neutral(source, message);
                }
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause()
                    : error;
            if (cause instanceof IllegalArgumentException) {
                VelocityMessages.error(source, cause.getMessage());
                return;
            }
            logger.warn("Hydrogen admin command failed", cause);
            VelocityMessages.error(source, "Service momentanément indisponible.");
        });
    }

    private void help(CommandSource source) {
        VelocityMessages.helpHeader(source, "Administration Hydrogen");
        if (source.hasPermission(PermissionNode.ACCOUNT_INSPECT.node())) {
            VelocityMessages.helpEntry(source, "/hydrogen account <joueur>", "Voir un compte");
        }
        if (source.hasPermission(PermissionNode.QUEUE_INSPECT.node())) {
            VelocityMessages.helpEntry(source, "/hydrogen queue <joueur>", "Voir une file");
        }
        if (source.hasPermission(PermissionNode.RANK_MANAGE.node())) {
            VelocityMessages.helpEntry(source, "/hydrogen rank <joueur> <grade>", "Changer un grade");
        }
        VelocityMessages.helpFooter(source);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        CommandSource source = invocation.source();
        return source.hasPermission(PermissionNode.ACCOUNT_INSPECT.node())
                || source.hasPermission(PermissionNode.QUEUE_INSPECT.node())
                || source.hasPermission(PermissionNode.RANK_MANAGE.node());
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] arguments = invocation.arguments();
        if (arguments.length == 0 || arguments.length == 1) {
            String prefix = arguments.length == 0 ? "" : arguments[0].toLowerCase(Locale.ROOT);
            List<String> commands = List.of("account", "queue", "rank");
            return CompletableFuture.completedFuture(commands.stream()
                    .filter(command -> source.hasPermission(permissionFor(command).node()))
                    .filter(command -> command.startsWith(prefix))
                    .toList());
        }
        if (arguments.length == 3 && arguments[0].equalsIgnoreCase("rank")
                && source.hasPermission(PermissionNode.RANK_MANAGE.node())) {
            String prefix = arguments[2].toUpperCase(Locale.ROOT);
            return CompletableFuture.completedFuture(Arrays.stream(Rank.values())
                    .map(Enum::name)
                    .filter(name -> name.startsWith(prefix))
                    .toList());
        }
        return CompletableFuture.completedFuture(List.of());
    }

    private PermissionNode permissionFor(String command) {
        return switch (command) {
            case "account" -> PermissionNode.ACCOUNT_INSPECT;
            case "queue" -> PermissionNode.QUEUE_INSPECT;
            case "rank" -> PermissionNode.RANK_MANAGE;
            default -> throw new IllegalArgumentException("Unknown admin command: " + command);
        };
    }
}
