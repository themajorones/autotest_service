package dev.themajorones.ats.service.resource.impl;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.themajorones.ats.repository.AndroidRepository;
import dev.themajorones.ats.repository.TaskLogRepository;
import dev.themajorones.ats.service.resource.AndroidService;
import dev.themajorones.ats.service.resource.DockerService;
import dev.themajorones.models.client.AdbClient;
import dev.themajorones.models.client.DockerClient;
import dev.themajorones.models.constants.AndroidStatus;
import dev.themajorones.models.constants.AndroidType;
import dev.themajorones.models.constants.RabbitMqConstant;
import dev.themajorones.models.constants.TaskLogConstant;
import dev.themajorones.models.dto.AdbCommandResult;
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

@Service
@RequiredArgsConstructor
public class AndroidServiceImpl implements AndroidService {

    private static final Logger LOG = LoggerFactory.getLogger(AndroidServiceImpl.class);

    private static final Duration PORT_CHECK_TIMEOUT = Duration.ofSeconds(2);
    private final AndroidRepository androidRepository;
    private final TaskLogRepository taskLogRepository;
    private final DockerService dockerService;
    private final DockerClient dockerClient;
    private final AdbClient adbClient;
    private final RabbitOperations rabbitOperations;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    @Override
    public List<Map<String, Object>> listAndroid() {
        LOG.info("Listing Android connections");
        return androidRepository.findAllByOrderByIdDesc().stream().map(AndroidMapper::toFlatMap).toList();
    }

    @Transactional
    @Override
    public Map<String, Object> updateAndroid(Integer id, CreateAndroidRequest request) {
        LOG.info("Updating Android connection id={}", id);
        Android android = androidRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android not found"));
        CreateAndroidRequest normalized = normalizeAndroidRequest(request);
        if (isDirect(normalized)) {
            DirectAndroidMetadata metadata = connectDirectAndInspect(normalized);
            android.setDocker(null)
                .setType(AndroidType.DIRECT.name())
                .setName(requireText(normalized.getName(), "Android name"))
                .setImage(metadata.image())
                .setContainerId(null)
                .setContainerName(null)
                .setAdbHost(requireText(normalized.getAdbHost(), "ADB host"))
                .setAdbPort(requirePort(normalized.getAdbPort(), "ADB connect port"))
                .setDetails(new AndroidDetails()
                    .setWidth(metadata.width())
                    .setHeight(metadata.height())
                    .setDpi(metadata.dpi()));
            Android saved = androidRepository.save(android);
            LOG.info("Updated Direct Android connection id={} host={} port={}", saved.getId(), saved.getAdbHost(), saved.getAdbPort());
            return androidResponse(saved, AndroidStatus.CONNECTED.name(), "Direct Android connection updated");
        }

        if (android.getDetails() == null) {
            android.setDetails(new AndroidDetails());
        }

        android.setDocker(dockerService.getDocker(requireId(normalized.getDockerId(), "Docker connection id")))
                .setType(AndroidType.REDROID.name())
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
        Android saved = androidRepository.save(android);
        LOG.info("Updated Redroid Android connection id={} dockerId={}", saved.getId(), saved.getDocker().getId());
        return androidResponse(saved, checkStatus(saved), "Redroid Android connection updated");
    }

    @Transactional(readOnly = true)
    @Override
    public AndroidDetail getAndroid(Integer id) {
        LOG.info("Loading Android detail id={}", id);
        Android android = androidRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android not found"));
        String inspectJson = null;
        if (isRedroid(android) && hasText(android.getContainerId())) {
            inspectJson = dockerClient.inspectContainerJson(android.getDocker().getBaseUrl(), android.getContainerId());
        }
        return new AndroidDetail().setAndroid(AndroidMapper.toFlatMap(android)).setDockerInspectJson(inspectJson);
    }

