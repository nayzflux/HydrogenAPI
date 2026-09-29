CREATE TABLE IF NOT EXISTS hydrogen_accounts (
    player_id UUID PRIMARY KEY,
    rank_id TEXT NOT NULL DEFAULT 'PLAYER',
    global_level INTEGER NOT NULL DEFAULT 1 CHECK (global_level >= 1),
    coins BIGINT NOT NULL DEFAULT 0 CHECK (coins >= 0)
);

CREATE TABLE IF NOT EXISTS hydrogen_game_levels (
    player_id UUID NOT NULL REFERENCES hydrogen_accounts(player_id) ON DELETE CASCADE,
    game_id TEXT NOT NULL,
    level INTEGER NOT NULL CHECK (level >= 1),
    PRIMARY KEY (player_id, game_id)
);

CREATE TABLE IF NOT EXISTS hydrogen_wallet_operations (
    operation_id UUID PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES hydrogen_accounts(player_id),
    delta BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS hydrogen_friendships (
    player_low UUID NOT NULL,
    player_high UUID NOT NULL,
    requested_by UUID NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (player_low, player_high),
    CHECK (player_low < player_high)
);

CREATE INDEX IF NOT EXISTS hydrogen_friendships_high_idx ON hydrogen_friendships(player_high);

CREATE TABLE IF NOT EXISTS hydrogen_parties (
    party_id UUID PRIMARY KEY,
    leader_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS hydrogen_party_members (
    player_id UUID PRIMARY KEY,
    party_id UUID NOT NULL REFERENCES hydrogen_parties(party_id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS hydrogen_party_members_party_idx ON hydrogen_party_members(party_id);

CREATE TABLE IF NOT EXISTS hydrogen_party_invitations (
    party_id UUID NOT NULL REFERENCES hydrogen_parties(party_id) ON DELETE CASCADE,
    invitee_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (party_id, invitee_id)
);

CREATE TABLE IF NOT EXISTS hydrogen_entitlements (
    player_id UUID NOT NULL REFERENCES hydrogen_accounts(player_id),
    game_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (player_id, game_id, item_id)
);

CREATE TABLE IF NOT EXISTS hydrogen_inventory_operations (
    operation_id UUID PRIMARY KEY,
    player_id UUID NOT NULL,
    game_id TEXT NOT NULL,
    item_id TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    quantity_after INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS hydrogen_purchases (
    operation_id UUID PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES hydrogen_accounts(player_id),
    game_id TEXT NOT NULL,
    offer_id TEXT NOT NULL,
    price BIGINT NOT NULL CHECK (price >= 0),
    entitlement_id TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    balance_after BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS hydrogen_purchases_player_idx ON hydrogen_purchases(player_id, created_at DESC);
