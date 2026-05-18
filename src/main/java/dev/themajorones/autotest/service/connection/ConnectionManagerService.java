package dev.themajorones.autotest.service.connection;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.themajorones.autotest.dto.connection.DockerConnectionRequest;
import dev.themajorones.autotest.dto.connection.OllamaConnectionRequest;
import dev.themajorones.autotest.repository.AndroidVMRepository;
import dev.themajorones.autotest.repository.DockerRepository;
import dev.themajorones.autotest.repository.OllamaRepository;
import dev.themajorones.autotest.repository.TaskLogRepository;
import dev.themajorones.models.client.DockerClient;
import dev.themajorones.models.client.OllamaClient;
import dev.themajorones.models.constants.ConnectionStatusConstant;
import dev.themajorones.models.constants.RabbitMqConstant;
import dev.themajorones.models.constants.TaskLogConstant;
import dev.themajorones.models.dto.AndroidVMDetail;
import dev.themajorones.models.dto.CreateAndroidVMRequest;
import dev.themajorones.models.dto.DockerCapability;
import dev.themajorones.models.dto.OllamaModelSummary;
import dev.themajorones.models.dto.TaskCommandEnvelope;
import dev.themajorones.models.entity.Docker;
import dev.themajorones.models.entity.Ollama;
import dev.themajorones.models.entity.AndroidVMRecord;
import dev.themajorones.models.entity.RetroidAndroidVM;
import dev.themajorones.models.entity.TaskLog;
import dev.themajorones.models.mapper.AndroidVmMapper;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class ConnectionManagerService {

    private static final Duration PORT_CHECK_TIMEOUT = Duration.ofSeconds(2);

    private final OllamaRepository ollamaRepository;
    private final DockerRepository dockerRepository;
    private final AndroidVMRepository androidVMRepository;
    private final TaskLogRepository taskLogRepository;
    private final OllamaClient ollamaClient;
    private final DockerClient dockerClient;
    private final RabbitOperations rabbitOperations;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<Ollama> listOllama() {
        return ollamaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Ollama getOllama(Integer id) {
        return ollamaRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Ollama connection not found"));
    }

    @Transactional
    public Ollama createOllama(OllamaConnectionRequest request) {
        Ollama ollama = new Ollama();
        applyOllamaRequest(ollama, request);
        return ollamaRepository.save(ollama);
    }

    @Transactional
    public Ollama updateOllama(Integer id, OllamaConnectionRequest request) {
        Ollama ollama = getOllama(id);
        applyOllamaRequest(ollama, request);
        return ollamaRepository.save(ollama);
    }

    @Transactional
    public void deleteOllama(Integer id) {
        ollamaRepository.delete(getOllama(id));
    }

    @Transactional(readOnly = true)
    public List<OllamaModelSummary> listOllamaModels(Integer id) {
        return ollamaClient.listModels(getOllama(id).getBaseUrl());
    }

    public List<OllamaModelSummary> listOllamaModels(String baseUrl) {
        return ollamaClient.listModels(ollamaClient.normalizeBaseUrl(requireText(baseUrl, "Ollama base URL")));
    }

    @Transactional(readOnly = true)
    public List<Docker> listDocker() {
        return dockerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Docker getDocker(Integer id) {
        return dockerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Docker connection not found"));
    }

    @Transactional
    public Docker createDocker(DockerConnectionRequest request) {
        Docker docker = new Docker();
        applyDockerRequest(docker, request);
        return dockerRepository.save(docker);
    }

    @Transactional
    public Docker updateDocker(Integer id, DockerConnectionRequest request) {
        Docker docker = getDocker(id);
        applyDockerRequest(docker, request);
        return dockerRepository.save(docker);
    }

    @Transactional
    public void deleteDocker(Integer id) {
        dockerRepository.delete(getDocker(id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAndroidVMs() {
        return androidVMRepository.findAllByOrderByIdDesc().stream()
            .map(AndroidVmMapper::toFlatMap)
            .toList();
    }

    @Transactional
    public Map<String, Object> updateAndroidVM(Integer id, CreateAndroidVMRequest request) {
        AndroidVMRecord record = getAndroidVmRecord(id);
        RetroidAndroidVM vm = asRetroid(AndroidVmMapper.fromRecord(record));
        CreateAndroidVMRequest normalized = normalizeAndroidRequest(request);
        vm
            .setDocker(getDocker(requireId(normalized.getDockerId(), "Docker connection id")))
            .setName(requireText(normalized.getName(), "Android VM name"))
            .setImage(normalized.getImage())
            .setAccelerationMode(normalized.getAccelerationMode());
        if (normalized.getWidth() != null) {
            vm.setWidth(normalized.getWidth());
        }
        if (normalized.getHeight() != null) {
            vm.setHeight(normalized.getHeight());
        }
        if (normalized.getDpi() != null) {
            vm.setDpi(normalized.getDpi());
        }
        return AndroidVmMapper.toFlatMap(androidVMRepository.save(AndroidVmMapper.toRecord(vm)));
    }

    @Transactional(readOnly = true)
    public AndroidVMDetail getAndroidVM(Integer id) {
        AndroidVMRecord record = getAndroidVmRecord(id);
        var vm = AndroidVmMapper.fromRecord(record);
        String inspectJson = null;
        if (hasText(vm.getContainerId())) {
            inspectJson = dockerClient.inspectContainerJson(vm.getDocker().getBaseUrl(), vm.getContainerId());
        }
        return new AndroidVMDetail().setAndroidVM(AndroidVmMapper.toFlatMap(vm)).setDockerInspectJson(inspectJson);
    }

    @Transactional
    public Map<String, Object> createAndroidVM(CreateAndroidVMRequest request) {
        Docker docker = getDocker(requireId(request.getDockerId(), "Docker connection id"));
        CreateAndroidVMRequest normalized = normalizeAndroidRequest(request);
        normalized.setName(requireText(normalized.getName(), "Android VM name"));
        RetroidAndroidVM vm = AndroidVmMapper.fromRequest(
            normalized,
            docker,
            ConnectionStatusConstant.QUEUED
        );
        AndroidVMRecord saved = androidVMRepository.save(AndroidVmMapper.toRecord(vm));

        TaskLog taskLog = taskLogRepository.save(new TaskLog()
            .setType(TaskLogConstant.Type.CREATE_ANDROID_VM)
            .setStatus(TaskLogConstant.Status.PENDING)
            .setContent(androidVmTaskContent(saved.getId(), docker.getId(), normalized)));

        TaskCommandEnvelope envelope = new TaskCommandEnvelope()
            .setTaskLogId(taskLog.getId())
            .setType(TaskLogConstant.Type.CREATE_ANDROID_VM)
            .setContent(taskLog.getContent());

        rabbitOperations.convertAndSend(
            RabbitMqConstant.DIRECT_EXCHANGE,
            RabbitMqConstant.Queue.ConnectionManager.ROUTING_KEY,
            writeJson(envelope)
        );
        taskLog.setStatus(TaskLogConstant.Status.QUEUED);
        taskLogRepository.save(taskLog);

        return Map.of("androidVMId", saved.getId(), "taskLogId", taskLog.getId());
    }

    @Transactional
    public Map<String, Object> stopAndroidVM(Integer id) {
        RetroidAndroidVM vm = asRetroid(AndroidVmMapper.fromRecord(getAndroidVmRecord(id)));
        if (hasText(vm.getContainerId())) {
            dockerClient.stopContainer(vm.getDocker().getBaseUrl(), vm.getContainerId());
        }
        vm.setStatus(ConnectionStatusConstant.STOPPED);
        return AndroidVmMapper.toFlatMap(androidVMRepository.save(AndroidVmMapper.toRecord(vm)));
    }

    @Transactional
    public void deleteAndroidVM(Integer id) {
        AndroidVMRecord record = getAndroidVmRecord(id);
        RetroidAndroidVM vm = asRetroid(AndroidVmMapper.fromRecord(record));
        if (hasText(vm.getContainerId())) {
            dockerClient.removeContainer(vm.getDocker().getBaseUrl(), vm.getContainerId());
        }
        androidVMRepository.delete(record);
    }

    @Transactional(readOnly = true)
    public List<TaskLog> listTaskLogs() {
        return taskLogRepository.findTop100ByOrderByIdDesc();
    }

    @Transactional
    public Map<String, Object> refreshConnectionHealth() {
        int ollamaCount = refreshOllamaHealth();
        int dockerCount = refreshDockerHealth();
        int androidCount = refreshAndroidHealth();
        return Map.of("ollama", ollamaCount, "docker", dockerCount, "android", androidCount);
    }

    @Transactional
    public int refreshOllamaHealth() {
        int count = 0;
        for (Ollama ollama : ollamaRepository.findAllByEnabledTrue()) {
            try {
                ollamaClient.isHealthy(ollama.getBaseUrl());
                ollama.setStatus(ConnectionStatusConstant.HEALTHY);
            } catch (Exception ex) {
                ollama.setStatus(ConnectionStatusConstant.UNHEALTHY);
            }
            count++;
        }
        return count;
    }

    @Transactional
    public int refreshDockerHealth() {
        int count = 0;
        for (Docker docker : dockerRepository.findAllByEnabledTrue()) {
            try {
                applyDockerCapability(docker, dockerClient.capabilities(docker.getBaseUrl()));
                docker.setStatus(ConnectionStatusConstant.HEALTHY);
            } catch (Exception ex) {
                docker.setStatus(ConnectionStatusConstant.UNHEALTHY);
            }
            count++;
        }
        return count;
    }

    @Transactional
    public int refreshAndroidHealth() {
        int count = 0;
        for (AndroidVMRecord record : androidVMRepository.findAll()) {
            refreshAndroidVmStatus(record);
            count++;
        }
        return count;
    }

    @Transactional
    public Ollama refreshOllamaHealth(Integer id) {
        Ollama ollama = getOllama(id);
        try {
            ollamaClient.isHealthy(ollama.getBaseUrl());
            ollama.setStatus(ConnectionStatusConstant.HEALTHY);
        } catch (Exception ex) {
            ollama.setStatus(ConnectionStatusConstant.UNHEALTHY);
        }
        return ollamaRepository.save(ollama);
    }

    @Transactional
    public Docker refreshDockerHealth(Integer id) {
        Docker docker = getDocker(id);
        try {
            applyDockerCapability(docker, dockerClient.capabilities(docker.getBaseUrl()));
            docker.setStatus(ConnectionStatusConstant.HEALTHY);
        } catch (Exception ex) {
            docker.setStatus(ConnectionStatusConstant.UNHEALTHY);
        }
        return dockerRepository.save(docker);
    }

    @Transactional
    public Map<String, Object> refreshAndroidHealth(Integer id) {
        AndroidVMRecord record = getAndroidVmRecord(id);
        refreshAndroidVmStatus(record);
        return AndroidVmMapper.toFlatMap(androidVMRepository.save(record));
    }

    private void applyOllamaRequest(Ollama ollama, OllamaConnectionRequest request) {
        String baseUrl = ollamaClient.normalizeBaseUrl(requireText(request.getBaseUrl(), "Ollama base URL"));
        List<OllamaModelSummary> models = ollamaClient.listModels(baseUrl);
        String selectedModel = requireText(request.getModel(), "Ollama model");
        boolean modelExists = models.stream().anyMatch(model ->
            selectedModel.equals(model.getName()) || selectedModel.equals(model.getModel()));
        if (!modelExists) {
            throw new IllegalArgumentException("Ollama model is not available on the selected server");
        }
        ollama
            .setName(requireText(request.getName(), "Ollama name"))
            .setBaseUrl(baseUrl)
            .setEnabled(request.getEnabled() == null || request.getEnabled())
            .setModel(selectedModel)
            .setStatus(ConnectionStatusConstant.HEALTHY);
    }

    private void applyDockerRequest(Docker docker, DockerConnectionRequest request) {
        String baseUrl = dockerClient.normalizeBaseUrl(requireText(request.getBaseUrl(), "Docker base URL"));
        DockerCapability capability = dockerClient.capabilities(baseUrl);
        docker
            .setName(requireText(request.getName(), "Docker name"))
            .setBaseUrl(baseUrl)
            .setEnabled(request.getEnabled() == null || request.getEnabled())
            .setStatus(ConnectionStatusConstant.HEALTHY);
        applyDockerCapability(docker, capability);
    }

    private void applyDockerCapability(Docker docker, DockerCapability capability) {
        docker
            .setApiVersion(capability.getApiVersion())
            .setOs(capability.getOs())
            .setArch(capability.getArch())
            .setNvidiaRuntimeAvailable(capability.isNvidiaRuntimeAvailable())
            .setGpuDevicesJson(writeJson(capability.getGpuDevices()));
    }

    private void refreshAndroidVmStatus(AndroidVMRecord record) {
        RetroidAndroidVM vm = asRetroid(AndroidVmMapper.fromRecord(record));
        if (!hasText(vm.getContainerId()) || ConnectionStatusConstant.DELETED.equals(vm.getStatus())) {
            return;
        }
        try {
            boolean running = dockerClient.isContainerRunning(vm.getDocker().getBaseUrl(), vm.getContainerId());
            if (!running) {
                vm.setStatus(ConnectionStatusConstant.STOPPED);
            } else if (!ConnectionStatusConstant.READY.equals(vm.getStatus())) {
                boolean portOpen = dockerClient.isTcpPortReachable(vm.getAdbHost(), vm.getAdbPort(), PORT_CHECK_TIMEOUT);
                vm.setStatus(portOpen ? ConnectionStatusConstant.RUNNING : ConnectionStatusConstant.UNHEALTHY);
            }
        } catch (Exception ex) {
            vm.setStatus(ConnectionStatusConstant.UNHEALTHY);
        }
        androidVMRepository.save(AndroidVmMapper.toRecord(vm));
    }

    private AndroidVMRecord getAndroidVmRecord(Integer id) {
        return androidVMRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Android VM not found"));
    }

    private CreateAndroidVMRequest normalizeAndroidRequest(CreateAndroidVMRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Android VM request is required");
        }
        if (!hasText(request.getImage())) {
            request.setImage(CreateAndroidVMRequest.DEFAULT_IMAGE);
        }
        if (!hasText(request.getAccelerationMode())) {
            request.setAccelerationMode(CreateAndroidVMRequest.DEFAULT_ACCELERATION_MODE);
        }
        request.setAccelerationMode(request.getAccelerationMode().trim().toUpperCase());
        return request;
    }

    private String androidVmTaskContent(Integer androidVmId, Integer dockerId, CreateAndroidVMRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("androidVMId", androidVmId);
        root.put("dockerId", dockerId);
        root.put("vmType", RetroidAndroidVM.VM_TYPE);
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

    private RetroidAndroidVM asRetroid(dev.themajorones.models.entity.AndroidVM vm) {
        if (vm instanceof RetroidAndroidVM retroid) {
            return retroid;
        }
        throw new IllegalArgumentException("Unsupported Android VM type: " + vm.getVmType());
    }

    private Integer requireId(Integer value, String description) {
        if (value == null) {
            throw new IllegalArgumentException(description + " is required");
        }
        return value;
    }

    private String requireText(String value, String description) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(description + " is required");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize JSON", ex);
        }
    }
}
