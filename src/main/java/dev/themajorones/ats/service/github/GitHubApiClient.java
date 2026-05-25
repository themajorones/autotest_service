package dev.themajorones.ats.service.github;

import java.util.List;
import dev.themajorones.ats.dto.github.GitHubArtifactResponse;
import dev.themajorones.ats.dto.github.GitHubOwnerResponse;
import dev.themajorones.ats.dto.github.GitHubRepoResponse;
import dev.themajorones.ats.dto.github.GitHubWorkflowRunResponse;

public interface GitHubApiClient {

    GitHubOwnerResponse getCurrentUser(String accessToken);

    List<GitHubOwnerResponse> getOrganizations(String accessToken);

    List<GitHubRepoResponse> getAccessibleRepositories(String accessToken);

    List<GitHubWorkflowRunResponse> getWorkflowRuns(String accessToken, String owner, String repo);

    List<GitHubArtifactResponse> getArtifacts(String accessToken, String owner, String repo, Long workflowRunId);

    byte[] downloadArtifact(String accessToken, String owner, String repo, Long artifactId);
}
