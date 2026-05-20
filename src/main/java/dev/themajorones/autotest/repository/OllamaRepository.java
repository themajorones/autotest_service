package dev.themajorones.autotest.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.themajorones.models.entity.Ollama;

@Repository
public interface OllamaRepository extends JpaRepository<Ollama, Integer> {

    Optional<Ollama> findByBaseUrl(String baseUrl);

}
