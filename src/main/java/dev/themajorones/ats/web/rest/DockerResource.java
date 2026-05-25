package dev.themajorones.ats.web.rest;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.themajorones.ats.dto.connection.DockerConnectionRequest;
import dev.themajorones.ats.dto.connection.HealthCheckRequest;
import dev.themajorones.ats.service.resource.DockerService;
import dev.themajorones.models.entity.Docker;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class DockerResource {

    private final DockerService service;

    @GetMapping("/api/connections/docker")
    public List<Docker> listDocker() {
        return service.listDocker();
    }

    @PostMapping("/api/connections/docker")
    public Docker connectDocker(@RequestBody DockerConnectionRequest request) {
        return service.connectDocker(request);
    }

    @GetMapping("/api/connections/docker/{id}")
    public Docker getDocker(@PathVariable Integer id) {
        return service.getDocker(id);
    }

    @PutMapping("/api/connections/docker/{id}")
    public Docker updateDocker(@PathVariable Integer id, @RequestBody DockerConnectionRequest request) {
        return service.updateDocker(id, request);
    }

    @DeleteMapping("/api/connections/docker/{id}")
    public ResponseEntity<Void> deleteDocker(@PathVariable Integer id) {
        service.deleteDocker(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/connections/docker/health")
    public List<Map<String, Object>> checkHealth(@RequestBody HealthCheckRequest request) {
        return service.checkHealth(request.getIds());
    }
}
