CREATE TABLE hydrogen_queue_ticket (
    ticket_id UUID PRIMARY KEY,
    queue_group_id TEXT NOT NULL,
    party_id TEXT NOT NULL,
    endercloud_entry_id TEXT,
    status TEXT NOT NULL,
    entered_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    confirmation_due_at TIMESTAMPTZ NOT NULL,
    lease_token UUID,
    lease_until TIMESTAMPTZ
);

CREATE TABLE hydrogen_queue_member (
    player_id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES hydrogen_queue_ticket(ticket_id) ON DELETE CASCADE
);

CREATE INDEX hydrogen_queue_member_ticket_idx ON hydrogen_queue_member(ticket_id);
CREATE INDEX hydrogen_queue_ticket_confirmation_idx ON hydrogen_queue_ticket(confirmation_due_at);

CREATE TABLE hydrogen_queue_found_notice (
    ticket_id UUID NOT NULL REFERENCES hydrogen_queue_ticket(ticket_id) ON DELETE CASCADE,
    player_id UUID NOT NULL,
    PRIMARY KEY (ticket_id, player_id)
);
