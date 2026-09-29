package fr.nayz.hydrogen.api;

import fr.nayz.hydrogen.api.account.AccountService;
import fr.nayz.hydrogen.api.friend.FriendService;
import fr.nayz.hydrogen.api.inventory.InventoryService;
import fr.nayz.hydrogen.api.matchmaking.QueueStateService;
import fr.nayz.hydrogen.api.party.PartyService;
import fr.nayz.hydrogen.api.presence.Presence;
import fr.nayz.hydrogen.api.presence.PresenceService;
import fr.nayz.hydrogen.api.shop.ShopService;

public interface HydrogenApi {
    AccountService accounts();

    FriendService friends();

    PartyService parties();

    PresenceService presence();

    ShopService shop();

    InventoryService inventory();

    QueueStateService queueStates();
}
