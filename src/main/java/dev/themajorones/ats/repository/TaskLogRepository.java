package dev.themajorones.ats.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.themajorones.models.entity.TaskLog;

public interface TaskLogRepository extends JpaRepository<TaskLog, Integer> {

    List<TaskLog> findTop100ByOrderByIdDesc();
}
