package com.domistats.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "watchlist_entry")
@Getter
@Setter
@NoArgsConstructor
public class WatchlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "domistats_id", nullable = false)
    private String domistatsId;

    @Column(name = "alliance_name", nullable = false)
    private String allianceName;

    @Column(name = "added_by_user_id")
    private String addedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
