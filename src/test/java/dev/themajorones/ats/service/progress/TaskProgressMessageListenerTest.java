package dev.themajorones.ats.service.progress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.Channel;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import dev.themajorones.models.constants.TaskProgressConstant;
import dev.themajorones.models.dto.TaskProgressEvent;
import dev.themajorones.models.entity.TaskLog;
import tools.jackson.databind.ObjectMapper;

class TaskProgressMessageListenerTest {

    @Test
    void listenBroadcastsDecodedEventAndAcknowledgesMessage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TaskProgressBroadcaster broadcaster = Mockito.mock(TaskProgressBroadcaster.class);
        TaskProgressMessageListener listener = new TaskProgressMessageListener(objectMapper, broadcaster);
        Channel channel = Mockito.mock(Channel.class);
        TaskProgressEvent event = new TaskProgressEvent()
            .setTaskLogId(77)
            .setTaskType("ANDROID_AUTONOMOUS_TEST")
            .setEventType(TaskProgressConstant.EventType.TASK_LOG_UPSERTED)
            .setTaskLog(new TaskLog().setId(77).setType("ANDROID_AUTONOMOUS_TEST").setStatus("RUNNING"));

        listener.listen(objectMapper.writeValueAsString(event), channel, 42L);

        verify(broadcaster).broadcast(any(TaskProgressEvent.class));
        verify(channel).basicAck(eq(42L), eq(false));
    }
}
