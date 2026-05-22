package dev.themajorones.autotest.service.resource.impl;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.themajorones.autotest.repository.AndroidRepository;
import dev.themajorones.autotest.repository.TaskLogRepository;
import dev.themajorones.autotest.service.resource.AndroidService;
import dev.themajorones.autotest.service.resource.DockerService;
import dev.themajorones.models.client.DockerClient;
import dev.themajorones.models.constants.RabbitMqConstant;
import dev.themajorones.models.constants.TaskLogConstant;
import dev.themajorones.models.dto.AndroidDetail;
import dev.themajorones.models.dto.CreateAndroidRequest;
import dev.themajorones.models.dto.TaskCommandEnvelope;
import dev.themajorones.models.entity.Android;
import dev.themajorones.models.entity.AndroidDetails;
import dev.themajorones.models.entity.Docker;
import dev.themajorones.models.entity.TaskLog;
import dev.themajorones.models.mapper.AndroidMapper;
import dev.themajorones.models.util.JsonUtils;
import static dev.themajorones.models.util.ValidationUtils.hasText;
import static dev.themajorones.models.util.ValidationUtils.requireId;
import static dev.themajorones.models.util.ValidationUtils.requireText;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class AndroidServiceImpl implements AndroidService {
    
    // TODO: support physical android devices in the future

    private static final Logger LOG = LoggerFactory.getLogger(AndroidServiceImpl.class);

    private static final Duration PORT_CHECK_TIMEOUT = Duration.ofSeconds(2);

    private final AndroidRepository androidRepository;
    private final TaskLogRepository taskLogRepository;
    private final DockerService dockerService;
    private final DockerClient dockerClient;
    private final RabbitOperations rabbitOperations;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    @Override
    public List<Map<String, Object>> listAndroid() {
        return androidRepository.findAllByOrderByIdDesc().stream().map(AndroidMapper::toFlatMap).toList();
    }

    @Transactional
    @Override
    public Map<String, Object> updateAndroid(Integer id, CreateAndroidRequest request) {
        Android android = androidRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android not found"));
        CreateAndroidRequest normalized = normalizeAndroidRequest(request);
        if (android.getDetails() == null) {
            android.setDetails(new AndroidDetails());
        }

        android.setDocker(dockerService.getDocker(requireId(normalized.getDockerId(), "Docker connection id")))
                .setType(normalized.getType())
                .setName(requireText(normalized.getName(), "Android name"))
                .setImage(normalized.getImage())
                .getDetails().setAccelerationMode(normalized.getAccelerationMode());

        if (normalized.getWidth() != null) {
            android.getDetails().setWidth(normalized.getWidth());
        }
        if (normalized.getHeight() != null) {
            android.getDetails().setHeight(normalized.getHeight());
        }
        if (normalized.getDpi() != null) {
            android.getDetails().setDpi(normalized.getDpi());
        }
        return AndroidMapper.toFlatMap(androidRepository.save(android));
    }

    @Transactional(readOnly = true)
    @Override
    public AndroidDetail getAndroid(Integer id) {
        Android android = androidRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android not found"));
        String inspectJson = null;
        if (hasText(android.getContainerId())) {
            inspectJson = dockerClient.inspectContainerJson(android.getDocker().getBaseUrl(), android.getContainerId());
        }
        return new AndroidDetail().setAndroid(AndroidMapper.toFlatMap(android)).setDockerInspectJson(inspectJson);
    }

    @Transactional
    @Override
    public Map<String, Object> createAndroid(CreateAndroidRequest request) {
        Docker docker = dockerService.getDocker(requireId(request.getDockerId(), "Docker connection id"));
        CreateAndroidRequest normalized = normalizeAndroidRequest(request);
        normalized.setName(requireText(normalized.getName(), "Android name"));
        Android android = AndroidMapper.fromRequest(normalized, docker);
        Android saved = androidRepository.save(AndroidMapper.toRecord(android));

        TaskLog taskLog = taskLogRepository.save(new TaskLog()
            .setType(TaskLogConstant.Type.CREATE_ANDROID)
            .setStatus(TaskLogConstant.Status.PENDING)
            .setContent(androidTaskContent(saved.getId(), docker.getId(), normalized)));

        TaskCommandEnvelope envelope = new TaskCommandEnvelope()
            .setTaskLogId(taskLog.getId())
            .setType(TaskLogConstant.Type.CREATE_ANDROID)
            .setContent(taskLog.getContent());

        rabbitOperations.convertAndSend(
            RabbitMqConstant.DIRECT_EXCHANGE,
            RabbitMqConstant.Queue.Android.ROUTING_KEY,
            JsonUtils.writeJson(objectMapper, envelope, "Unable to serialize JSON")
        );
        taskLog.setStatus(TaskLogConstant.Status.QUEUED);
        taskLogRepository.save(taskLog);

        return Map.of("androidId", saved.getId(), "taskLogId", taskLog.getId());
    }

    @Transactional
    @Override
    public Map<String, Object> stopAndroid(Integer id) {
        Android android = androidRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android not found"));
        if (hasText(android.getContainerId())) {
            dockerClient.stopContainer(android.getDocker().getBaseUrl(), android.getContainerId());
        }
        Map<String, Object> values = AndroidMapper.toFlatMap(android);
        values.put("status", "STOPPED");
        return values;
    }

    @Transactional
    @Override
    public void deleteAndroid(Integer id) {
        Android android = androidRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android not found"));
        if (hasText(android.getContainerId())) {
            try {
                dockerClient.removeContainer(android.getDocker().getBaseUrl(), android.getContainerId());
            } catch (Exception ex) {
                if (!isDockerNotFound(ex)) {
                    throw ex;
                }
                LOG.info("Android container already deleted for androidId={} containerId={}", android.getId(), android.getContainerId());
            }
        }
        androidRepository.delete(android);
    }

    private boolean isDockerNotFound(Exception ex) {
        String message = ex.getMessage();
        return message != null && (message.contains("404") || message.contains("NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    @Override
    public String checkHealth(Integer id) {
        Android record = androidRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android not found"));
        return checkStatus(record);
    }

    @Transactional(readOnly = true)
    @Override
    public List<Map<String, Object>> checkHealth(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Health resource ids are required");
        }
        return ids.stream()
            .map(id -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "android");
                result.put("id", id);
                result.put("status", checkHealth(id));
                return result;
            })
            .toList();
    }

    private String checkStatus(Android android) {
        if (!hasText(android.getContainerId())) {
            return "NO_CONTAINER";
        }
        try {
            boolean running = dockerClient.isContainerRunning(android.getDocker().getBaseUrl(), android.getContainerId());
            if (!running) {
                return "STOPPED";
            }
            boolean portOpen = dockerClient.isTcpPortReachable(android.getAdbHost(), android.getAdbPort(), PORT_CHECK_TIMEOUT);
            return portOpen ? "RUNNING" : "CANT_REACH";
        } catch (Exception ex) {
            return "UNHEALTHY";
        }
    }

    private CreateAndroidRequest normalizeAndroidRequest(CreateAndroidRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Android request is required");
        }

        if (!hasText(request.getImage())) {
            request.setImage(CreateAndroidRequest.DEFAULT_IMAGE);
        }

        if (!hasText(request.getAccelerationMode())) {
            request.setAccelerationMode(CreateAndroidRequest.DEFAULT_ACCELERATION_MODE);
        }

        request.setAccelerationMode(request.getAccelerationMode().trim().toUpperCase());
        return request;
    }

    private String androidTaskContent(Integer androidId, Integer dockerId, CreateAndroidRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("androidId", androidId);
        root.put("dockerId", dockerId);
        root.put("type", request.getType());
        root.put("name", request.getName());
        root.put("image", request.getImage());
        root.put("accelerationMode", request.getAccelerationMode());

        if (request.getWidth() != null) {
            root.put("width", request.getWidth());
        }
        if (request.getHeight() != null) {
            root.put("height", request.getHeight());
        }
        if (request.getDpi() != null) {
            root.put("dpi", request.getDpi());
        }

        return root.toString();
    }
}
