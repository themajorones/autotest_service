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

import dev.themajorones.ats.dto.connection.HealthCheckRequest;
import dev.themajorones.ats.service.resource.AndroidService;
import dev.themajorones.models.constants.AndroidType;
import dev.themajorones.models.dto.AndroidDetail;
import dev.themajorones.models.dto.CreateAndroidRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AndroidResource {

    private final AndroidService service;

    @GetMapping("/api/connections/android")
    public List<Map<String, Object>> listAndroid() {
        return service.listAndroid();
    }

    @PostMapping("/api/connections/android")
    public ResponseEntity<Map<String, Object>> createAndroid(@RequestBody CreateAndroidRequest request) {
        Map<String, Object> body = service.createAndroid(request);
        if (AndroidType.DIRECT.name().equalsIgnoreCase(request.getType())) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.accepted().body(body);
    }

    @PostMapping("/api/connections/android/direct/pair")
    public Map<String, Object> pairDirectAndroid(@RequestBody CreateAndroidRequest request) {
        return service.pairDirectAndroid(request);
    }

    @PutMapping("/api/connections/android/{id}")
    public Map<String, Object> updateAndroid(@PathVariable Integer id, @RequestBody CreateAndroidRequest request) {
        return service.updateAndroid(id, request);
    }

    @GetMapping("/api/connections/android/{id}")
    public AndroidDetail getAndroid(@PathVariable Integer id) {
        return service.getAndroid(id);
    }

    @PostMapping("/api/connections/android/{id}/start")
    public Map<String, Object> startAndroid(@PathVariable Integer id) {
        return service.startAndroid(id);
    }

    @PostMapping("/api/connections/android/{id}/stop")
    public Map<String, Object> stopAndroid(@PathVariable Integer id) {
        return service.stopAndroid(id);
    }

    @DeleteMapping("/api/connections/android/{id}")
    public ResponseEntity<Void> deleteAndroid(@PathVariable Integer id) {
        service.deleteAndroid(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/connections/android/health")
    public List<Map<String, Object>> checkHealth(@RequestBody HealthCheckRequest request) {
        return service.checkHealth(request.getIds());
    }
}
