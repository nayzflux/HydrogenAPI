package fr.nayz.hydrogen.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import fr.nayz.hydrogen.api.account.PermissionNode;
import fr.nayz.hydrogen.velocity.message.VelocityMessages;
import fr.nayz.hydrogen.api.friend.FriendRequest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FriendCommand implements SimpleCommand {
    private static final List<String> SUBCOMMANDS = List.of(
            "add", "requests", "accept", "decline", "remove", "list");
    private final SocialCommandSupport support;

    public FriendCommand(SocialCommandSupport support) {
        this.support = support;
    }

    @Override
    public void execute(Invocation invocation) {
        Player player = support.player(invocation.source());
        if (player == null) {
            return;
        }
        if (!allowed(player)) {
            support.error(player, "Permission manquante pour /friend.");
            return;
        }
        String[] arguments = invocation.arguments();
        support.ready(player).whenComplete((ignored, error) -> {
            if (error != null) {
                support.error(player, support.message(error));
            } else if (support.connected(player)) {
                if (allowed(player)) {
                    executeReady(player, arguments);
                } else {
                    support.error(player, "Permission manquante pour /friend.");
                }
            }
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if (invocation.source() instanceof Player player) {
            return allowed(player);
        }
        return true;
    }

    private boolean allowed(Player player) {
        return player.hasPermission(PermissionNode.FRIEND_COMMAND.node());
    }

    private void executeReady(Player player, String[] arguments) {
        if (arguments.length == 0) {
            help(player);
            return;
        }
        try {
            switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
                case "add" -> add(player, arguments);
                case "requests" -> requests(player, arguments);
                case "accept" -> answer(player, arguments, true);
                case "decline" -> answer(player, arguments, false);
                case "remove" -> remove(player, arguments);
                case "list" -> list(player, arguments);
                default -> help(player);
            }
        } catch (CommandFailure failure) {
            support.error(player, failure.getMessage());
        }
    }

    private void add(Player player, String[] arguments) {
        support.requireArguments(arguments, 2, "/friend add <pseudo>");
        UUID sender = player.getUniqueId();
        CompletableFuture<Void> operation = support.online(arguments[1])
                .thenCompose(recipient -> support.core().friends().request(sender, recipient));
        support.finish(player, operation, "Demande d'ami envoyée.");
    }

    private void requests(Player player, String[] arguments) {
        int page = support.page(arguments, 1);
        UUID playerId = player.getUniqueId();
        CompletableFuture<Void> operation = support.core().friends().pending(playerId)
                .thenCompose(requests -> {
                    List<UUID> ids = requests.stream()
                            .map(request -> request.sender().equals(playerId)
                                    ? request.recipient() : request.sender())
                            .toList();
                    return support.core().directory().names(ids).thenAccept(names -> {
                        int pages = Math.max(1, (requests.size() + 9) / 10);
                        if (page > pages) {
                            throw new CommandFailure("Cette page n'existe pas. Dernière page : " + pages + ".");
                        }
                        support.important(player, "Demandes d'amis (" + page + "/" + pages + ") :");
                        if (requests.isEmpty()) {
                            support.send(player, "Aucune demande.");
                        }
                        int end = Math.min(requests.size(), page * 10);
                        for (int index = (page - 1) * 10; index < end; index++) {
                            FriendRequest request = requests.get(index);
                            UUID other = ids.get(index);
                            String direction = request.sender().equals(playerId) ? "envoyée" : "reçue";
                            support.send(player, "- " + direction + " : " + support.display(other, names));
                        }
                    });
                });
        support.show(player, operation);
    }

    private void answer(Player player, String[] arguments, boolean accept) {
        support.requireArguments(arguments, 2,
                accept ? "/friend accept <pseudo>" : "/friend decline <pseudo>");
        UUID recipient = player.getUniqueId();
        CompletableFuture<Void> operation = support.core().friends().pending(recipient)
                .thenCompose(requests -> {
                    List<UUID> senders = requests.stream()
                            .filter(request -> request.recipient().equals(recipient))
                            .map(FriendRequest::sender)
                            .toList();
                    return support.related(arguments[1], senders);
                })
                .thenCompose(sender -> accept
                        ? support.core().friends().accept(recipient, sender)
                        : support.core().friends().decline(recipient, sender));
        support.finish(player, operation,
                accept ? "Demande d'ami acceptée." : "Demande d'ami refusée.");
    }

    private void remove(Player player, String[] arguments) {
        support.requireArguments(arguments, 2, "/friend remove <pseudo>");
        UUID playerId = player.getUniqueId();
        CompletableFuture<Void> operation = support.core().friends().list(playerId)
                .thenCompose(friends -> support.related(arguments[1], friends))
                .thenCompose(friend -> support.core().friends().remove(playerId, friend));
        support.finish(player, operation, "Ami retiré.");
    }

    private void list(Player player, String[] arguments) {
        int page = support.page(arguments, 1);
        CompletableFuture<Void> operation = support.core().friends().list(player.getUniqueId())
                .thenCompose(friends -> support.list(player, "Amis", friends, page));
        support.show(player, operation);
    }

    private void help(Player player) {
        VelocityMessages.helpHeader(player, "Système d'amis");
        VelocityMessages.helpEntry(player, "/friend add <pseudo>", "Ajouter un ami");
        VelocityMessages.helpEntry(player, "/friend requests [page]", "Voir les demandes");
        VelocityMessages.helpEntry(player, "/friend accept <pseudo>", "Accepter une demande");
        VelocityMessages.helpEntry(player, "/friend decline <pseudo>", "Refuser une demande");
        VelocityMessages.helpEntry(player, "/friend remove <pseudo>", "Retirer un ami");
        VelocityMessages.helpEntry(player, "/friend list [page]", "Voir les amis");
        VelocityMessages.helpFooter(player);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        if (!hasPermission(invocation)) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.completedFuture(
                support.suggestions(invocation.arguments(), SUBCOMMANDS));
    }
}
