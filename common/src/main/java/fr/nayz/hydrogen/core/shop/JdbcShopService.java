package fr.nayz.hydrogen.core.shop;

import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.api.shop.Offer;
import fr.nayz.hydrogen.api.shop.Purchase;
import fr.nayz.hydrogen.api.shop.ShopService;
import fr.nayz.hydrogen.core.database.AccountRowOperations;
import fr.nayz.hydrogen.core.database.Database;
import fr.nayz.hydrogen.core.database.EntitlementRowOperations;
import fr.nayz.hydrogen.core.database.Sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

public final class JdbcShopService implements ShopService {
    private final Database database;
    private final Map<OfferKey, Offer> offers = new ConcurrentHashMap<>();

    public JdbcShopService(Database database) {
        this.database = database;
    }

    @Override
    public void register(Offer offer) {
        Sql.require(offer != null, HydrogenErrorReason.INVALID_INPUT, "Offer is required");
        Sql.require(offer.gameId() != null && !offer.gameId().isBlank(),
                HydrogenErrorReason.INVALID_INPUT, "gameId is required");
        Sql.require(offer.offerId() != null && !offer.offerId().isBlank(),
                HydrogenErrorReason.INVALID_INPUT, "offerId is required");
        Sql.require(offer.entitlementId() != null && !offer.entitlementId().isBlank(),
                HydrogenErrorReason.INVALID_INPUT, "entitlementId is required");
        Sql.require(offer.price() >= 0 && offer.quantity() > 0,
                HydrogenErrorReason.INVALID_INPUT, "Invalid price or quantity");
        OfferKey key = new OfferKey(offer.gameId(), offer.offerId());
        Offer existing = offers.putIfAbsent(key, offer);
        Sql.require(existing == null || existing.equals(offer),
                HydrogenErrorReason.CONFLICT, "Offer has another definition");
    }

    @Override
    public CompletableFuture<Purchase> purchase(UUID playerId, String gameId, String offerId, UUID operationId) {
        return database.transaction(connection -> {
            Sql.ensureAccount(connection, playerId);
            AccountRowOperations.lockAccount(connection, playerId);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "SELECT player_id, game_id, offer_id, balance_after FROM hydrogen_purchases "
                            + "WHERE operation_id = ?", operationId);
                 ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    Sql.require(playerId.equals(Sql.uuid(rows, "player_id"))
                                    && gameId.equals(rows.getString("game_id"))
                                    && offerId.equals(rows.getString("offer_id")),
                            HydrogenErrorReason.CONFLICT, "Operation ID was used for another purchase");
                    return new Purchase(operationId, playerId, gameId, offerId, rows.getLong("balance_after"));
                }
            }
            Offer offer = offers.get(new OfferKey(gameId, offerId));
            Sql.require(offer != null, HydrogenErrorReason.NOT_FOUND, "Offer not registered here");
            if (!offer.repeatable()) {
                try (PreparedStatement statement = Sql.prepare(connection,
                        "SELECT 1 FROM hydrogen_entitlements "
                                + "WHERE player_id = ? AND game_id = ? AND item_id = ?",
                        playerId, gameId, offer.entitlementId());
                     ResultSet rows = statement.executeQuery()) {
                    Sql.require(!rows.next(), HydrogenErrorReason.CONFLICT, "Already owned");
                }
            }
            long balance = AccountRowOperations.balance(connection, playerId);
            Sql.require(balance >= offer.price(), HydrogenErrorReason.INSUFFICIENT_COINS, "Insufficient coins");
            long updated = balance - offer.price();
            try (PreparedStatement statement = Sql.prepare(connection,
                    "UPDATE hydrogen_accounts SET coins = ? WHERE player_id = ?", updated, playerId)) {
                statement.executeUpdate();
            }
            EntitlementRowOperations.add(connection, playerId, gameId, offer.entitlementId(), offer.quantity());
            try (PreparedStatement statement = Sql.prepare(connection,
                    "INSERT INTO hydrogen_purchases "
                            + "(operation_id, player_id, game_id, offer_id, price, entitlement_id, quantity, balance_after) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    operationId, playerId, gameId, offerId, offer.price(), offer.entitlementId(),
                    offer.quantity(), updated)) {
                statement.executeUpdate();
            }
            return new Purchase(operationId, playerId, gameId, offerId, updated);
        });
    }

}
