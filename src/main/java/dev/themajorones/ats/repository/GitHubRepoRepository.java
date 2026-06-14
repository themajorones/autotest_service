package dev.themajorones.ats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.themajorones.models.entity.GitHubRepo;

public interface GitHubRepoRepository extends JpaRepository<GitHubRepo, Integer> {

    Optional<GitHubRepo> findByGithubId(Long githubId);

    Optional<GitHubRepo> findByFullName(String fullName);
}
