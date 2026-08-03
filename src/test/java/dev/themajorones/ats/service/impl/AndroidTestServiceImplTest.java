package dev.themajorones.ats.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitOperations;

import dev.themajorones.ats.repository.AndroidRepository;
import dev.themajorones.ats.repository.AndroidTestStepHistoryRepository;
import dev.themajorones.ats.repository.ArtifactRepository;
import dev.themajorones.ats.repository.OllamaRepository;
import dev.themajorones.ats.repository.TaskLogRepository;
import dev.themajorones.ats.service.progress.TaskProgressBroadcaster;
import dev.themajorones.ats.service.storage.ArtifactStorageObject;
import dev.themajorones.ats.service.storage.ImageStorageClient;
import dev.themajorones.models.constants.RabbitMqConstant;
import dev.themajorones.models.constants.TaskLogConstant;
import dev.themajorones.models.dto.RunAndroidTestRequest;
import dev.themajorones.models.dto.TaskCommandEnvelope;
import dev.themajorones.models.entity.Android;
import dev.themajorones.models.entity.AndroidTestStepHistory;
import dev.themajorones.models.entity.Artifact;
import dev.themajorones.models.entity.ArtifactSource;
import dev.themajorones.models.entity.Ollama;
import dev.themajorones.models.entity.TaskLog;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AndroidTestServiceImplTest {

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private AndroidRepository androidRepository;

    @Mock
    private OllamaRepository ollamaRepository;

    @Mock
    private TaskLogRepository taskLogRepository;

    @Mock
    private AndroidTestStepHistoryRepository androidTestStepHistoryRepository;

    @Mock
    private TaskProgressBroadcaster taskProgressBroadcaster;

    @Mock
    private RabbitOperations rabbitOperations;

    @Mock
    private ImageStorageClient imageStorageClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AndroidTestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AndroidTestServiceImpl(
            artifactRepository,
            androidRepository,
            ollamaRepository,
            taskLogRepository,
            androidTestStepHistoryRepository,
            taskProgressBroadcaster,
            rabbitOperations,
            imageStorageClient,
            objectMapper
        );
    }

    @Test
    void runAndroidTestQueuesAutonomousTestTask() throws Exception {
        Artifact artifact = new Artifact()
            .setId(11)
            .setSource(ArtifactSource.UPLOAD)
            .setName("app")
            .setStorageKey("artifacts/11.apk");
        Android android = new Android()
            .setId(22)
            .setName("device")
            .setAdbHost("127.0.0.1")
            .setAdbPort(5555);
        Ollama ollama = new Ollama()
            .setId(33)
            .setName("local")
            .setBaseUrl("http://localhost:11434")
            .setModel("qwen")
            .setEnabled(true);

        when(artifactRepository.findById(11)).thenReturn(Optional.of(artifact));
        when(androidRepository.findById(22)).thenReturn(Optional.of(android));
        when(ollamaRepository.findById(33)).thenReturn(Optional.of(ollama));
        AtomicInteger ids = new AtomicInteger(50);
        when(taskLogRepository.save(any(TaskLog.class))).thenAnswer(invocation -> {
            TaskLog taskLog = invocation.getArgument(0);
            if (taskLog.getId() == null) {
                taskLog.setId(ids.getAndIncrement());
            }
            return taskLog;
        });

        TaskLog taskLog = service.runAndroidTest(new RunAndroidTestRequest()
            .setArtifactId(11)
            .setAndroidId(22)
            .setOllamaId(33)
            .setObjective("verify login")
            .setMaxSteps(7));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitOperations).convertAndSend(
            eq(RabbitMqConstant.DIRECT_EXCHANGE),
            eq(RabbitMqConstant.Queue.AndroidTest.ROUTING_KEY),
            messageCaptor.capture()
        );
        TaskCommandEnvelope envelope = objectMapper.readValue(messageCaptor.getValue(), TaskCommandEnvelope.class);
        RunAndroidTestRequest queued = objectMapper.readValue(envelope.getContent(), RunAndroidTestRequest.class);

        assertThat(taskLog.getStatus()).isEqualTo(TaskLogConstant.Status.QUEUED);
        assertThat(envelope.getType()).isEqualTo(TaskLogConstant.Type.ANDROID_AUTONOMOUS_TEST);
        assertThat(queued.getArtifactId()).isEqualTo(11);
        assertThat(queued.getAndroidId()).isEqualTo(22);
        assertThat(queued.getOllamaId()).isEqualTo(33);
        assertThat(queued.getObjective()).isEqualTo("verify login");
        assertThat(queued.getMaxSteps()).isEqualTo(7);
        verify(taskProgressBroadcaster).broadcastTaskLog(any(TaskLog.class), anyString());
    }

    @Test
    void listAndroidTestStepsReturnsOrderedHistory() {
        TaskLog taskLog = new TaskLog()
            .setId(77)
            .setType(TaskLogConstant.Type.ANDROID_AUTONOMOUS_TEST)
            .setStatus(TaskLogConstant.Status.SUCCESS)
            .setContent("{}");
        when(taskLogRepository.findById(77)).thenReturn(Optional.of(taskLog));
        when(androidTestStepHistoryRepository.findAllByTaskLogIdOrderByStepNumberAsc(77)).thenReturn(List.of(
            new AndroidTestStepHistory()
                .setId(1)
                .setTaskLogId(77)
                .setStepNumber(1)
                .setStartedAt(10L)
                .setEndedAt(20L)
                .setAction("CLICK")
                .setTargetElementId(2)
                .setTargetX(548)
                .setTargetY(548)
                .setImageStorageKey("android-tests/77/steps/1.png"),
            new AndroidTestStepHistory()
                .setId(2)
                .setTaskLogId(77)
                .setStepNumber(2)
                .setStartedAt(21L)
                .setEndedAt(30L)
                .setAction("FINISH")
                .setState("SUCCESS")
                .setImageStorageKey("android-tests/77/steps/2.png")
        ));

        var steps = service.listAndroidTestSteps(77);

        assertThat(steps).extracting("stepNumber").containsExactly(1, 2);
        assertThat(steps.get(0).targetElementId()).isEqualTo(2);
        assertThat(steps.get(1).imageStorageKey()).isEqualTo("android-tests/77/steps/2.png");
    }

    @Test
    void getStepImageReadsImageBucketObject() throws Exception {
        TaskLog taskLog = new TaskLog()
            .setId(77)
            .setType(TaskLogConstant.Type.ANDROID_AUTONOMOUS_TEST)
            .setStatus(TaskLogConstant.Status.SUCCESS)
            .setContent("{}");
        AndroidTestStepHistory history = new AndroidTestStepHistory()
            .setTaskLogId(77)
            .setStepNumber(3)
            .setStartedAt(1L)
            .setImageStorageKey("android-tests/77/steps/3.png");
        when(taskLogRepository.findById(77)).thenReturn(Optional.of(taskLog));
        when(androidTestStepHistoryRepository.findByTaskLogIdAndStepNumber(77, 3)).thenReturn(Optional.of(history));
        when(imageStorageClient.getObject("android-tests/77/steps/3.png")).thenReturn(new ArtifactStorageObject(
            new ByteArrayInputStream("png".getBytes()),
            3,
            "image/png"
        ));

        var download = service.getStepImage(77, 3);

        assertThat(download.contentLength()).isEqualTo(3);
        assertThat(download.contentType()).isEqualTo("image/png");
        assertThat(download.inputStream().readAllBytes()).isEqualTo("png".getBytes());
    }
}
