package fr.nayz.hydrogen.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import fr.nayz.hydrogen.api.account.PermissionNode;
import fr.nayz.hydrogen.velocity.message.VelocityMessages;
import fr.nayz.hydrogen.api.party.Party;
import fr.nayz.hydrogen.api.party.PartyInvitation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PartyCommand implements SimpleCommand {
    private static final List<String> SUBCOMMANDS = List.of(
            "create", "invite", "invites", "accept", "decline", "list",
            "leave", "kick", "leader", "disband");
    private final SocialCommandSupport support;
    private final PartyInviteLimiter limiter;

    public PartyCommand(SocialCommandSupport support, PartyInviteLimiter limiter) {
        this.support = support;
        this.limiter = limiter;
    }

    @Override
    public void execute(Invocation invocation) {
        Player player = support.player(invocation.source());
        if (player == null) {
            return;
        }
        if (!allowed(player)) {
            support.error(player, "Permission manquante pour /party.");
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
                    support.error(player, "Permission manquante pour /party.");
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
        return player.hasPermission(PermissionNode.PARTY_COMMAND.node());
    }

    private void executeReady(Player player, String[] arguments) {
        if (arguments.length == 0) {
            help(player);
            return;
        }
        try {
            switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
                case "create" -> create(player, arguments);
                case "invite" -> invite(player, arguments);
                case "invites" -> invites(player, arguments);
                case "accept" -> answer(player, arguments, true);
                case "decline" -> answer(player, arguments, false);
                case "list" -> list(player, arguments);
                case "leave" -> leave(player, arguments);
                case "kick" -> memberAction(player, arguments, false);
                case "leader" -> memberAction(player, arguments, true);
                case "disband" -> disband(player, arguments);
                default -> help(player);
            }
        } catch (CommandFailure failure) {
            support.error(player, failure.getMessage());
        }
    }

    private void create(Player player, String[] arguments) {
        support.requireArguments(arguments, 1, "/party create");
        support.finish(player, support.core().parties().create(player.getUniqueId()),
                "Groupe créé. Les membres peuvent inviter d'autres joueurs.");
    }

    private void invite(Player player, String[] arguments) {
        support.requireArguments(arguments, 2, "/party invite <pseudo>");
        UUID author = player.getUniqueId();
        if (!limiter.allow(author)) {
            throw new CommandFailure("Trop d'invitations. Réessayez dans quelques secondes.");
        }
        CompletableFuture<?> operation = support.online(arguments[1]).thenCompose(target -> {
            return support.core().parties().getForPlayer(author)
                    .thenCompose(existing -> existing.isPresent()
                            ? CompletableFuture.completedFuture(existing.get())
                            : support.core().parties().create(author))
                    .thenCompose(party -> support.core().parties().invite(author, target));
        });
        support.finish(player, operation, "Invitation de groupe envoyée.");
    }

    private void invites(Player player, String[] arguments) {
        int page = support.page(arguments, 1);
        CompletableFuture<Void> operation = support.core().parties().invitations(player.getUniqueId())
                .thenCompose(invitations -> {
                    List<UUID> inviters = invitations.stream().map(PartyInvitation::inviter).toList();
                    return support.core().directory().names(inviters).thenAccept(names -> {
                        int pages = Math.max(1, (invitations.size() + 9) / 10);
                        if (page > pages) {
                            throw new CommandFailure("Cette page n'existe pas. Dernière page : " + pages + ".");
                        }
                        support.important(player, "Invitations de groupe (" + page + "/" + pages + ") :");
                        if (invitations.isEmpty()) {
                            support.send(player, "Aucune invitation.");
                        }
                        int end = Math.min(invitations.size(), page * 10);
                        for (int index = (page - 1) * 10; index < end; index++) {
                            PartyInvitation invitation = invitations.get(index);
                            support.send(player, "- " + support.display(invitation.inviter(), names));
                        }
                    });
                });
        support.show(player, operation);
    }

    private void answer(Player player, String[] arguments, boolean accept) {
        support.requireArguments(arguments, 2,
                accept ? "/party accept <pseudo>" : "/party decline <pseudo>");
        UUID invitee = player.getUniqueId();
        CompletableFuture<?> operation = support.core().parties().invitations(invitee)
                .thenCompose(invitations -> support.related(arguments[1],
                                invitations.stream().map(PartyInvitation::inviter).toList())
                        .thenApply(inviter -> invitations.stream()
                                .filter(invitation -> invitation.inviter().equals(inviter))
                                .findFirst()
                                .orElseThrow()))
                .thenCompose(invitation -> {
                    if (accept) {
                        return support.core().parties()
                                .accept(invitee, invitation.partyId(), invitation.inviter())
                                .thenApply(ignored -> null);
                    }
                    return support.core().parties()
                            .decline(invitee, invitation.partyId(), invitation.inviter());
                });
        support.finish(player, operation,
                accept ? "Vous avez rejoint le groupe." : "Invitation refusée.");
    }

    private void list(Player player, String[] arguments) {
        support.requireArguments(arguments, 1, "/party list");
        CompletableFuture<Void> operation = requiredParty(player.getUniqueId())
                .thenCompose(party -> support.core().directory().names(party.members())
                        .thenAccept(names -> {
                            support.important(player, "Groupe (" + party.members().size() + "/12) :");
                            for (UUID member : party.members()) {
                                String role = member.equals(party.leader()) ? "chef" : "membre";
                                support.send(player, "- " + support.display(member, names) + " : " + role);
                            }
                            support.send(player, party.membersCanInvite()
                                    ? "Tous les membres peuvent inviter."
                                    : "Seul le chef peut inviter.");
                        }));
        support.show(player, operation);
    }

    private void leave(Player player, String[] arguments) {
        support.requireArguments(arguments, 1, "/party leave");
        support.finish(player, support.core().parties().leave(player.getUniqueId()),
                "Vous avez quitté le groupe.");
    }

    private void memberAction(Player player, String[] arguments, boolean transfer) {
        support.requireArguments(arguments, 2,
                transfer ? "/party leader <pseudo>" : "/party kick <pseudo>");
        UUID leader = player.getUniqueId();
        CompletableFuture<?> operation = requiredParty(leader)
                .thenCompose(party -> support.related(arguments[1], party.members()))
                .thenCompose(member -> transfer
                        ? support.core().parties().transferLeader(leader, member)
                        : support.core().parties().kick(leader, member));
        support.finish(player, operation,
                transfer ? "Nouveau chef désigné." : "Membre exclu du groupe.");
    }

    private void disband(Player player, String[] arguments) {
        support.requireArguments(arguments, 1, "/party disband");
        support.finish(player, support.core().parties().disband(player.getUniqueId()),
                "Groupe dissous.");
    }

    private CompletableFuture<Party> requiredParty(UUID playerId) {
        return support.core().parties().getForPlayer(playerId)
                .thenApply(found -> found.orElseThrow(
                        () -> new CommandFailure("Vous n'avez pas de groupe.")));
    }

    private void help(Player player) {
        VelocityMessages.helpHeader(player, "Système de groupe");
        VelocityMessages.helpEntry(player, "/party create", "Créer un groupe");
        VelocityMessages.helpEntry(player, "/party invite <pseudo>", "Inviter un joueur");
        VelocityMessages.helpEntry(player, "/party invites [page]", "Voir les invitations");
        VelocityMessages.helpEntry(player, "/party accept <pseudo>", "Rejoindre un groupe");
        VelocityMessages.helpEntry(player, "/party decline <pseudo>", "Refuser une invitation");
        VelocityMessages.helpEntry(player, "/party list", "Voir le groupe");
        VelocityMessages.helpEntry(player, "/party leave", "Quitter le groupe");
        VelocityMessages.helpEntry(player, "/party kick <pseudo>", "Exclure un membre");
        VelocityMessages.helpEntry(player, "/party leader <pseudo>", "Nommer un chef");
        VelocityMessages.helpEntry(player, "/party disband", "Dissoudre le groupe");
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
