package dev.themajorones.ats.service.progress;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import dev.themajorones.models.constants.TaskProgressConstant;
import dev.themajorones.models.dto.TaskProgressEvent;
import dev.themajorones.models.entity.TaskLog;

class TaskProgressBroadcasterTest {

    @Test
    void broadcastSendsTaskLogAndAndroidTestTopics() {
        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        TaskProgressBroadcaster broadcaster = new TaskProgressBroadcaster(messagingTemplate);
        TaskLog taskLog = new TaskLog().setId(55).setType("ANDROID_AUTONOMOUS_TEST").setStatus("QUEUED");

        broadcaster.broadcast(new TaskProgressEvent()
            .setTaskLogId(55)
            .setTaskType("ANDROID_AUTONOMOUS_TEST")
            .setEventType(TaskProgressConstant.EventType.TASK_LOG_UPSERTED)
            .setTaskLog(taskLog));

        verify(messagingTemplate).convertAndSend(eq("/topic/task-logs"), eq(new TaskProgressEvent()
            .setTaskLogId(55)
            .setTaskType("ANDROID_AUTONOMOUS_TEST")
            .setEventType(TaskProgressConstant.EventType.TASK_LOG_UPSERTED)
            .setTaskLog(taskLog)));
        verify(messagingTemplate).convertAndSend(eq("/topic/android-tests/55"), eq(new TaskProgressEvent()
            .setTaskLogId(55)
            .setTaskType("ANDROID_AUTONOMOUS_TEST")
            .setEventType(TaskProgressConstant.EventType.TASK_LOG_UPSERTED)
            .setTaskLog(taskLog)));
    }

    @Test
    void broadcastClearOnlyTargetsLogsTopic() {
        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        TaskProgressBroadcaster broadcaster = new TaskProgressBroadcaster(messagingTemplate);

        broadcaster.broadcastTaskLogsCleared();

        verify(messagingTemplate).convertAndSend(eq("/topic/task-logs"), Mockito.any(TaskProgressEvent.class));
    }
}
