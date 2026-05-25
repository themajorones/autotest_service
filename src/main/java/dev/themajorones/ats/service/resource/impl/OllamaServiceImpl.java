package dev.themajorones.ats.service.resource.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.themajorones.ats.dto.connection.OllamaConnectionRequest;
import dev.themajorones.ats.repository.OllamaRepository;
import dev.themajorones.ats.service.resource.OllamaService;
import dev.themajorones.models.client.OllamaClient;
import dev.themajorones.models.dto.OllamaModelSummary;
import dev.themajorones.models.entity.Ollama;
import static dev.themajorones.models.util.ValidationUtils.requireText;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OllamaServiceImpl implements OllamaService {

    private final OllamaRepository ollamaRepository;
    private final OllamaClient ollamaClient;

    @Transactional(readOnly = true)
    @Override
    public List<Ollama> listOllama() {
        return ollamaRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Override
    public Ollama getOllama(Integer id) {
        return ollamaRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Ollama connection not found"));
    }

    @Transactional
    @Override
    public Ollama connectOllama(OllamaConnectionRequest request) {
        Ollama ollama = new Ollama();
        populateOllamaFromRequest(ollama, request);
        return ollamaRepository.save(ollama);
    }

    @Transactional
    @Override
    public Ollama updateOllama(Integer id, OllamaConnectionRequest request) {
        Ollama ollama = getOllama(id);
        populateOllamaFromRequest(ollama, request);
        return ollamaRepository.save(ollama);
    }

    @Transactional
    @Override
    public void deleteOllama(Integer id) {
        ollamaRepository.delete(getOllama(id));
    }

    @Transactional(readOnly = true)
    @Override
    public List<OllamaModelSummary> listOllamaModels(Integer id) {
        return ollamaClient.listModels(getOllama(id).getBaseUrl());
    }

    @Override
    public List<OllamaModelSummary> listOllamaModels(String baseUrl) {
        return ollamaClient.listModels(ollamaClient.normalizeBaseUrl(requireText(baseUrl, "Ollama base URL")));
    }

    @Override
    public String checkHealth(Integer id) {
        Ollama ollama = getOllama(id);
        try {
            ollamaClient.isHealthy(ollama.getBaseUrl());
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
                result.put("type", "ollama");
                result.put("id", id);
                result.put("status", checkHealth(id));
                return result;
            })
            .toList();
    }

    private void populateOllamaFromRequest(Ollama ollama, OllamaConnectionRequest request) {
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
            .setModel(selectedModel);
    }
}
