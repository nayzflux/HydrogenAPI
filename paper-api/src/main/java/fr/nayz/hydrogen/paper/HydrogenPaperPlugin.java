package fr.nayz.hydrogen.paper;

import fr.nayz.endercloud.core.api.EnderCloudPaperApi;
import fr.nayz.hydrogen.api.account.AccountService;
import fr.nayz.hydrogen.api.friend.FriendService;
import fr.nayz.hydrogen.api.inventory.InventoryService;
import fr.nayz.hydrogen.api.matchmaking.QueueStateService;
import fr.nayz.hydrogen.api.party.PartyService;
import fr.nayz.hydrogen.api.presence.PresenceService;
import fr.nayz.hydrogen.api.shop.ShopService;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.core.HydrogenConfig;
import fr.nayz.hydrogen.paper.command.MatchmakingCommand;
import fr.nayz.hydrogen.paper.event.account.AsyncPlayerPreLoginListener;
import fr.nayz.hydrogen.paper.event.rank.PlayerJoinListener;
import fr.nayz.hydrogen.paper.event.rank.PlayerQuitListener;
import fr.nayz.hydrogen.paper.event.rank.RankChatListener;
import fr.nayz.hydrogen.paper.event.matchmaking.QueueClickListener;
import fr.nayz.hydrogen.paper.event.matchmaking.QueueDragListener;
import fr.nayz.hydrogen.paper.event.matchmaking.QueueDropListener;
import fr.nayz.hydrogen.paper.event.matchmaking.QueueInteractListener;
import fr.nayz.hydrogen.paper.event.matchmaking.QueueJoinListener;
import fr.nayz.hydrogen.paper.event.matchmaking.QueueQuitListener;
import fr.nayz.hydrogen.paper.event.matchmaking.QueueSwapListener;
import fr.nayz.hydrogen.paper.matchmaking.EnderCloudMatchmakingService;
import fr.nayz.hydrogen.paper.matchmaking.MatchmakingService;
import fr.nayz.hydrogen.paper.matchmaking.QueueInventoryManager;
import fr.nayz.hydrogen.paper.transfer.HubTransferService;
import fr.nayz.hydrogen.paper.transfer.EnderCloudHubTransferService;
import fr.nayz.hydrogen.paper.scoreboards.ScoreboardService;
import fr.nayz.hydrogen.paper.scoreboards.ScoreboardServiceImpl;
import fr.nayz.hydrogen.paper.scoreboards.ScoreboardJoinListener;
import fr.nayz.hydrogen.paper.scoreboards.ScoreboardQuitListener;
import fr.nayz.hydrogen.paper.gui.GuiClickListener;
import fr.nayz.hydrogen.paper.gui.GuiOpenListener;
import fr.nayz.hydrogen.paper.gui.GuiCloseListener;
import fr.nayz.hydrogen.paper.rank.PaperRankManager;
import fr.nayz.hydrogen.paper.rank.PaperChatRenderer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

import java.util.Objects;

public final class HydrogenPaperPlugin extends JavaPlugin implements HydrogenPaperApi {
    private CoreHydrogenApi core;
    private MatchmakingService matchmaking;
    private PaperRankManager ranks;
    private QueueInventoryManager queueInventory;
    private HubTransferService transfers;
    private ScoreboardServiceImpl scoreboards;
    private PaperChatRenderer chat;

    @Override
    public void onEnable() {
        EnderCloudPaperApi cloud = Bukkit.getServicesManager().load(EnderCloudPaperApi.class);
        if (cloud == null) {
            getLogger().severe("EnderCloud Paper service is required");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try {
            core = new CoreHydrogenApi(HydrogenConfig.fromEnvironment());
            EnderCloudMatchmakingService enderMatchmaking =
                    new EnderCloudMatchmakingService(cloud, core.parties(), core.queueStore());
            matchmaking = enderMatchmaking;
            transfers = new EnderCloudHubTransferService(cloud);
            scoreboards = new ScoreboardServiceImpl();
            scoreboards.enable(this);
            queueInventory = new QueueInventoryManager(this, core, enderMatchmaking);
            ranks = new PaperRankManager(this, core);
            ranks.start();
            Objects.requireNonNull(getCommand("matchmaking"))
                    .setExecutor(new MatchmakingCommand(this, matchmaking, core.queueStates()));
            Bukkit.getServicesManager().register(HydrogenPaperApi.class, this, this, ServicePriority.Normal);
            Bukkit.getPluginManager().registerEvents(new AsyncPlayerPreLoginListener(ranks), this);
            Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(ranks), this);
            Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(ranks), this);
            chat = new PaperChatRenderer(ranks::rankFor);
            Bukkit.getPluginManager().registerEvents(new RankChatListener(chat), this);
            Bukkit.getPluginManager().registerEvents(new ScoreboardJoinListener(scoreboards), this);
            Bukkit.getPluginManager().registerEvents(new ScoreboardQuitListener(scoreboards), this);
            Bukkit.getPluginManager().registerEvents(new GuiClickListener(), this);
            Bukkit.getPluginManager().registerEvents(new GuiOpenListener(), this);
            Bukkit.getPluginManager().registerEvents(new GuiCloseListener(), this);
            Bukkit.getPluginManager().registerEvents(new QueueJoinListener(queueInventory), this);
            Bukkit.getPluginManager().registerEvents(new QueueQuitListener(queueInventory), this);
            Bukkit.getPluginManager().registerEvents(new QueueClickListener(queueInventory), this);
            Bukkit.getPluginManager().registerEvents(new QueueDragListener(queueInventory), this);
            Bukkit.getPluginManager().registerEvents(new QueueDropListener(queueInventory), this);
            Bukkit.getPluginManager().registerEvents(new QueueSwapListener(queueInventory), this);
            Bukkit.getPluginManager().registerEvents(new QueueInteractListener(queueInventory), this);
            queueInventory.start();
        } catch (RuntimeException exception) {
            getLogger().severe("Hydrogen startup failed: " + exception.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        if (ranks != null) {
            ranks.close();
        }
        if (queueInventory != null) {
            queueInventory.close();
        }
        if (scoreboards != null) {
            scoreboards.disable();
        }
        if (core != null) {
            core.close();
        }
    }

    @Override
    public MatchmakingService matchmaking() {
        return matchmaking;
    }

    @Override
    public HubTransferService transfers() {
        return transfers;
    }

    @Override
    public ScoreboardService scoreboards() {
        return scoreboards;
    }

    @Override
    public PaperChatRenderer chat() {
        return chat;
    }

    @Override
    public AccountService accounts() {
        return core.accounts();
    }

    @Override
    public FriendService friends() {
        return core.friends();
    }

    @Override
    public PartyService parties() {
        return core.parties();
    }

    @Override
    public PresenceService presence() {
        return core.presence();
    }

    @Override
    public ShopService shop() {
        return core.shop();
    }

    @Override
    public InventoryService inventory() {
        return core.inventory();
    }

    @Override
    public QueueStateService queueStates() {
        return core.queueStates();
    }
}
