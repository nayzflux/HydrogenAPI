ALTER TABLE hydrogen_accounts ADD COLUMN last_name TEXT;

CREATE INDEX hydrogen_accounts_last_name_idx
    ON hydrogen_accounts (lower(last_name)) WHERE last_name IS NOT NULL;

ALTER TABLE hydrogen_parties
    ADD COLUMN members_can_invite BOOLEAN NOT NULL DEFAULT true;

DELETE FROM hydrogen_party_invitations;
ALTER TABLE hydrogen_party_invitations DROP CONSTRAINT hydrogen_party_invitations_pkey;
ALTER TABLE hydrogen_party_invitations ADD COLUMN inviter_id UUID NOT NULL;
ALTER TABLE hydrogen_party_invitations
    ADD PRIMARY KEY (inviter_id, invitee_id);
CREATE INDEX hydrogen_party_invitations_party_idx
    ON hydrogen_party_invitations (party_id);

CREATE TABLE hydrogen_party_invite_limits (
    inviter_id UUID NOT NULL,
    invitee_id UUID NOT NULL,
    last_sent_at TIMESTAMPTZ NOT NULL,
    step INTEGER NOT NULL CHECK (step BETWEEN 0 AND 4),
    PRIMARY KEY (inviter_id, invitee_id)
);
