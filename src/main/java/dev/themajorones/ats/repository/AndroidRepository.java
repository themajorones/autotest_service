package dev.themajorones.ats.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.themajorones.models.entity.Android;

public interface AndroidRepository extends JpaRepository<Android, Integer> {

    List<Android> findAllByOrderByIdDesc();
}
