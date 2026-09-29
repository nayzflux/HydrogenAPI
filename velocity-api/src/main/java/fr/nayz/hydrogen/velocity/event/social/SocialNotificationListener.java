package fr.nayz.hydrogen.velocity.event.social;

import com.velocitypowered.api.proxy.ProxyServer;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.core.redis.SocialEvent;
import fr.nayz.hydrogen.velocity.message.VelocityMessages;
import org.slf4j.Logger;

public final class SocialNotificationListener {
    private final ProxyServer proxy;
    private final CoreHydrogenApi core;
    private final Logger logger;

    public SocialNotificationListener(ProxyServer proxy, CoreHydrogenApi core, Logger logger) {
        this.proxy = proxy;
        this.core = core;
        this.logger = logger;
    }

    public void onEvent(SocialEvent event) {
        proxy.getPlayer(event.target()).ifPresent(player ->
                core.directory().names(java.util.List.of(event.actor()))
                        .thenAccept(names -> {
                            String actor = names.getOrDefault(event.actor(), event.actor().toString());
                            String message = message(event.kind(), actor);
                            if (message != null) {
                                switch (event.kind()) {
                                    case "friend-request", "party-invite", "party-change" ->
                                            VelocityMessages.important(player, message);
                                    case "friend-accepted" -> VelocityMessages.success(player, message);
                                    default -> VelocityMessages.neutral(player, message);
                                }
                            }
                        })
                        .exceptionally(error -> {
                            logger.warn("Social notification failed for {}", event.target(), error);
                            return null;
                        }));
    }

    private static String message(String kind, String actor) {
        return switch (kind) {
            case "friend-request" -> actor + " vous a envoyé une demande d'ami. /friend requests";
            case "friend-accepted" -> actor + " a accepté votre demande d'ami.";
            case "friend-removed" -> actor + " vous a retiré de ses amis.";
            case "party-invite" -> actor + " vous invite dans son groupe. /party invites";
            case "party-declined" -> actor + " a refusé votre invitation de groupe.";
            case "party-removed" -> "Vous avez été exclu du groupe par " + actor + ".";
            case "party-disbanded" -> "Le groupe a été dissous.";
            case "party-change" -> "Votre groupe a changé. /party list";
            default -> null;
        };
    }
}
