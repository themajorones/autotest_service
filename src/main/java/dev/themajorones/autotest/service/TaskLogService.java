package dev.themajorones.autotest.service;

import java.util.List;

import dev.themajorones.models.entity.TaskLog;

public interface TaskLogService {

    List<TaskLog> listTaskLogs();
}
