package dev.themajorones.autotest.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.themajorones.models.entity.Docker;

@Repository
public interface DockerRepository extends JpaRepository<Docker, Integer> {

    Optional<Docker> findByBaseUrl(String baseUrl);

}
