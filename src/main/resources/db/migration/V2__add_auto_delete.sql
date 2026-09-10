-- Auto-delete for notification messages: 0 = keep forever (default),
-- otherwise delete notification messages N seconds after posting.
ALTER TABLE guild_config ADD COLUMN auto_delete_seconds INTEGER NOT NULL DEFAULT 0;