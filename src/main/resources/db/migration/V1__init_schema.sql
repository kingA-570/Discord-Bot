-- Snapshot of the last known state per alliance, used to diff against each scan
-- so we only fire notifications on real transitions (not spam every scan).
CREATE TABLE alliance_state (
    id                  BIGSERIAL PRIMARY KEY,
    domistats_id        VARCHAR(64) NOT NULL UNIQUE,
    name                VARCHAR(128) NOT NULL,
    glory               INTEGER,
    ranking             INTEGER,
    member_count        INTEGER,
    league              VARCHAR(64),
    win_rate            DOUBLE PRECISION,
    estimated_weight    INTEGER,
    is_spinning         BOOLEAN NOT NULL DEFAULT FALSE,
    spin_started_at     TIMESTAMPTZ,
    is_in_war           BOOLEAN NOT NULL DEFAULT FALSE,
    current_war_id      VARCHAR(64),
    profile_url         VARCHAR(256),
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_alliance_state_spinning ON alliance_state (is_spinning);
CREATE INDEX idx_alliance_state_name ON alliance_state (LOWER(name));

-- Wars we've already notified on, so "spin -> war" notifications only fire once.
CREATE TABLE war_record (
    id                  BIGSERIAL PRIMARY KEY,
    domistats_war_id    VARCHAR(64) NOT NULL UNIQUE,
    alliance_a_id       VARCHAR(64) NOT NULL,
    alliance_b_id       VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ONGOING', -- ONGOING, FINISHED
    detected_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at         TIMESTAMPTZ,
    notified_found      BOOLEAN NOT NULL DEFAULT FALSE,
    notified_finished   BOOLEAN NOT NULL DEFAULT FALSE
);

-- Per-guild (server) configuration.
CREATE TABLE guild_config (
    id                          BIGSERIAL PRIMARY KEY,
    guild_id                    VARCHAR(32) NOT NULL UNIQUE,
    notification_channel_id     VARCHAR(32),
    dashboard_channel_id        VARCHAR(32),
    dashboard_message_id        VARCHAR(32),
    notify_new_spin             BOOLEAN NOT NULL DEFAULT TRUE,
    notify_war_found            BOOLEAN NOT NULL DEFAULT TRUE,
    notify_watchlist_activity   BOOLEAN NOT NULL DEFAULT TRUE,
    notify_war_finished         BOOLEAN NOT NULL DEFAULT TRUE,
    notify_glory_change         BOOLEAN NOT NULL DEFAULT FALSE,
    notify_member_change        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Alliances watched by a given guild.
CREATE TABLE watchlist_entry (
    id                  BIGSERIAL PRIMARY KEY,
    guild_id            VARCHAR(32) NOT NULL,
    domistats_id        VARCHAR(64) NOT NULL,
    alliance_name       VARCHAR(128) NOT NULL,
    added_by_user_id    VARCHAR(32),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (guild_id, domistats_id)
);

CREATE INDEX idx_watchlist_guild ON watchlist_entry (guild_id);

-- Optional "spin compatibility" profile per guild/alliance (feature 9).
CREATE TABLE alliance_profile (
    id                  BIGSERIAL PRIMARY KEY,
    guild_id            VARCHAR(32) NOT NULL UNIQUE,
    alliance_name       VARCHAR(128) NOT NULL,
    league              VARCHAR(64),
    player_count        INTEGER,
    target_weight       INTEGER,
    min_glory           INTEGER,
    max_glory           INTEGER,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
