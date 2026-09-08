# DomiStats War Radar Discord Bot

A Discord bot that tracks DomiNations alliances via DomiStats: live spin tracking,
automatic war detection, alliance/war lookups, watchlists, and per-server
notifications.

## ⚠️ Read this before deploying

This bot works by **scraping DomiStats' HTML pages** — the project spec explicitly
says not to assume a public API exists, and none was found. Before running this
against production:

1. **Check `https://domistats.com/robots.txt` and DomiStats' Terms of Service.**
   I could not fetch these from the build sandbox this project was created in, so
   they have not been verified. If scraping isn't permitted, reach out to the site
   owner — an API, data export, or partnership may be possible instead.
2. **Fix the CSS selectors.** `DomiStatsClient.java` is full of `TODO` comments and
   placeholder selectors (`.alliance-row`, `.glory`, `.win-rate`, etc.) inferred from
   the fields in the spec, not from the live DOM. Open the real pages in a browser's
   dev tools, find the actual classes/`data-*` attributes DomiStats uses, and update
   the `parse*` methods in `DomiStatsClient` accordingly.
3. **Set a real `DOMISTATS_USER_AGENT`** with contact info, and keep
   `DOMISTATS_MIN_INTERVAL_MS` conservative (2s+ default) so the bot never hammers
   the site. All requests funnel through `RateLimiter`, so this one setting throttles
   everything (slash commands + the background scanner) together.

## Architecture

```
Discord
  │
  ▼
JDA (SlashCommandListener) ──► CommandHandler beans (/spinning, /alliance, ...)
  │                                    │
  │                                    ▼
  │                          AllianceLookupService / WatchlistService / etc.
  │                                    │
  │                                    ▼
  │                            DomiStatsClient (Jsoup, cached, rate-limited)
  │                                    │
  ▼                                    ▼
SpinWarDetectionScheduler ◄──── PostgreSQL (alliance_state, war_record, ...)
  │
  ▼
NotificationDispatcher ──► Discord notification channels
```

- **`SpinWarDetectionScheduler`** runs on a fixed delay (`domistats.scan-interval-ms`,
  default 60s), diffs the live spinning list against `alliance_state` in Postgres,
  and is the single source of truth for "this is a *new* transition, notify once."
- **`DashboardScheduler`** edits one persistent embed per guild instead of spamming
  new messages (feature 11).
- **Commands are modular**: every `/slash` command is a Spring bean implementing
  `CommandHandler` and is auto-registered — add a new class in
  `discord/commands/` to add a new command, no wiring needed elsewhere.
- **Caching**: alliance detail pages and the spinning list are cached separately
  (Caffeine) with independent TTLs so repeated `/alliance` lookups or overlapping
  scans don't re-hit DomiStats unnecessarily.
- **Resilience**: `DomiStatsClient.fetchWithRetry` retries on 429/5xx/IO errors with
  exponential backoff (capped at 30s), and callers (scheduler, commands) treat a
  failed fetch as "DomiStats temporarily unavailable" rather than crashing.

## Setup

### 1. Prerequisites
- Java 21+, Maven 3.9+
- PostgreSQL 14+
- A Discord bot application (Discord Developer Portal) with the
  `applications.commands` and `bot` scopes, and the bot token

### 2. Database
```sql
CREATE DATABASE domistats_bot;
```
Flyway will run the migration in `src/main/resources/db/migration` automatically
on first startup.

### 3. Configuration
Copy `.env.example` to `.env` and fill in:
- `DISCORD_BOT_TOKEN` — from the Discord Developer Portal
- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
- `DOMISTATS_USER_AGENT` — identify your bot with contact info

Never commit secrets. `.env` is already in `.gitignore`; load it with your process
manager / Docker / `export $(cat .env | xargs)` at runtime — Spring reads these as
plain environment variables (see `application.yml`).

### 4. Build & run
```bash
mvn clean package
java -jar target/domistats-bot.jar
```

During development, set `DISCORD_DEV_GUILD_ID` to your test server's ID so slash
commands sync instantly instead of waiting up to an hour for global propagation.

### 5. Invite the bot
Generate an OAuth2 URL in the Discord Developer Portal with the `bot` and
`applications.commands` scopes, and at least `Send Messages`, `Embed Links`, and
`Use Slash Commands` permissions.

## What's implemented vs. stubbed

**Implemented:** live spin scanning + diffing, spin→war detection (once-only
notifications), war-found/comparison embeds, alliance/war/war-history/compare
lookups, per-guild watchlist with persistence, per-guild notification config,
editable War Radar dashboard, `/setprofile` spin-compatibility matching.

**Stubbed / needs follow-up work:**
- **CSS selectors** in `DomiStatsClient` (see warning above) — this is the one
  thing that *must* be done before the bot will return real data.
- **War-finished detection**: `war_record.status` and `notified_finished` exist in
  the schema but the scheduler doesn't yet poll ongoing wars for completion — add a
  method that re-checks `fetchCurrentWar` for alliances with `in_war = true` and
  flips `WarRecord.status` to `FINISHED` when DomiStats no longer shows an active war.
- **`/warhistory` pagination** currently takes a `page` option rather than Discord
  buttons; wiring buttons up is a `ButtonInteractionListener` that re-calls
  `AllianceLookupService.warHistory(id, page ± 1)`.
- **Automatic compatibility push notifications** (spec feature 9's "when the
  alliance is spinning, identify compatible alliances" as an unprompted alert,
  rather than only on `/setprofile`) — `CompatibilityService` is ready to be called
  from `SpinWarDetectionScheduler.handleObservedSpinning` for any guild with a saved
  `AllianceProfile`.
- Multi-server isolation is handled throughout (all watchlist/config/profile data
  is keyed by `guild_id`), but load-testing against many concurrently active guilds
  hasn't been done.

## Project layout

```
src/main/java/com/domistats/bot/
  config/       Spring + JDA + DomiStats settings, Caffeine cache config
  entity/       JPA entities (alliance_state, war_record, guild_config, ...)
  repository/   Spring Data repositories
  dto/          Plain data objects for scraped DomiStats content
  service/      Business logic: scraping client, scheduler, watchlist, etc.
  discord/      JDA wiring, embed building
  discord/commands/  One class per slash command
src/main/resources/
  application.yml
  db/migration/ Flyway SQL migrations
```
