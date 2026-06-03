package dev.themajorones.ats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import dev.themajorones.models.entity.GitHubUser;

@Repository
public interface GitHubUserRepository extends JpaRepository<GitHubUser, Integer> {

    @EntityGraph(attributePaths = "owner")
    Optional<GitHubUser> findDetailedById(Integer id);

    Optional<GitHubUser> findByOwnerGithubId(Long githubId);

    Optional<GitHubUser> findByOwnerLogin(String login);
}
