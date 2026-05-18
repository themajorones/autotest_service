package dev.themajorones.autotest.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.themajorones.models.entity.AndroidVMRecord;

@Repository
public interface AndroidVMRepository extends JpaRepository<AndroidVMRecord, Integer> {

    List<AndroidVMRecord> findAllByOrderByIdDesc();
}
