package dev.themajorones.ats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.themajorones.ats.entity.AppAuthCode;

public interface AppAuthCodeRepository extends JpaRepository<AppAuthCode, Integer> {

    Optional<AppAuthCode> findByCode(String code);
}
