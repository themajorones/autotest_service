package dev.themajorones.ats.service.resource.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.themajorones.ats.dto.connection.DockerConnectionRequest;
import dev.themajorones.ats.repository.DockerRepository;
import dev.themajorones.ats.service.resource.DockerService;
import dev.themajorones.models.client.DockerClient;
import dev.themajorones.models.dto.DockerCapability;
import dev.themajorones.models.entity.Docker;
import dev.themajorones.models.util.JsonUtils;
import static dev.themajorones.models.util.ValidationUtils.requireText;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class DockerServiceImpl implements DockerService {

    private final DockerRepository dockerRepository;
    private final DockerClient dockerClient;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    @Override
    public List<Docker> listDocker() {
        return dockerRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Override
    public Docker getDocker(Integer id) {
        return dockerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Docker connection not found"));
    }

    @Transactional
    @Override
    public Docker connectDocker(DockerConnectionRequest request) {
        Docker docker = new Docker();
        populateDockerFromRequest(docker, request);
        return dockerRepository.save(docker);
    }

    @Transactional
    @Override
    public Docker updateDocker(Integer id, DockerConnectionRequest request) {
        Docker docker = getDocker(id);
        populateDockerFromRequest(docker, request);
        return dockerRepository.save(docker);
    }

    @Transactional
    @Override
    public void deleteDocker(Integer id) {
        dockerRepository.delete(getDocker(id));
    }

    @Override
    public String checkHealth(Integer id) {
        Docker docker = getDocker(id);
        try {
            dockerClient.isHealthy(docker.getBaseUrl());
            return "HEALTHY";
        } catch (Exception ex) {
            return "UNHEALTHY";
        }
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
                result.put("type", "docker");
                result.put("id", id);
                result.put("status", checkHealth(id));
                return result;
            })
            .toList();
    }

    private void populateDockerFromRequest(Docker docker, DockerConnectionRequest request) {
        String baseUrl = dockerClient.normalizeBaseUrl(requireText(request.getBaseUrl(), "Docker base URL"));
        DockerCapability capability = dockerClient.capabilities(baseUrl);
        docker
            .setName(requireText(request.getName(), "Docker name"))
            .setBaseUrl(baseUrl)
            .setEnabled(request.getEnabled() == null || request.getEnabled());
        updateDockerCapabilities(docker, capability);
    }

    private void updateDockerCapabilities(Docker docker, DockerCapability capability) {
        docker
            .setApiVersion(capability.getApiVersion())
            .setOs(capability.getOs())
            .setArch(capability.getArch())
            .setGraphicsRuntimesJson(JsonUtils.writeJson(objectMapper, capability.getGraphicsRuntimes(), "Unable to serialize JSON"))
            .setGpuDevicesJson(JsonUtils.writeJson(objectMapper, capability.getGpuDevices(), "Unable to serialize JSON"));
    }
}
