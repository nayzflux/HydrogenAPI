package fr.nayz.hydrogen.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.nayz.hydrogen.api.account.AccountService;
import fr.nayz.hydrogen.api.friend.FriendService;
import fr.nayz.hydrogen.api.inventory.InventoryService;
import fr.nayz.hydrogen.api.matchmaking.QueueStateService;
import fr.nayz.hydrogen.api.party.PartyService;
import fr.nayz.hydrogen.api.presence.PresenceService;
import fr.nayz.hydrogen.api.shop.ShopService;
import fr.nayz.hydrogen.velocity.event.lifecycle.ProxyInitializeHandler;
import org.slf4j.Logger;

@Plugin(id = "hydrogenapi", name = "HydrogenAPI", version = "0.7.3",
        description = "Shared Hydrogen services for Velocity plugins")
public final class HydrogenVelocityPlugin implements HydrogenVelocityApi {
    private final ProxyServer proxy;
    private final Logger logger;
    private VelocityRuntime runtime;

    @Inject
    public HydrogenVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        runtime = new ProxyInitializeHandler(proxy, logger).initialize(this);
    }

    @Override
    public AccountService accounts() {
        return runtime.core().accounts();
    }

    @Override
    public FriendService friends() {
        return runtime.core().friends();
    }

    @Override
    public PartyService parties() {
        return runtime.core().parties();
    }

    @Override
    public PresenceService presence() {
        return runtime.core().presence();
    }

    @Override
    public ShopService shop() {
        return runtime.core().shop();
    }

    @Override
    public InventoryService inventory() {
        return runtime.core().inventory();
    }

    @Override
    public QueueStateService queueStates() {
        return runtime.core().queueStates();
    }
}
