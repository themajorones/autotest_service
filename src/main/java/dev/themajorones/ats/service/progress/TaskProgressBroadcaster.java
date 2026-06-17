package dev.themajorones.ats.service.progress;

import java.time.Instant;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import dev.themajorones.models.constants.TaskLogConstant;
import dev.themajorones.models.constants.TaskProgressConstant;
import dev.themajorones.models.dto.TaskProgressEvent;
import dev.themajorones.models.entity.AndroidTestStepHistory;
import dev.themajorones.models.entity.TaskLog;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskProgressBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastTaskLog(TaskLog taskLog, String eventType) {
        broadcast(new TaskProgressEvent()
            .setTaskLogId(taskLog == null ? null : taskLog.getId())
            .setTaskType(taskLog == null ? null : taskLog.getType())
            .setEventType(eventType)
            .setEmittedAt(Instant.now().toEpochMilli())
            .setTaskLog(taskLog));
    }

    public void broadcastAndroidTestStep(TaskLog taskLog, AndroidTestStepHistory step, String eventType) {
        broadcast(new TaskProgressEvent()
            .setTaskLogId(taskLog == null ? null : taskLog.getId())
            .setTaskType(taskLog == null ? null : taskLog.getType())
            .setEventType(eventType)
            .setEmittedAt(Instant.now().toEpochMilli())
            .setTaskLog(taskLog)
            .setStep(step));
    }

    public void broadcastTaskLogsCleared() {
        messagingTemplate.convertAndSend("/topic/task-logs", new TaskProgressEvent()
            .setEventType(TaskProgressConstant.EventType.TASK_LOGS_CLEARED)
            .setEmittedAt(Instant.now().toEpochMilli()));
    }

    public void broadcast(TaskProgressEvent event) {
        if (event == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/task-logs", event);
        if (event.getTaskLogId() != null && TaskLogConstant.Type.ANDROID_AUTONOMOUS_TEST.equals(event.getTaskType())) {
            messagingTemplate.convertAndSend("/topic/android-tests/" + event.getTaskLogId(), event);
        }
    }
}
