package com.domistats.bot.repository;

import com.domistats.bot.entity.WarRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarRecordRepository extends JpaRepository<WarRecord, Long> {
    Optional<WarRecord> findByDomistatsWarId(String domistatsWarId);
}
