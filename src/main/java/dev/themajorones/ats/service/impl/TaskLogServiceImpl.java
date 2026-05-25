package dev.themajorones.ats.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.themajorones.ats.repository.TaskLogRepository;
import dev.themajorones.ats.service.TaskLogService;
import dev.themajorones.models.entity.TaskLog;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskLogServiceImpl implements TaskLogService {

    private final TaskLogRepository taskLogRepository;

    @Transactional(readOnly = true)
    @Override
    public List<TaskLog> listTaskLogs() {
        return taskLogRepository.findTop100ByOrderByIdDesc();
    }
}
