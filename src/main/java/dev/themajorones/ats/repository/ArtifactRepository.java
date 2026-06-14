package dev.themajorones.ats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import dev.themajorones.models.entity.Artifact;
import dev.themajorones.models.entity.ArtifactSource;
import dev.themajorones.models.entity.GitHubWorkflowRun;

public interface ArtifactRepository extends JpaRepository<Artifact, Integer> {

    Optional<Artifact> findByGithubArtifactId(Long githubArtifactId);

    boolean existsByNameIgnoreCase(String name);

    List<Artifact> findAllByWorkflowRunOrderByIdDesc(GitHubWorkflowRun workflowRun);

    List<Artifact> findAllBySourceOrderByIdDesc(ArtifactSource source);

    List<Artifact> findAllByOrderByIdDesc();
}
