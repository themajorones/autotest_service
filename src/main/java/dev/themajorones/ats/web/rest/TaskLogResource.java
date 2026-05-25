package dev.themajorones.ats.web.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.themajorones.ats.service.TaskLogService;
import dev.themajorones.models.entity.TaskLog;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TaskLogResource {

    private final TaskLogService service;

    @GetMapping("/api/task-logs")
    public List<TaskLog> listTaskLogs() {
        return service.listTaskLogs();
    }
}
