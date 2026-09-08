package com.domistats.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "war_record")
@Getter
@Setter
@NoArgsConstructor
public class WarRecord {

    public enum Status { ONGOING, FINISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "domistats_war_id", nullable = false, unique = true)
    private String domistatsWarId;

    @Column(name = "alliance_a_id", nullable = false)
    private String allianceAId;

    @Column(name = "alliance_b_id", nullable = false)
    private String allianceBId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ONGOING;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "notified_found", nullable = false)
    private boolean notifiedFound;

    @Column(name = "notified_finished", nullable = false)
    private boolean notifiedFinished;
}
