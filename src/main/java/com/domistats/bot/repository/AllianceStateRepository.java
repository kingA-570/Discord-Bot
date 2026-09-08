package com.domistats.bot.repository;

import com.domistats.bot.entity.AllianceState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AllianceStateRepository extends JpaRepository<AllianceState, Long> {
    Optional<AllianceState> findByDomistatsId(String domistatsId);
    List<AllianceState> findBySpinningTrue();
    Optional<AllianceState> findFirstByNameIgnoreCase(String name);
}
