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
import dev.themajorones.ats.dto.connection.OllamaConnectionRequest;
import dev.themajorones.ats.service.resource.OllamaService;
import dev.themajorones.models.dto.OllamaModelSummary;
import dev.themajorones.models.entity.Ollama;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class OllamaResource {

    private final OllamaService service;

    @GetMapping("/api/connections/ollama")
    public List<Ollama> listOllama() {
        return service.listOllama();
    }

    @PostMapping("/api/connections/ollama")
    public Ollama connectOllama(@RequestBody OllamaConnectionRequest request) {
        return service.connectOllama(request);
    }

    @GetMapping("/api/connections/ollama/{id}")
    public Ollama getOllama(@PathVariable Integer id) {
        return service.getOllama(id);
    }

    @PutMapping("/api/connections/ollama/{id}")
    public Ollama updateOllama(@PathVariable Integer id, @RequestBody OllamaConnectionRequest request) {
        return service.updateOllama(id, request);
    }

    @DeleteMapping("/api/connections/ollama/{id}")
    public ResponseEntity<Void> deleteOllama(@PathVariable Integer id) {
        service.deleteOllama(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/connections/ollama/{id}/models")
    public List<OllamaModelSummary> listOllamaModels(@PathVariable Integer id) {
        return service.listOllamaModels(id);
    }

    @PostMapping("/api/connections/ollama/models")
    public List<OllamaModelSummary> listOllamaModelsForBaseUrl(@RequestBody OllamaConnectionRequest request) {
        return service.listOllamaModels(request.getBaseUrl());
    }

    @PostMapping("/api/connections/ollama/health")
    public List<Map<String, Object>> checkHealth(@RequestBody HealthCheckRequest request) {
        return service.checkHealth(request.getIds());
    }
}
