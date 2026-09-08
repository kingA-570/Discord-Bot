package com.domistats.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A guild's own preferred-war-opponent profile, used for /setprofile compatibility matching. */
@Entity
@Table(name = "alliance_profile")
@Getter
@Setter
@NoArgsConstructor
public class AllianceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, unique = true)
    private String guildId;

    @Column(name = "alliance_name", nullable = false)
    private String allianceName;

    private String league;

    @Column(name = "player_count")
    private Integer playerCount;

    @Column(name = "target_weight")
    private Integer targetWeight;

    @Column(name = "min_glory")
    private Integer minGlory;

    @Column(name = "max_glory")
    private Integer maxGlory;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
