package fr.nayz.hydrogen.velocity.event.lifecycle;

import com.velocitypowered.api.proxy.ProxyServer;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.core.HydrogenConfig;
import fr.nayz.hydrogen.velocity.event.account.LoginListener;
import fr.nayz.hydrogen.velocity.event.presence.DisconnectListener;
import fr.nayz.hydrogen.velocity.event.presence.PostLoginListener;
import fr.nayz.hydrogen.velocity.event.presence.ServerConnectedListener;
import fr.nayz.hydrogen.velocity.event.matchmaking.QueueServerConnectedListener;
import fr.nayz.hydrogen.velocity.event.matchmaking.QueueDisconnectListener;
import fr.nayz.hydrogen.velocity.matchmaking.VelocityQueueMessenger;
import fr.nayz.hydrogen.velocity.event.rank.PermissionsSetupListener;
import fr.nayz.hydrogen.velocity.presence.VelocityPresenceManager;
import fr.nayz.hydrogen.velocity.rank.VelocityRankManager;
import fr.nayz.hydrogen.velocity.VelocityRuntime;
import fr.nayz.hydrogen.velocity.command.FriendCommand;
import fr.nayz.hydrogen.velocity.command.PartyCommand;
import fr.nayz.hydrogen.velocity.command.PartyInviteLimiter;
import fr.nayz.hydrogen.velocity.command.SocialCommandSupport;
import fr.nayz.hydrogen.velocity.command.admin.HydrogenCommand;
import fr.nayz.hydrogen.velocity.event.social.SocialNotificationListener;
import org.slf4j.Logger;

public final class ProxyInitializeHandler {
    private final ProxyServer proxy;
    private final Logger logger;

    public ProxyInitializeHandler(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    public VelocityRuntime initialize(Object plugin) {
        String proxyId = System.getenv("HYDROGEN_PROXY_ID");
        if (proxyId == null || proxyId.isBlank()) {
            throw new IllegalStateException("HYDROGEN_PROXY_ID is required");
        }

        CoreHydrogenApi core = new CoreHydrogenApi(HydrogenConfig.fromEnvironment());
        VelocityRankManager ranks = new VelocityRankManager(proxy, core, logger);
        VelocityPresenceManager presence = new VelocityPresenceManager(proxy, core, logger, proxyId);
        VelocityQueueMessenger queues = new VelocityQueueMessenger(proxy, core, logger);
        VelocityRuntime runtime = new VelocityRuntime(core, ranks, presence, queues);
        try {
            ranks.start(plugin);
            presence.start(plugin);
            queues.start(plugin);
            proxy.getEventManager().register(plugin, new LoginListener(ranks));
            proxy.getEventManager().register(plugin, new PostLoginListener(presence));
            proxy.getEventManager().register(plugin, new ServerConnectedListener(presence));
            proxy.getEventManager().register(plugin, new QueueServerConnectedListener(queues));
            proxy.getEventManager().register(plugin, new QueueDisconnectListener(queues));
            proxy.getEventManager().register(plugin, new DisconnectListener(presence, ranks));
            proxy.getEventManager().register(plugin, new PermissionsSetupListener(ranks));
            proxy.getEventManager().register(plugin, new ProxyShutdownListener(runtime));
            SocialCommandSupport commands = new SocialCommandSupport(proxy, core, logger, presence);
            PartyInviteLimiter limiter = new PartyInviteLimiter();
            var friendMeta = proxy.getCommandManager().metaBuilder("friend").plugin(plugin).build();
            var partyMeta = proxy.getCommandManager().metaBuilder("party").plugin(plugin).build();
            proxy.getCommandManager().register(friendMeta, new FriendCommand(commands));
            proxy.getCommandManager().register(partyMeta, new PartyCommand(commands, limiter));
            var hydrogenMeta = proxy.getCommandManager().metaBuilder("hydrogen").plugin(plugin).build();
            proxy.getCommandManager().register(hydrogenMeta, new HydrogenCommand(core, logger));
            core.events().onSocialEvent(new SocialNotificationListener(proxy, core, logger)::onEvent);
            return runtime;
        } catch (RuntimeException exception) {
            runtime.close();
            throw exception;
        }
    }
}
