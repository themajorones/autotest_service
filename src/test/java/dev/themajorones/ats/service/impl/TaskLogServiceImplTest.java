package dev.themajorones.ats.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitOperations;

import dev.themajorones.ats.repository.AndroidTestStepHistoryRepository;
import dev.themajorones.ats.repository.TaskLogRepository;
import dev.themajorones.models.constants.RabbitMqConstant;
import dev.themajorones.models.constants.TaskLogConstant;
import dev.themajorones.ats.service.progress.TaskProgressBroadcaster;
import dev.themajorones.models.entity.TaskLog;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TaskLogServiceImplTest {

    @Mock
    private TaskLogRepository taskLogRepository;

    @Mock
    private AndroidTestStepHistoryRepository androidTestStepHistoryRepository;

    @Mock
    private TaskProgressBroadcaster taskProgressBroadcaster;

    @Mock
    private RabbitOperations rabbitOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TaskLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TaskLogServiceImpl(
            taskLogRepository,
            androidTestStepHistoryRepository,
            taskProgressBroadcaster,
            rabbitOperations,
            objectMapper
        );
    }

    @Test
    void retryTaskLogClearsAndroidTestHistoryBeforeRequeue() {
        TaskLog taskLog = new TaskLog()
            .setId(2044)
            .setType(TaskLogConstant.Type.ANDROID_AUTONOMOUS_TEST)
            .setStatus(TaskLogConstant.Status.FAILED)
            .setContent("{}")
            .setResult("{\"old\":true}");
        when(taskLogRepository.findById(2044)).thenReturn(Optional.of(taskLog));
        AtomicInteger saveCount = new AtomicInteger();
        when(taskLogRepository.save(any(TaskLog.class))).thenAnswer(invocation -> {
            TaskLog current = invocation.getArgument(0);
            if (current.getId() == null) {
                current.setId(2044);
            }
            saveCount.incrementAndGet();
            return current;
        });

        TaskLog retried = service.retryTaskLog(2044);

        verify(androidTestStepHistoryRepository).deleteAllByTaskLogId(2044);
        verify(rabbitOperations).convertAndSend(
            eq(RabbitMqConstant.DIRECT_EXCHANGE),
            eq(RabbitMqConstant.Queue.AndroidTest.ROUTING_KEY),
            any(String.class)
        );
        verify(taskProgressBroadcaster).broadcastTaskLog(eq(retried), anyString());
        assertThat(retried.getStatus()).isEqualTo(TaskLogConstant.Status.QUEUED);
        assertThat(retried.getStartedAt()).isNull();
        assertThat(retried.getEndedAt()).isNull();
        assertThat(retried.getResult()).isNull();
        assertThat(saveCount.get()).isEqualTo(2);
    }

    @Test
    void clearTaskLogsClearsAndroidTestHistoryToo() {
        service.clearTaskLogs();

        verify(androidTestStepHistoryRepository).deleteAllInBatch();
        verify(taskLogRepository).deleteAllInBatch();
        verify(taskProgressBroadcaster).broadcastTaskLogsCleared();
    }
}
