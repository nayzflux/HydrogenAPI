package fr.nayz.hydrogen.paper.command;

import fr.nayz.hydrogen.paper.matchmaking.QueueJoinResult;
import fr.nayz.hydrogen.api.account.PermissionNode;
import fr.nayz.hydrogen.api.matchmaking.MatchmakingQueue;
import fr.nayz.hydrogen.api.matchmaking.QueueState;
import fr.nayz.hydrogen.api.matchmaking.QueueStateService;
import fr.nayz.hydrogen.paper.matchmaking.MatchmakingService;
import fr.nayz.hydrogen.paper.message.PaperMessages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class MatchmakingCommand implements TabExecutor {
    private final JavaPlugin plugin;
    private final MatchmakingService matchmaking;
    private final QueueStateService queueStates;

    public MatchmakingCommand(
            JavaPlugin plugin,
            MatchmakingService matchmaking,
            QueueStateService queueStates
    ) {
        this.plugin = plugin;
        this.matchmaking = matchmaking;
        this.queueStates = queueStates;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            PaperMessages.error(sender, "Commande réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission(PermissionNode.MATCHMAKING_DEBUG.node())) {
            PaperMessages.error(player, "Permission manquante pour cette commande.");
            return true;
        }
        if (arguments.length == 0) {
            help(player);
            return true;
        }

        switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "join" -> join(player, arguments);
            case "leave" -> leave(player, arguments);
            case "status" -> status(player, arguments);
            case "assignment" -> assignment(player, arguments);
            default -> help(player);
        }
        return true;
    }

    private void join(Player player, String[] arguments) {
        if (arguments.length < 2 || arguments.length > 3) {
            PaperMessages.error(player, "Utilisation : /matchmaking join <skywars-solo|skywars-duo> [solo|party]");
            return;
        }
        MatchmakingQueue queue;
        try {
            queue = MatchmakingQueue.fromGroupId(arguments[1].toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            PaperMessages.error(player, "File inconnue. Utiliser skywars-solo ou skywars-duo.");
            return;
        }

        String mode = arguments.length == 3 ? arguments[2].toLowerCase(Locale.ROOT) : "solo";
        CompletableFuture<QueueJoinResult> operation;
        if (mode.equals("solo")) {
            operation = matchmaking.enqueueSolo(queue, player.getUniqueId());
        } else if (mode.equals("party")) {
            operation = matchmaking.enqueueParty(queue, player.getUniqueId());
        } else {
            PaperMessages.error(player, "Mode inconnu. Utiliser solo ou party.");
            return;
        }
        reply(player, operation, result -> PaperMessages.success(player,
                "File " + queue.displayName() + " : "
                        + (result.selected() ? "partie trouvée" : "en attente")
                        + " (ticket EnderCloud " + result.entryId() + ")."));
    }

    private void leave(Player player, String[] arguments) {
        if (arguments.length != 1) {
            PaperMessages.error(player, "Utilisation : /matchmaking leave");
            return;
        }
        reply(player, matchmaking.leaveQueue(player.getUniqueId()), accepted -> {
            if (accepted) {
                PaperMessages.success(player, "Sortie de file confirmée.");
            } else {
                PaperMessages.error(player, "Impossible de quitter la file à ce stade, ou aucune file active.");
            }
        });
    }

    private void status(Player player, String[] arguments) {
        if (arguments.length != 1) {
            PaperMessages.error(player, "Utilisation : /matchmaking status");
            return;
        }
        reply(player, queueStates.get(player.getUniqueId()), found ->
                PaperMessages.neutral(player, found.map(this::describe).orElse("Aucune file active.")));
    }

    private void assignment(Player player, String[] arguments) {
        if (arguments.length != 1) {
            PaperMessages.error(player, "Utilisation : /matchmaking assignment");
            return;
        }
        String message = matchmaking.currentAssignment()
                .map(value -> "Affectation courante : " + value)
                .orElse("Aucune affectation courante sur ce serveur.");
        PaperMessages.neutral(player, message);
    }

    private String describe(QueueState state) {
        long seconds = state.elapsedAt(Instant.now()).toSeconds();
        return "File " + state.queue().displayName() + " | état " + state.status()
                + " | attente " + seconds + " s | membres " + state.members().size()
                + " | ticket Hydrogen " + state.ticketId()
                + " | ticket EnderCloud " + state.enderCloudEntryId();
    }

    private <T> void reply(Player player, CompletableFuture<T> operation,
                           java.util.function.Consumer<T> onSuccess) {
        operation.whenComplete((result, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (error != null) {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null
                            ? error.getCause()
                            : error;
                    PaperMessages.error(player, "Matchmaking indisponible : " + cause.getMessage());
                    return;
                }
                onSuccess.accept(result);
            });
        });
    }

    private void help(Player player) {
        PaperMessages.helpHeader(player, "Matchmaking");
        PaperMessages.helpEntry(player, "/matchmaking join <file> [solo|party]", "Rejoindre une file");
        PaperMessages.helpEntry(player, "/matchmaking leave", "Quitter la file");
        PaperMessages.helpEntry(player, "/matchmaking status", "Voir la file active");
        PaperMessages.helpEntry(player, "/matchmaking assignment", "Voir l'affectation");
        PaperMessages.helpFooter(player);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (arguments.length == 0) {
            return List.of();
        }
        if (!(sender instanceof Player player)
                || !player.hasPermission(PermissionNode.MATCHMAKING_DEBUG.node())) {
            return List.of();
        }
        List<String> options = switch (arguments.length) {
            case 1 -> List.of("join", "leave", "status", "assignment");
            case 2 -> arguments[0].equalsIgnoreCase("join")
                    ? Arrays.stream(MatchmakingQueue.values()).map(MatchmakingQueue::groupId).toList()
                    : List.of();
            case 3 -> arguments[0].equalsIgnoreCase("join") ? List.of("solo", "party") : List.of();
            default -> List.of();
        };
        String prefix = arguments[arguments.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
