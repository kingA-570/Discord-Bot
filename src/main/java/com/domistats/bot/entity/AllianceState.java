package com.domistats.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Last known snapshot of an alliance's stats, used to detect state transitions
 * (idle -> spinning -> war -> finished) between scans without re-hitting DomiStats
 * for every comparison.
 */
@Entity
@Table(name = "alliance_state")
@Getter
@Setter
@NoArgsConstructor
public class AllianceState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "domistats_id", nullable = false, unique = true)
    private String domistatsId;

    @Column(nullable = false)
    private String name;

    private Integer glory;
    private Integer ranking;

    @Column(name = "member_count")
    private Integer memberCount;

    private String league;

    @Column(name = "win_rate")
    private Double winRate;

    @Column(name = "estimated_weight")
    private Integer estimatedWeight;

    @Column(name = "is_spinning", nullable = false)
    private boolean spinning;

    @Column(name = "spin_started_at")
    private Instant spinStartedAt;

    @Column(name = "is_in_war", nullable = false)
    private boolean inWar;

    @Column(name = "current_war_id")
    private String currentWarId;

    @Column(name = "profile_url")
    private String profileUrl;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