    @Transactional
    @Override
    public Map<String, Object> createAndroid(CreateAndroidRequest request) {
        CreateAndroidRequest normalized = normalizeAndroidRequest(request);
        normalized.setName(requireText(normalized.getName(), "Android name"));
        if (isDirect(normalized)) {
            LOG.info("Creating Direct Android connection name={} host={} port={} pairRequested={}",
                normalized.getName(),
                normalized.getAdbHost(),
                normalized.getAdbPort(),
                hasPairRequest(normalized));
            DirectAndroidMetadata metadata = connectDirectAndInspect(normalized);
            Android saved = androidRepository.save(AndroidMapper.fromRequest(normalized, null)
                .setDocker(null)
                .setImage(metadata.image())
                .setDetails(new AndroidDetails()
                    .setWidth(metadata.width())
                    .setHeight(metadata.height())
                    .setDpi(metadata.dpi())));
            LOG.info("Saved Direct Android connection id={} host={} port={}", saved.getId(), saved.getAdbHost(), saved.getAdbPort());
            return androidResponse(saved, AndroidStatus.CONNECTED.name(), "Direct Android connection saved");
        }

        LOG.info("Creating Redroid Android connection name={} dockerId={}", normalized.getName(), normalized.getDockerId());
        TaskLog taskLog = taskLogRepository.save(new TaskLog()
            .setType(TaskLogConstant.Type.CREATE_ANDROID)
            .setStatus(TaskLogConstant.Status.PENDING)
            .setContent(androidTaskContent(normalized)));

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

        LOG.info("Queued Redroid Android creation taskLogId={} dockerId={}", taskLog.getId(), normalized.getDockerId());
        return Map.of(
            "status", TaskLogConstant.Status.QUEUED,
            "message", "Redroid Android creation queued",
            "taskLogId", taskLog.getId()
        );
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> pairDirectAndroid(CreateAndroidRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Android request is required");
        }
        LOG.info("Pairing Direct Android host={} pairPort={}", request.getAdbHost(), request.getPairPort());
        AdbCommandResult result = adbClient.pair(
            requireText(request.getAdbHost(), "ADB pair host"),
            requirePort(request.getPairPort(), "ADB pair port"),
            requireText(request.getPairCode(), "ADB pair code")
        );
        LOG.info("Paired Direct Android host={} pairPort={}", request.getAdbHost(), request.getPairPort());
        return Map.of(
            "status", AndroidStatus.PAIRED.name(),
            "message", "ADB pair completed",
            "stdout", result.getStdout(),
            "stderr", result.getStderr()
        );
    }

    @Transactional
    @Override
    public Map<String, Object> startAndroid(Integer id) {
        LOG.info("Starting Android connection id={}", id);
        Android android = androidRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android not found"));
        requireRedroid(android, "start");
        if (!hasText(android.getContainerId())) {
            throw new IllegalStateException("Android container has not been created yet");
        }
        dockerClient.startContainer(android.getDocker().getBaseUrl(), android.getContainerId());
        Integer mappedPort = dockerClient.mappedAdbPort(android.getDocker().getBaseUrl(), android.getContainerId());
        if (mappedPort != null) {
            android.setAdbHost(dockerClient.hostFromBaseUrl(android.getDocker().getBaseUrl())).setAdbPort(mappedPort);
            androidRepository.save(android);
        }
        LOG.info("Started Android connection id={} status={}", id, checkStatus(android));
        return androidResponse(android, checkStatus(android), "Redroid Android start requested");
    }

    @Transactional
    @Override
    public Map<String, Object> stopAndroid(Integer id) {
        LOG.info("Stopping Android connection id={}", id);
        Android android = androidRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android not found"));
        requireRedroid(android, "stop");
        if (hasText(android.getContainerId())) {
            dockerClient.stopContainer(android.getDocker().getBaseUrl(), android.getContainerId());
        }
        Map<String, Object> values = AndroidMapper.toFlatMap(android);
        values.put("status", AndroidStatus.STOPPED.name());
        values.put("message", "Redroid Android stopped");
        LOG.info("Stopped Android connection id={}", id);
        return values;
    }

