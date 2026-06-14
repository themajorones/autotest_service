package dev.themajorones.ats.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.themajorones.models.entity.GitHubOwner;

public interface GitHubOwnerRepository extends JpaRepository<GitHubOwner, Integer> {

    Optional<GitHubOwner> findByGithubId(Long githubId);

    Optional<GitHubOwner> findByLogin(String login);
}
