package dev.themajorones.ats.service.impl;

import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dev.themajorones.ats.dto.androidtest.AndroidTestDetailResponse;
import dev.themajorones.ats.dto.androidtest.AndroidTestImageDownload;
import dev.themajorones.ats.dto.androidtest.AndroidTestStepHistoryResponse;
import dev.themajorones.ats.repository.AndroidRepository;
import dev.themajorones.ats.repository.AndroidTestStepHistoryRepository;
import dev.themajorones.ats.repository.ArtifactRepository;
import dev.themajorones.ats.repository.OllamaRepository;
import dev.themajorones.ats.repository.TaskLogRepository;
import dev.themajorones.ats.service.AndroidTestService;
import dev.themajorones.ats.service.progress.TaskProgressBroadcaster;
import dev.themajorones.ats.service.storage.ImageStorageClient;
import dev.themajorones.ats.service.storage.ArtifactStorageObject;
import dev.themajorones.models.constants.RabbitMqConstant;
import dev.themajorones.models.constants.TaskLogConstant;
import dev.themajorones.models.constants.TaskProgressConstant;
import dev.themajorones.models.dto.RunAndroidTestRequest;
import dev.themajorones.models.dto.TaskCommandEnvelope;
import dev.themajorones.models.entity.Android;
import dev.themajorones.models.entity.AndroidTestStepHistory;
import dev.themajorones.models.entity.Artifact;
import dev.themajorones.models.entity.Ollama;
import dev.themajorones.models.entity.TaskLog;
import dev.themajorones.models.util.JsonUtils;
import dev.themajorones.models.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AndroidTestServiceImpl implements AndroidTestService {

    private static final int DEFAULT_MAX_STEPS = 20;

    private final ArtifactRepository artifactRepository;
    private final AndroidRepository androidRepository;
    private final OllamaRepository ollamaRepository;
    private final TaskLogRepository taskLogRepository;
    private final AndroidTestStepHistoryRepository androidTestStepHistoryRepository;
    private final TaskProgressBroadcaster taskProgressBroadcaster;
    private final RabbitOperations rabbitOperations;
    private final ImageStorageClient imageStorageClient;
    private final ObjectMapper objectMapper;

    @Transactional
    @Override
    public TaskLog runAndroidTest(RunAndroidTestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Android test request is required");
        }
        Artifact artifact = artifactRepository.findById(ValidationUtils.requireId(request.getArtifactId(), "Artifact id"))
            .orElseThrow(() -> new IllegalArgumentException("Artifact was not found"));
        Android android = androidRepository.findById(ValidationUtils.requireId(request.getAndroidId(), "Android id"))
            .orElseThrow(() -> new IllegalArgumentException("Android device was not found"));
        Ollama ollama = ollamaRepository.findById(ValidationUtils.requireId(request.getOllamaId(), "Ollama connection id"))
            .orElseThrow(() -> new IllegalArgumentException("Ollama connection was not found"));
        if (!StringUtils.hasText(artifact.getStorageKey())) {
            throw new IllegalStateException("Artifact file is not available for testing");
        }
        if (!StringUtils.hasText(android.getAdbHost()) || android.getAdbPort() == null) {
            throw new IllegalStateException("Android device does not have an ADB address");
        }
        if (!ollama.isEnabled()) {
            throw new IllegalStateException("Selected Ollama connection is disabled");
        }

        RunAndroidTestRequest normalized = new RunAndroidTestRequest()
            .setArtifactId(artifact.getId())
            .setAndroidId(android.getId())
            .setOllamaId(ollama.getId())
            .setObjective(ValidationUtils.requireText(request.getObjective(), "Test objective"))
            .setMaxSteps(normalizeMaxSteps(request.getMaxSteps()));

        TaskLog taskLog = taskLogRepository.save(new TaskLog()
            .setType(TaskLogConstant.Type.ANDROID_AUTONOMOUS_TEST)
            .setStatus(TaskLogConstant.Status.PENDING)
            .setContent(JsonUtils.writeJson(objectMapper, normalized, "Unable to serialize Android test request")));

        TaskCommandEnvelope envelope = new TaskCommandEnvelope()
            .setTaskLogId(taskLog.getId())
            .setType(TaskLogConstant.Type.ANDROID_AUTONOMOUS_TEST)
            .setContent(taskLog.getContent());

        rabbitOperations.convertAndSend(
            RabbitMqConstant.DIRECT_EXCHANGE,
            RabbitMqConstant.Queue.AndroidTest.ROUTING_KEY,
            JsonUtils.writeJson(objectMapper, envelope, "Unable to serialize JSON")
        );

        taskLog.setStatus(TaskLogConstant.Status.QUEUED);
        TaskLog saved = taskLogRepository.save(taskLog);
        taskProgressBroadcaster.broadcastTaskLog(saved, TaskProgressConstant.EventType.TASK_LOG_UPSERTED);
        return saved;
    }

    @Transactional(readOnly = true)
    @Override
    public AndroidTestDetailResponse getAndroidTest(Integer taskLogId) {
        TaskLog taskLog = loadAndroidTestTaskLog(taskLogId);
        int stepCount = androidTestStepHistoryRepository.findAllByTaskLogIdOrderByStepNumberAsc(taskLogId).size();
        return new AndroidTestDetailResponse(
            taskLog.getId(),
            taskLog.getStatus(),
            taskLog.getContent(),
            taskLog.getResult(),
            taskLog.getStartedAt(),
            taskLog.getEndedAt(),
            parseJson(taskLog.getContent()),
            parseJson(taskLog.getResult()),
            stepCount
        );
    }

    @Transactional(readOnly = true)
    @Override
    public List<AndroidTestStepHistoryResponse> listAndroidTestSteps(Integer taskLogId) {
        loadAndroidTestTaskLog(taskLogId);
        return androidTestStepHistoryRepository.findAllByTaskLogIdOrderByStepNumberAsc(taskLogId)
            .stream()
            .map(this::toStepResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public AndroidTestImageDownload getStepImage(Integer taskLogId, Integer stepNumber) {
        loadAndroidTestTaskLog(taskLogId);
        AndroidTestStepHistory history = androidTestStepHistoryRepository.findByTaskLogIdAndStepNumber(taskLogId, stepNumber)
            .orElseThrow(() -> new IllegalArgumentException("Android test step was not found"));
        if (!StringUtils.hasText(history.getImageStorageKey())) {
            throw new IllegalStateException("Android test step image is not available");
        }
        ArtifactStorageObject stored = imageStorageClient.getObject(history.getImageStorageKey());
        return new AndroidTestImageDownload(stored.inputStream(), stored.contentLength(), stored.contentType());
    }

    private int normalizeMaxSteps(Integer value) {
        if (value == null) {
            return DEFAULT_MAX_STEPS;
        }
        if (value < 1 || value > 100) {
            throw new IllegalArgumentException("Max steps must be between 1 and 100");
        }
        return value;
    }

    private TaskLog loadAndroidTestTaskLog(Integer taskLogId) {
        if (taskLogId == null) {
            throw new IllegalArgumentException("Task log id is required");
        }
        TaskLog taskLog = taskLogRepository.findById(taskLogId)
            .orElseThrow(() -> new IllegalArgumentException("Android test task log was not found"));
        if (!TaskLogConstant.Type.ANDROID_AUTONOMOUS_TEST.equals(taskLog.getType())) {
            throw new IllegalArgumentException("Task log is not an Android autonomous test");
        }
        return taskLog;
    }

    private Object parseJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception ex) {
            return value;
        }
    }

    private AndroidTestStepHistoryResponse toStepResponse(AndroidTestStepHistory history) {
        return new AndroidTestStepHistoryResponse(
            history.getId(),
            history.getTaskLogId(),
            history.getStepNumber(),
            history.getStartedAt(),
            history.getEndedAt(),
            history.getForeground(),
            history.getUiHash(),
            history.getUiContext(),
            history.getVisionProvider(),
            history.getVisionText(),
            history.getAction(),
            history.getState(),
            history.getTargetElementId(),
            history.getTargetX(),
            history.getTargetY(),
            history.getSwipeX1(),
            history.getSwipeY1(),
            history.getSwipeX2(),
            history.getSwipeY2(),
            history.getSwipeDurationMs(),
            history.getInputText(),
            history.getReasoning(),
            history.getDecisionJson(),
            history.getActionResult(),
            history.getError(),
            history.getImageStorageKey()
        );
    }
}
