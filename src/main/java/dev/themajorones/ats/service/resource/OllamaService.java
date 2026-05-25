package dev.themajorones.ats.service.resource;

import java.util.List;
import java.util.Map;

import dev.themajorones.ats.dto.connection.OllamaConnectionRequest;
import dev.themajorones.models.dto.OllamaModelSummary;
import dev.themajorones.models.entity.Ollama;

public interface OllamaService {

    List<Ollama> listOllama();

    Ollama getOllama(Integer id);

    Ollama connectOllama(OllamaConnectionRequest request);

    Ollama updateOllama(Integer id, OllamaConnectionRequest request);

    void deleteOllama(Integer id);

    List<OllamaModelSummary> listOllamaModels(Integer id);

    List<OllamaModelSummary> listOllamaModels(String baseUrl);

    String checkHealth(Integer id);

    List<Map<String, Object>> checkHealth(List<Integer> ids);
}
