package dev.themajorones.ats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.themajorones.ats.entity.AppAuthCode;

@Repository
public interface AppAuthCodeRepository extends JpaRepository<AppAuthCode, Integer> {

    Optional<AppAuthCode> findByCode(String code);
}
