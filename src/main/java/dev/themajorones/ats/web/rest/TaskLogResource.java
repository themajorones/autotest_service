package dev.themajorones.ats.web.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/api/task-logs/{id}/retry")
    public TaskLog retryTaskLog(@PathVariable Integer id) {
        return service.retryTaskLog(id);
    }

    @DeleteMapping("/api/task-logs")
    public ResponseEntity<Void> clearTaskLogs() {
        service.clearTaskLogs();
        return ResponseEntity.noContent().build();
    }
}
