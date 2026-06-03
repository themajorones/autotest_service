package dev.themajorones.ats.service;

import java.util.List;

import dev.themajorones.models.entity.TaskLog;

public interface TaskLogService {

    List<TaskLog> listTaskLogs();

    TaskLog retryTaskLog(Integer id);

    void clearTaskLogs();
}
