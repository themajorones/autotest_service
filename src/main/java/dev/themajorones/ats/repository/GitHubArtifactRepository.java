package dev.themajorones.ats.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import dev.themajorones.models.entity.GitHubArtifact;
import dev.themajorones.models.entity.GitHubWorkflowRun;

@Repository
public interface GitHubArtifactRepository extends JpaRepository<GitHubArtifact, Integer> {

    Optional<GitHubArtifact> findByGithubArtifactId(Long githubArtifactId);

    Optional<GitHubArtifact> findFirstByWorkflowRunAndExpiresAtGreaterThanEqualOrderByExpiresAtDesc(
        GitHubWorkflowRun workflowRun,
        Long expiresAt
    );

    List<GitHubArtifact> findAllByWorkflowRunOrderByExpiresAtDesc(GitHubWorkflowRun workflowRun);
}
