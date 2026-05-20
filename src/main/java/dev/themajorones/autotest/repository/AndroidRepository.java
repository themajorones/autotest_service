package dev.themajorones.autotest.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.themajorones.models.entity.Android;

@Repository
public interface AndroidRepository extends JpaRepository<Android, Integer> {

    List<Android> findAllByOrderByIdDesc();
}
