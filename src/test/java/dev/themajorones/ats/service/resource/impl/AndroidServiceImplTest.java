package dev.themajorones.ats.service.resource.impl;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitOperations;

import dev.themajorones.ats.repository.AndroidRepository;
import dev.themajorones.ats.repository.TaskLogRepository;
import dev.themajorones.ats.service.resource.DockerService;
import dev.themajorones.models.client.AdbClient;
import dev.themajorones.models.client.DockerClient;
import dev.themajorones.models.constants.AndroidStatus;
import dev.themajorones.models.constants.AndroidType;
import dev.themajorones.models.constants.RabbitMqConstant;
import dev.themajorones.models.constants.TaskLogConstant;
import dev.themajorones.models.dto.AdbCommandResult;
import dev.themajorones.models.dto.CreateAndroidRequest;
import dev.themajorones.models.dto.TaskCommandEnvelope;
import dev.themajorones.models.entity.Android;
import dev.themajorones.models.entity.TaskLog;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AndroidServiceImplTest {

    @Mock
    private AndroidRepository androidRepository;

    @Mock
    private TaskLogRepository taskLogRepository;

    @Mock
    private DockerService dockerService;

    @Mock
    private DockerClient dockerClient;

    @Mock
    private AdbClient adbClient;

    @Mock
    private RabbitOperations rabbitOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AndroidServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AndroidServiceImpl(
            androidRepository,
            taskLogRepository,
            dockerService,
            dockerClient,
            adbClient,
            rabbitOperations,
            objectMapper
        );
    }

    @Test
    void createRedroidQueuesRequestWithoutSavingAndroidRow() throws Exception {
        CreateAndroidRequest request = new CreateAndroidRequest()
            .setType("redroid")
            .setDockerId(7)
            .setName("pixel")
            .setImage("redroid/redroid:latest")
            .setAccelerationMode("host")
            .setWidth(1080)
            .setHeight(1920)
            .setDpi(420);

        AtomicInteger ids = new AtomicInteger(41);
        when(taskLogRepository.save(any(TaskLog.class))).thenAnswer(invocation -> {
            TaskLog taskLog = invocation.getArgument(0);
            if (taskLog.getId() == null) {
                taskLog.setId(ids.getAndIncrement());
            }
            return taskLog;
        });

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        Map<String, Object> response = service.createAndroid(request);

        verify(androidRepository, never()).save(any(Android.class));
        verify(rabbitOperations).convertAndSend(
            eq(RabbitMqConstant.DIRECT_EXCHANGE),
            eq(RabbitMqConstant.Queue.Android.ROUTING_KEY),
            messageCaptor.capture()
        );

        TaskCommandEnvelope envelope = objectMapper.readValue(messageCaptor.getValue(), TaskCommandEnvelope.class);
        CreateAndroidRequest queuedRequest = objectMapper.readValue(envelope.getContent(), CreateAndroidRequest.class);

        assertThat(response.get("status")).isEqualTo(TaskLogConstant.Status.QUEUED);
        assertThat(response.get("taskLogId")).isEqualTo(41);
        assertThat(envelope.getType()).isEqualTo(TaskLogConstant.Type.CREATE_ANDROID);
        assertThat(envelope.getTaskLogId()).isEqualTo(41);
        assertThat(queuedRequest.getType()).isEqualTo(AndroidType.REDROID.name());
        assertThat(queuedRequest.getDockerId()).isEqualTo(7);
        assertThat(queuedRequest.getImage()).isEqualTo("redroid/redroid:latest");
        assertThat(queuedRequest.getAccelerationMode()).isEqualTo("HOST");
        assertThat(queuedRequest.getWidth()).isEqualTo(1080);
        assertThat(queuedRequest.getHeight()).isEqualTo(1920);
        assertThat(queuedRequest.getDpi()).isEqualTo(420);
    }

    @Test
    void createDirectAndroidSavesOnlyAfterSuccessfulConnectAndInspect() {
        CreateAndroidRequest request = new CreateAndroidRequest()
            .setType(AndroidType.DIRECT.name())
            .setName("local")
            .setAdbHost("10.0.2.2")
            .setAdbPort(5555);

        when(adbClient.connect("10.0.2.2", 5555)).thenReturn(new AdbCommandResult(0, "connected to 10.0.2.2:5555\n", "", Duration.ofSeconds(1)));
        when(adbClient.shell("10.0.2.2:5555", "getprop", "ro.build.version.release")).thenReturn(new AdbCommandResult(0, "15\n", "", Duration.ofSeconds(1)));
        when(adbClient.shell("10.0.2.2:5555", "uname", "-r")).thenReturn(new AdbCommandResult(0, "6.1.0\n", "", Duration.ofSeconds(1)));
        when(adbClient.shell("10.0.2.2:5555", "wm", "size")).thenReturn(new AdbCommandResult(0, "Physical size: 1080x1920\n", "", Duration.ofSeconds(1)));
        when(adbClient.shell("10.0.2.2:5555", "wm", "density")).thenReturn(new AdbCommandResult(0, "Physical density: 420\n", "", Duration.ofSeconds(1)));
        when(androidRepository.save(any(Android.class))).thenAnswer(invocation -> {
            Android android = invocation.getArgument(0);
            android.setId(12);
            return android;
        });

        Map<String, Object> response = service.createAndroid(request);

        verify(androidRepository).save(any(Android.class));
        assertThat(response.get("status")).isEqualTo(AndroidStatus.CONNECTED.name());
        assertThat(response.get("androidId")).isEqualTo(12);
        assertThat(response.get("adbHost")).isEqualTo("10.0.2.2");
        assertThat(response.get("adbPort")).isEqualTo(5555);
    }
}
