package fr.nayz.hydrogen.core;

import fr.nayz.hydrogen.api.account.AccountService;
import fr.nayz.hydrogen.api.friend.FriendService;
import fr.nayz.hydrogen.api.HydrogenApi;
import fr.nayz.hydrogen.api.inventory.InventoryService;
import fr.nayz.hydrogen.api.matchmaking.QueueStateService;
import fr.nayz.hydrogen.api.party.PartyService;
import fr.nayz.hydrogen.api.presence.Presence;
import fr.nayz.hydrogen.api.presence.PresenceService;
import fr.nayz.hydrogen.api.shop.ShopService;
import fr.nayz.hydrogen.core.account.JdbcAccountService;
import fr.nayz.hydrogen.core.account.JdbcRankReader;
import fr.nayz.hydrogen.core.database.Database;
import fr.nayz.hydrogen.core.directory.PlayerDirectory;
import fr.nayz.hydrogen.core.friend.JdbcFriendService;
import fr.nayz.hydrogen.core.inventory.JdbcInventoryService;
import fr.nayz.hydrogen.core.matchmaking.JdbcQueueStateService;
import fr.nayz.hydrogen.core.party.JdbcPartyService;
import fr.nayz.hydrogen.core.presence.RedisPresenceService;
import fr.nayz.hydrogen.core.redis.RedisCoordinator;
import fr.nayz.hydrogen.core.shop.JdbcShopService;

public final class CoreHydrogenApi implements HydrogenApi, AutoCloseable {
    private final Database database;
    private final RedisCoordinator redis;
    private final AccountService accounts;
    private final FriendService friends;
    private final PartyService parties;
    private final PresenceService presence;
    private final ShopService shop;
    private final InventoryService inventory;
    private final PlayerDirectory directory;
    private final JdbcRankReader rankReader;
    private final JdbcQueueStateService queueStates;

    public CoreHydrogenApi(HydrogenConfig config) {
        database = new Database(config);
        try {
            redis = new RedisCoordinator(config);
        } catch (RuntimeException exception) {
            database.close();
            throw exception;
        }
        accounts = new JdbcAccountService(database, redis);
        rankReader = new JdbcRankReader(database);
        friends = new JdbcFriendService(database, redis);
        parties = new JdbcPartyService(database, redis);
        presence = new RedisPresenceService(redis);
        shop = new JdbcShopService(database);
        inventory = new JdbcInventoryService(database);
        directory = new PlayerDirectory(database, redis);
        queueStates = new JdbcQueueStateService(database, redis);
    }

    public RedisCoordinator events() {
        return redis;
    }

    public PlayerDirectory directory() {
        return directory;
    }

    public JdbcRankReader rankReader() {
        return rankReader;
    }

    @Override
    public QueueStateService queueStates() {
        return queueStates;
    }

    public JdbcQueueStateService queueStore() {
        return queueStates;
    }

    @Override
    public AccountService accounts() {
        return accounts;
    }

    @Override
    public FriendService friends() {
        return friends;
    }

    @Override
    public PartyService parties() {
        return parties;
    }

    @Override
    public PresenceService presence() {
        return presence;
    }

    @Override
    public ShopService shop() {
        return shop;
    }

    @Override
    public InventoryService inventory() {
        return inventory;
    }

    @Override
    public void close() {
        redis.close();
        database.close();
    }
}
