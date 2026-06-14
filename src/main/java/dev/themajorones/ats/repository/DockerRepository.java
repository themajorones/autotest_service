package dev.themajorones.ats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.themajorones.models.entity.Docker;

public interface DockerRepository extends JpaRepository<Docker, Integer> {

    Optional<Docker> findByBaseUrl(String baseUrl);

}
