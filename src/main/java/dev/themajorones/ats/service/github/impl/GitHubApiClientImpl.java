package dev.themajorones.ats.service.github.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import dev.themajorones.ats.dto.github.GitHubArtifactResponse;
import dev.themajorones.ats.dto.github.GitHubArtifactsResponse;
import dev.themajorones.ats.dto.github.GitHubOwnerResponse;
import dev.themajorones.ats.dto.github.GitHubRepoResponse;
import dev.themajorones.ats.dto.github.GitHubWorkflowRunResponse;
import dev.themajorones.ats.dto.github.GitHubWorkflowRunsResponse;
import dev.themajorones.ats.service.github.GitHubApiClient;

@Component
public class GitHubApiClientImpl implements GitHubApiClient {
    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;

    public GitHubApiClientImpl(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
            .baseUrl("https://api.github.com")
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .build();
    }

    @Override
    public GitHubOwnerResponse getCurrentUser(String accessToken) {
        return restClient.get()
            .uri("/user")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(GitHubOwnerResponse.class);
    }

    @Override
    public List<GitHubOwnerResponse> getOrganizations(String accessToken) {
        return getPagedArray(
            accessToken,
            "/user/orgs",
            GitHubOwnerResponse[].class
        );
    }

    @Override
    public List<GitHubRepoResponse> getAccessibleRepositories(String accessToken) {
        return getPagedArray(
            accessToken,
            "/user/repos",
            GitHubRepoResponse[].class
        );
    }

    @Override
    public List<GitHubWorkflowRunResponse> getWorkflowRuns(String accessToken, String owner, String repo) {
        List<GitHubWorkflowRunResponse> workflowRuns = new ArrayList<>();
        for (int page = 1; ; page++) {
            final int currentPage = page;
            GitHubWorkflowRunsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/{owner}/{repo}/actions/runs")
                    .queryParam("per_page", PAGE_SIZE)
                    .queryParam("page", currentPage)
                    .build(owner, repo))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(GitHubWorkflowRunsResponse.class);
            List<GitHubWorkflowRunResponse> pageItems = response == null || response.getWorkflowRuns() == null
                ? List.of()
                : response.getWorkflowRuns();
            if (pageItems.isEmpty()) {
                return workflowRuns;
            }
            workflowRuns.addAll(pageItems);
        }
    }

    @Override
    public List<GitHubArtifactResponse> getArtifacts(String accessToken, String owner, String repo, Long workflowRunId) {
        List<GitHubArtifactResponse> artifacts = new ArrayList<>();
        for (int page = 1; ; page++) {
            final int currentPage = page;
            GitHubArtifactsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/{owner}/{repo}/actions/runs/{workflowRunId}/artifacts")
                    .queryParam("per_page", PAGE_SIZE)
                    .queryParam("page", currentPage)
                    .build(owner, repo, workflowRunId))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(GitHubArtifactsResponse.class);
            List<GitHubArtifactResponse> pageItems = response == null || response.getArtifacts() == null
                ? List.of()
                : response.getArtifacts();
            if (pageItems.isEmpty()) {
                return artifacts;
            }
            artifacts.addAll(pageItems);
        }
    }

    @Override
    public byte[] downloadArtifact(String accessToken, String owner, String repo, Long artifactId) {
        return restClient.get()
            .uri("/repos/{owner}/{repo}/actions/artifacts/{artifactId}/zip", owner, repo, artifactId)
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(byte[].class);
    }

    private <T> List<T> getPagedArray(String accessToken, String path, Class<T[]> responseType) {
        List<T> items = new ArrayList<>();
        for (int page = 1; ; page++) {
            final int currentPage = page;
            T[] pageItems = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(path)
                    .queryParam("per_page", PAGE_SIZE)
                    .queryParam("page", currentPage)
                    .build())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(responseType);
            List<T> values = pageItems == null ? List.of() : Arrays.asList(pageItems);
            if (values.isEmpty()) {
                return items;
            }
            items.addAll(values);
        }
    }
}
