package dev.themajorones.ats.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.themajorones.models.entity.GitHubRepo;
import dev.themajorones.models.entity.GitHubWorkflowRun;

public interface GitHubWorkflowRunRepository extends JpaRepository<GitHubWorkflowRun, Integer> {

    Optional<GitHubWorkflowRun> findByGithubRunId(Long githubRunId);

    List<GitHubWorkflowRun> findAllByRepoAndWorkflowIdOrderByGithubRunIdDesc(GitHubRepo repo, Long workflowId);

    List<GitHubWorkflowRun> findAllByRepoAndHeadShaOrderByGithubRunIdDesc(GitHubRepo repo, String headSha);

    List<GitHubWorkflowRun> findAllByRepoAndWorkflowIdAndHeadShaOrderByGithubRunIdDesc(
        GitHubRepo repo,
        Long workflowId,
        String headSha
    );
}
