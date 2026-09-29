package fr.nayz.hydrogen.api.shop;

public record Offer(String gameId, String offerId, long price, String entitlementId, int quantity, boolean repeatable) {
}
