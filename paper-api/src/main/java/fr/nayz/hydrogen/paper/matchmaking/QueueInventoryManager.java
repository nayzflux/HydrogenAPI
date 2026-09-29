package fr.nayz.hydrogen.paper.matchmaking;

import fr.nayz.hydrogen.api.matchmaking.QueueState;
import fr.nayz.hydrogen.api.matchmaking.QueueStatus;
import fr.nayz.endercloud.core.model.SessionAssignment;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.paper.event.matchmaking.QueueStateChangedEvent;
import fr.nayz.hydrogen.paper.event.matchmaking.QueueStateReadyEvent;
import fr.nayz.hydrogen.paper.message.PaperMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QueueInventoryManager implements AutoCloseable {
    private static final int BARRIER_SLOT = 2;
    private static final int PAPER_SLOT = 4;
    private final JavaPlugin plugin;
    private final CoreHydrogenApi core;
    private final EnderCloudMatchmakingService matchmaking;
    private final NamespacedKey marker;
    private final Map<UUID, Optional<QueueState>> known = new HashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Set<UUID> resolvingAssignment = ConcurrentHashMap.newKeySet();
    private final Set<UUID> reportedAssignmentErrors = ConcurrentHashMap.newKeySet();
    private BukkitTask refreshTask;
    private BukkitTask confirmationTask;
    private BukkitTask assignmentTask;

    public QueueInventoryManager(
            JavaPlugin plugin,
            CoreHydrogenApi core,
            EnderCloudMatchmakingService matchmaking
    ) {
        this.plugin = plugin;
        this.core = core;
        this.matchmaking = matchmaking;
        this.marker = new NamespacedKey(plugin, "queue_control");
    }

    public void start() {
        core.events().onQueueChange(this::refresh);
        checkAssignment();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 300, 300);
        confirmationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::confirmDue, 1200, 1200);
        assignmentTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAssignment, 100, 100);
    }

    public void joined(Player player) {
        UUID playerId = player.getUniqueId();
        known.remove(playerId);
        Optional<SessionAssignment> assigned = matchmaking.currentCloudAssignment()
                .filter(assignment -> assignment.players().stream()
                        .anyMatch(member -> playerId.toString().equals(member.playerId())));
        if (assigned.isPresent()) {
            resolvingAssignment.add(playerId);
            resolveAssignment(playerId, assigned.get());
            return;
        }
        refresh(playerId);
        matchmaking.confirmForArrival(playerId).exceptionally(error -> {
            plugin.getLogger().warning("Queue confirmation failed for " + playerId + ": " + error.getMessage());
            return null;
        });
    }

    public void quit(UUID playerId) {
        known.remove(playerId);
        loading.remove(playerId);
        resolvingAssignment.remove(playerId);
        reportedAssignmentErrors.remove(playerId);
    }

    private void resolveAssignment(UUID playerId, SessionAssignment assignment) {
        if (!resolvingAssignment.contains(playerId)) {
            return;
        }
        matchmaking.confirmAssignedPlayer(assignment, playerId).whenComplete((ignored, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || !resolvingAssignment.contains(playerId)) {
                    return;
                }
                if (error != null) {
                    if (reportedAssignmentErrors.add(playerId)) {
                        plugin.getLogger().warning("Assignment confirmation failed for "
                                + playerId + ": " + error.getMessage());
                    }
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> resolveAssignment(playerId, assignment), 100L);
                    return;
                }
                reportedAssignmentErrors.remove(playerId);
                resolvingAssignment.remove(playerId);
                refresh(playerId);
            });
        });
    }

    public boolean isLocked(UUID playerId) {
        Optional<QueueState> state = known.get(playerId);
        return state != null && state.isPresent() && state.get().status().hasQueueInventory();
    }

    public boolean isControl(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(marker, PersistentDataType.STRING);
    }

    public void use(Player player, ItemStack item) {
        if (!isControl(item)) {
            return;
        }
        Optional<QueueState> found = known.getOrDefault(player.getUniqueId(), Optional.empty());
        if (found.isEmpty()) {
            return;
        }
        QueueState state = found.get();
        if (item.getType() == Material.BARRIER) {
            matchmaking.leaveQueue(player.getUniqueId()).whenComplete((accepted, error) -> {
                if (!plugin.isEnabled()) {
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (error != null) {
                            PaperMessages.error(player,
                                    "Impossible de vérifier la sortie de file pour le moment.");
                        } else if (!accepted) {
                            PaperMessages.error(player,
                                    "Impossible de quitter la file à ce stade.");
                        } else {
                            PaperMessages.success(player, "File quittée.");
                            refresh(player.getUniqueId());
                        }
                    });
            });
            return;
        }
        if (item.getType() == Material.PAPER) {
            player.sendMessage(Component.text("File : ", NamedTextColor.GRAY)
                    .append(Component.text(state.queue().displayName(), NamedTextColor.YELLOW))
                    .append(Component.text(" • attente : " + formatElapsed(state), NamedTextColor.GRAY)));
            if (state.status() == QueueStatus.VERIFYING || state.status() == QueueStatus.JOINING) {
                PaperMessages.important(player,
                        "Vérification de la file en cours.");
            }
        }
    }

    public void refresh(UUID playerId) {
        if (resolvingAssignment.contains(playerId)) {
            return;
        }
        if (!loading.add(playerId)) {
            return;
        }
        core.queueStates().get(playerId).whenComplete((state, error) -> {
            if (!plugin.isEnabled()) {
                loading.remove(playerId);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                    loading.remove(playerId);
                    if (error != null) {
                        plugin.getLogger().warning("Queue state read failed for " + playerId + ": " + error.getMessage());
                        return;
                    }
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()
                            && !resolvingAssignment.contains(playerId)) {
                        apply(player, state);
                    }
                });
        });
    }

    private void refreshAll() {
        List<UUID> ids = Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        core.queueStates().getMany(ids).whenComplete((states, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().warning("Queue state refresh failed: " + error.getMessage());
                        return;
                    }
                    for (UUID playerId : ids) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null && player.isOnline()
                                && !resolvingAssignment.contains(playerId)) {
                            apply(player, Optional.ofNullable(states.get(playerId)));
                        }
                    }
                });
        });
    }

    private void apply(Player player, Optional<QueueState> current) {
        UUID playerId = player.getUniqueId();
        Optional<QueueState> previous = known.put(playerId, current);
        boolean initial = previous == null;
        boolean wasActive = previous != null && previous.isPresent()
                && previous.get().status().hasQueueInventory();
        boolean active = current.isPresent() && current.get().status().hasQueueInventory();
        if (active) {
            if (!wasActive) {
                player.getInventory().clear();
            }
            placeControls(player, current.get());
        } else if (wasActive) {
            removeControls(player);
        }
        if (initial) {
            Bukkit.getPluginManager().callEvent(new QueueStateReadyEvent(player, current));
        } else if (!previous.equals(current)) {
            Bukkit.getPluginManager().callEvent(new QueueStateChangedEvent(player, previous, current));
        }
    }

    private void placeControls(Player player, QueueState state) {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = barrier.getItemMeta();
        barrierMeta.displayName(Component.text("Quitter la file", NamedTextColor.RED));
        barrierMeta.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "leave");
        barrier.setItemMeta(barrierMeta);
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta paperMeta = paper.getItemMeta();
        paperMeta.displayName(Component.text("File : " + state.queue().displayName(), NamedTextColor.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Attente : " + formatElapsed(state), NamedTextColor.GRAY));
        if (state.status() == QueueStatus.VERIFYING || state.status() == QueueStatus.JOINING) {
            lore.add(Component.text("Vérification en cours", NamedTextColor.YELLOW));
        }
        paperMeta.lore(lore);
        paperMeta.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "details");
        paper.setItemMeta(paperMeta);
        player.getInventory().setItem(BARRIER_SLOT, barrier);
        player.getInventory().setItem(PAPER_SLOT, paper);
    }

    private void removeControls(Player player) {
        for (int slot : List.of(BARRIER_SLOT, PAPER_SLOT)) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isControl(item)) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private void confirmDue() {
        Collection<UUID> players = Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList();
        if (players.isEmpty()) {
            return;
        }
        matchmaking.confirmDue(players).exceptionally(error -> {
            plugin.getLogger().warning("Periodic queue confirmation failed: " + error.getMessage());
            return null;
        });
    }

    private void checkAssignment() {
        matchmaking.currentCloudAssignment().ifPresent(assignment ->
                matchmaking.confirmAssignment(assignment).exceptionally(error -> {
                    plugin.getLogger().warning("Assignment confirmation failed: " + error.getMessage());
                    return null;
                }));
    }

    public static String formatElapsed(QueueState state) {
        Duration elapsed = state.elapsedAt(Instant.now());
        long minutes = elapsed.toMinutes();
        long seconds = elapsed.toSecondsPart();
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public void close() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        if (confirmationTask != null) {
            confirmationTask.cancel();
        }
        if (assignmentTask != null) {
            assignmentTask.cancel();
        }
        known.clear();
        loading.clear();
        resolvingAssignment.clear();
        reportedAssignmentErrors.clear();
    }
}
