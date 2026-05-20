package dev.themajorones.autotest.service.resource;

import java.util.List;
import java.util.Map;

import dev.themajorones.autotest.dto.connection.DockerConnectionRequest;
import dev.themajorones.models.entity.Docker;

public interface DockerService {

    List<Docker> listDocker();

    Docker getDocker(Integer id);

    Docker connectDocker(DockerConnectionRequest request);

    Docker updateDocker(Integer id, DockerConnectionRequest request);

    void deleteDocker(Integer id);

    String checkHealth(Integer id);

    List<Map<String, Object>> checkHealth(List<Integer> ids);
}
