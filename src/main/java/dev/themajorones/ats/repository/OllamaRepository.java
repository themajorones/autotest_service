package dev.themajorones.ats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.themajorones.models.entity.Ollama;

public interface OllamaRepository extends JpaRepository<Ollama, Integer> {

    Optional<Ollama> findByBaseUrl(String baseUrl);

}