    @Transactional
    @Override
    public void deleteAndroid(Integer id) {
        LOG.info("Deleting Android connection id={}", id);
        Android android = androidRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android not found"));
        if (isRedroid(android) && hasText(android.getContainerId())) {
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
        LOG.info("Deleted Android connection id={}", id);
    }

    private boolean isDockerNotFound(Exception ex) {
        String message = ex.getMessage();
        return message != null && (message.contains("404") || message.contains("NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    @Override
    public String checkHealth(Integer id) {
        Android record = androidRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android not found"));
        String status = checkStatus(record);
        LOG.info("Checked Android health id={} status={}", id, status);
        return status;
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
        if (isDirect(android)) {
            return checkDirectStatus(android);
        }
        if (!hasText(android.getContainerId())) {
            return AndroidStatus.NO_CONTAINER.name();
        }
        try {
            boolean running = dockerClient.isContainerRunning(android.getDocker().getBaseUrl(), android.getContainerId());
            if (!running) {
                return AndroidStatus.STOPPED.name();
            }
            boolean portOpen = dockerClient.isTcpPortReachable(android.getAdbHost(), android.getAdbPort(), PORT_CHECK_TIMEOUT);
            return portOpen ? AndroidStatus.RUNNING.name() : AndroidStatus.CANT_REACH.name();
        } catch (Exception ex) {
            return AndroidStatus.UNHEALTHY.name();
        }
    }

    private CreateAndroidRequest normalizeAndroidRequest(CreateAndroidRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Android request is required");
        }

        if (!hasText(request.getType())) {
            request.setType(AndroidType.REDROID.name());
        }
        request.setType(request.getType().trim().toUpperCase());
        if (!AndroidType.REDROID.name().equals(request.getType()) && !AndroidType.DIRECT.name().equals(request.getType())) {
            throw new IllegalArgumentException("Android type must be REDROID or DIRECT");
        }

        if (isDirect(request)) {
            request.setAdbHost(requireText(request.getAdbHost(), "ADB host"));
            request.setAdbPort(requirePort(request.getAdbPort(), "ADB connect port"));
            return request;
        }

        request.setImage(requireText(request.getImage(), "Android image"));
        request.setAccelerationMode(requireText(request.getAccelerationMode(), "Android acceleration mode"));
        request.setAccelerationMode(request.getAccelerationMode().trim().toUpperCase());
        request.setDockerId(requireId(request.getDockerId(), "Docker connection id"));
        return request;
    }

    private DirectAndroidMetadata connectDirectAndInspect(CreateAndroidRequest request) {
        pairDirectIfRequested(request);
        String serial = connectDirect(request);
        return inspectDirectAndroid(serial);
    }

    private void pairDirectIfRequested(CreateAndroidRequest request) {
        if (hasPairRequest(request)) {
            LOG.info("Pairing Direct Android host={} pairPort={}", request.getAdbHost(), request.getPairPort());
            try {
                adbClient.pair(
                    requireText(request.getAdbHost(), "ADB host"),
                    requirePort(request.getPairPort(), "ADB pair port"),
                    requireText(request.getPairCode(), "ADB pair code")
                );
                LOG.info("Paired Direct Android host={} pairPort={}", request.getAdbHost(), request.getPairPort());
            } catch (RuntimeException ex) {
                if (!isPairProtocolFault(ex)) {
                    throw ex;
                }
                LOG.warn("ADB pair reported protocol fault for host={} pairPort={} but will continue to connect: {}",
                    request.getAdbHost(),
                    request.getPairPort(),
                    ex.getMessage());
            }
        } else if (request.getPairPort() != null || hasText(request.getPairCode())) {
            throw new IllegalArgumentException("ADB pair port and pair code must be provided together");
        }
    }

    private String connectDirect(CreateAndroidRequest request) {
        String host = requireText(request.getAdbHost(), "ADB host");
        Integer port = requirePort(request.getAdbPort(), "ADB connect port");
        String serial = host + ":" + port;
        LOG.info("Connecting Direct Android host={} port={}", host, port);
        AdbCommandResult result = adbClient.connect(host, port);
        String output = (result.getStdout() + "\n" + result.getStderr()).toLowerCase();
        if (!output.contains("connected to") && !output.contains("already connected to")) {
            LOG.warn("Direct Android connect failed host={} port={} output={}", host, port, output.strip());
            throw new IllegalStateException("ADB connect did not report a connected device: " + output.strip());
        }
        LOG.info("Connected Direct Android host={} port={}", host, port);
        return serial;
    }

    private boolean hasPairRequest(CreateAndroidRequest request) {
        return request.getPairPort() != null && hasText(request.getPairCode());
    }

    private Map<String, Object> androidResponse(Android android, String status, String message) {
        Map<String, Object> values = AndroidMapper.toFlatMap(android);
        values.put("status", status);
        values.put("message", message);
        values.put("androidId", android.getId());
        return values;
    }

    private String checkDirectStatus(Android android) {
        if (!hasText(android.getAdbHost()) || android.getAdbPort() == null) {
            return AndroidStatus.STOPPED.name();
        }
        try {
            String serial = android.getAdbHost() + ":" + android.getAdbPort();
            return adbClient.devices().getStdout()
                .lines()
                .map(String::strip)
                .filter(line -> line.startsWith(serial + "\t") || line.startsWith(serial + " "))
                .findFirst()
                .map(line -> line.contains("\tdevice") || line.endsWith(" device") ? AndroidStatus.RUNNING.name() : AndroidStatus.UNHEALTHY.name())
                .orElse(AndroidStatus.STOPPED.name());
        } catch (Exception ex) {
            return AndroidStatus.UNHEALTHY.name();
        }
    }

    private DirectAndroidMetadata inspectDirectAndroid(String serial) {
        String androidVersion = adbClient.shell(serial, "getprop", "ro.build.version.release").getStdout().strip();
        String kernelVersion = adbClient.shell(serial, "uname", "-r").getStdout().strip();
        Integer[] resolution = parseResolution(adbClient.shell(serial, "wm", "size").getStdout());
        Integer dpi = parseDensity(adbClient.shell(serial, "wm", "density").getStdout());
        if (!hasText(androidVersion)) {
            throw new IllegalStateException("ADB did not return an Android version for " + serial);
        }
        if (!hasText(kernelVersion)) {
            throw new IllegalStateException("ADB did not return a kernel version for " + serial);
        }
        if (resolution[0] == null || resolution[1] == null) {
            throw new IllegalStateException("ADB did not return a display resolution for " + serial);
        }
        if (dpi == null) {
            throw new IllegalStateException("ADB did not return a display density for " + serial);
        }
        String image = "Android " + androidVersion + " / Kernel " + kernelVersion;
        LOG.info("Inspected Direct Android serial={} image={} resolution={}x{} dpi={}", serial, image, resolution[0], resolution[1], dpi);
        return new DirectAndroidMetadata(image, resolution[0], resolution[1], dpi);
    }

    private boolean isPairProtocolFault(Throwable ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("protocol fault") || normalized.contains("couldn't read status message");
    }

    private Integer[] parseResolution(String output) {
        Integer width = null;
        Integer height = null;
        if (output != null) {
            for (String line : output.lines().map(String::strip).toList()) {
                if (!line.toLowerCase().contains("size:")) {
                    continue;
                }
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)x(\\d+)").matcher(line);
                if (matcher.find()) {
                    width = Integer.valueOf(matcher.group(1));
                    height = Integer.valueOf(matcher.group(2));
                    break;
                }
            }
        }
        return new Integer[] { width, height };
    }

    private Integer parseDensity(String output) {
        Integer dpi = null;
        if (output != null) {
            for (String line : output.lines().map(String::strip).toList()) {
                if (!line.toLowerCase().contains("density:")) {
                    continue;
                }
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(line);
                if (matcher.find()) {
                    dpi = Integer.valueOf(matcher.group(1));
                    break;
                }
            }
        }
        return dpi;
    }

    private boolean isRedroid(Android android) {
        return AndroidType.REDROID.name().equalsIgnoreCase(android.getType());
    }

    private boolean isDirect(Android android) {
        return AndroidType.DIRECT.name().equalsIgnoreCase(android.getType());
    }

    private boolean isDirect(CreateAndroidRequest request) {
        return AndroidType.DIRECT.name().equalsIgnoreCase(request.getType());
    }

    private void requireRedroid(Android android, String action) {
        if (!isRedroid(android) || android.getDocker() == null) {
            throw new IllegalStateException("Only Redroid Android connections can " + action);
        }
    }

    private Integer requirePort(Integer port, String label) {
        if (port == null || port <= 0 || port > 65535) {
            throw new IllegalArgumentException(label + " must be between 1 and 65535");
        }
        return port;
    }

    private static final class DirectAndroidMetadata {
        private final String image;
        private final Integer width;
        private final Integer height;
        private final Integer dpi;

        private DirectAndroidMetadata(String image, Integer width, Integer height, Integer dpi) {
            this.image = image;
            this.width = width;
            this.height = height;
            this.dpi = dpi;
        }

        private String image() {
            return image;
        }

        private Integer width() {
            return width;
        }

        private Integer height() {
            return height;
        }

        private Integer dpi() {
            return dpi;
        }
    }

    private String androidTaskContent(CreateAndroidRequest request) {
        return JsonUtils.writeJson(objectMapper, request, "Unable to serialize Android task content");
    }
}
