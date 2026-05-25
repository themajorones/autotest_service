package dev.themajorones.ats.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.themajorones.models.entity.TaskLog;

@Repository
public interface TaskLogRepository extends JpaRepository<TaskLog, Integer> {

    List<TaskLog> findTop100ByOrderByIdDesc();
}
