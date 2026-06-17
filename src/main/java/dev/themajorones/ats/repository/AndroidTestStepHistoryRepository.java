package dev.themajorones.ats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.themajorones.models.entity.AndroidTestStepHistory;

public interface AndroidTestStepHistoryRepository extends JpaRepository<AndroidTestStepHistory, Integer> {

    void deleteAllByTaskLogId(Integer taskLogId);

    List<AndroidTestStepHistory> findAllByTaskLogIdOrderByStepNumberAsc(Integer taskLogId);

    Optional<AndroidTestStepHistory> findByTaskLogIdAndStepNumber(Integer taskLogId, Integer stepNumber);
}
