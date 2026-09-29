package fr.nayz.hydrogen.core.inventory;

import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.api.inventory.Entitlement;
import fr.nayz.hydrogen.api.inventory.InventoryService;
import fr.nayz.hydrogen.core.database.AccountRowOperations;
import fr.nayz.hydrogen.core.database.Database;
import fr.nayz.hydrogen.core.database.EntitlementRowOperations;
import fr.nayz.hydrogen.core.database.Sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.UUID;

public final class JdbcInventoryService implements InventoryService {
    private final Database database;

    public JdbcInventoryService(Database database) {
        this.database = database;
    }

    @Override
    public CompletableFuture<List<Entitlement>> list(UUID playerId, String gameId) {
        return database.read(connection -> {
            List<Entitlement> result = new ArrayList<>();
            try (PreparedStatement statement = Sql.prepare(connection,
                    "SELECT item_id, quantity FROM hydrogen_entitlements WHERE player_id = ? AND game_id = ?",
                    playerId, gameId);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new Entitlement(gameId, rows.getString("item_id"), rows.getInt("quantity")));
                }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public CompletableFuture<Entitlement> grant(
            UUID playerId,
            String gameId,
            String itemId,
            int quantity,
            UUID operationId
    ) {
        Sql.require(quantity > 0, HydrogenErrorReason.INVALID_INPUT, "Quantity must be positive");
        Sql.require(gameId != null && !gameId.isBlank(), HydrogenErrorReason.INVALID_INPUT, "gameId is required");
        Sql.require(itemId != null && !itemId.isBlank(), HydrogenErrorReason.INVALID_INPUT, "itemId is required");
        return database.transaction(connection -> {
            Sql.ensureAccount(connection, playerId);
            AccountRowOperations.lockAccount(connection, playerId);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "SELECT player_id, game_id, item_id, quantity, quantity_after "
                            + "FROM hydrogen_inventory_operations WHERE operation_id = ?", operationId);
                 ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    Sql.require(playerId.equals(Sql.uuid(rows, "player_id"))
                                    && gameId.equals(rows.getString("game_id"))
                                    && itemId.equals(rows.getString("item_id"))
                                    && quantity == rows.getInt("quantity"),
                            HydrogenErrorReason.CONFLICT, "Operation ID was used for another grant");
                    return new Entitlement(gameId, itemId, rows.getInt("quantity_after"));
                }
            }
            int updated = EntitlementRowOperations.add(connection, playerId, gameId, itemId, quantity);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "INSERT INTO hydrogen_inventory_operations "
                            + "(operation_id, player_id, game_id, item_id, quantity, quantity_after) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    operationId, playerId, gameId, itemId, quantity, updated)) {
                statement.executeUpdate();
            }
            return new Entitlement(gameId, itemId, updated);
        });
    }

}
