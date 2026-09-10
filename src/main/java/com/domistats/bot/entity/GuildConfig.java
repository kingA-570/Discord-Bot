package com.domistats.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Per-Discord-server settings: notification channel + which notification types are enabled. */
@Entity
@Table(name = "guild_config")
@Getter
@Setter
@NoArgsConstructor
public class GuildConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, unique = true)
    private String guildId;

    @Column(name = "notification_channel_id")
    private String notificationChannelId;

    @Column(name = "dashboard_channel_id")
    private String dashboardChannelId;

    @Column(name = "dashboard_message_id")
    private String dashboardMessageId;

    @Column(name = "notify_new_spin", nullable = false)
    private boolean notifyNewSpin = true;

    @Column(name = "notify_war_found", nullable = false)
    private boolean notifyWarFound = true;

    @Column(name = "notify_watchlist_activity", nullable = false)
    private boolean notifyWatchlistActivity = true;

    @Column(name = "notify_war_finished", nullable = false)
    private boolean notifyWarFinished = true;

    @Column(name = "notify_glory_change", nullable = false)
    private boolean notifyGloryChange = false;

    @Column(name = "notify_member_change", nullable = false)
    private boolean notifyMemberChange = false;

    @Column(name = "auto_delete_seconds", nullable = false)
    private int autoDeleteSeconds = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
