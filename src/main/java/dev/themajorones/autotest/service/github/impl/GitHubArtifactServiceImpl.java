package dev.themajorones.autotest.service.github.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.themajorones.autotest.dto.github.GitHubArtifactResponse;
import dev.themajorones.autotest.dto.github.GitHubWorkflowRunResponse;
import dev.themajorones.autotest.repository.GitHubArtifactRepository;
import dev.themajorones.autotest.repository.GitHubRepoRepository;
import dev.themajorones.autotest.repository.GitHubWorkflowRunRepository;
import dev.themajorones.autotest.service.github.GitHubApiClient;
import dev.themajorones.autotest.service.github.GitHubArtifactService;
import dev.themajorones.autotest.service.github.GitHubRepoSyncService;
import dev.themajorones.models.entity.GitHubArtifact;
import dev.themajorones.models.entity.GitHubRepo;
import dev.themajorones.models.entity.GitHubUser;
import dev.themajorones.models.entity.GitHubWorkflowRun;
import dev.themajorones.models.util.ValidationUtils;

@Service
public class GitHubArtifactServiceImpl implements GitHubArtifactService {
    private static final long DEFAULT_STALE_AFTER_MILLIS = Duration.ofMinutes(5).toMillis();

    private final GitHubApiClient gitHubApiClient;
    private final GitHubRepoSyncService gitHubRepoSyncService;
    private final GitHubRepoRepository gitHubRepoRepository;
    private final GitHubWorkflowRunRepository gitHubWorkflowRunRepository;
    private final GitHubArtifactRepository gitHubArtifactRepository;

    public GitHubArtifactServiceImpl(
            GitHubApiClient gitHubApiClient,
            GitHubRepoSyncService gitHubRepoSyncService,
            GitHubRepoRepository gitHubRepoRepository,
            GitHubWorkflowRunRepository gitHubWorkflowRunRepository,
            GitHubArtifactRepository gitHubArtifactRepository
    ) {
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubRepoSyncService = gitHubRepoSyncService;
        this.gitHubRepoRepository = gitHubRepoRepository;
        this.gitHubWorkflowRunRepository = gitHubWorkflowRunRepository;
        this.gitHubArtifactRepository = gitHubArtifactRepository;
    }

    @Override
    @Transactional
    public Optional<GitHubArtifact> findLatestArtifact(GitHubUser user, String repoFullName, Long workflowId, String headSha) {
        validateLookup(repoFullName, workflowId, headSha);

        GitHubRepo repo = gitHubRepoRepository.findByFullName(repoFullName)
            .or(() -> gitHubRepoSyncService.syncRepositoryByFullName(user, repoFullName))
            .orElseThrow(() -> new IllegalArgumentException("Repository not found or not accessible: " + repoFullName));

        long nowMillis = System.currentTimeMillis();
        long nowSeconds = Instant.ofEpochMilli(nowMillis).getEpochSecond();

        Optional<GitHubArtifact> localArtifact = findLatestLocalArtifact(repo, workflowId, headSha, nowSeconds);
        if (localArtifact.isPresent() && !isStale(repo, workflowId, headSha, nowMillis)) {
            return localArtifact;
        }

        syncRunsAndArtifacts(user, repo, workflowId, headSha);
        return findLatestLocalArtifact(repo, workflowId, headSha, nowSeconds);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadArtifact(GitHubUser user, GitHubArtifact artifact) {
        if (user == null || artifact == null) {
            throw new IllegalArgumentException("User and artifact are required");
        }
        String accessToken = ValidationUtils.requireText(user.getAccessToken(), "GitHub access token");
        GitHubRepo repo = artifact.getRepo();
        if (repo == null || repo.getOwner() == null) {
            throw new IllegalArgumentException("Artifact repository and owner are required");
        }

        return gitHubApiClient.downloadArtifact(
            accessToken,
            ValidationUtils.requireText(repo.getOwner().getLogin(), "GitHub owner login"),
            ValidationUtils.requireText(repo.getName(), "GitHub repository name"),
            ValidationUtils.requireId(artifact.getGithubArtifactId(), "GitHub artifact id")
        );
    }

    private void syncRunsAndArtifacts(GitHubUser user, GitHubRepo repo, Long workflowId, String headSha) {
        long syncedAt = System.currentTimeMillis();
        List<GitHubWorkflowRunResponse> workflowRuns = gitHubApiClient.getWorkflowRuns(
            user.getAccessToken(),
            repo.getOwner().getLogin(),
            repo.getName()
        );
        List<GitHubWorkflowRunResponse> matchingRuns = (workflowRuns == null ? List.<GitHubWorkflowRunResponse>of() : workflowRuns).stream()
            .filter(run -> matches(run, workflowId, headSha))
            .toList();

        for (GitHubWorkflowRunResponse runResponse : matchingRuns) {
            GitHubWorkflowRun run = upsertRun(repo, runResponse, syncedAt);
            List<GitHubArtifactResponse> artifacts = gitHubApiClient.getArtifacts(
                    user.getAccessToken(),
                    repo.getOwner().getLogin(),
                    repo.getName(),
                    run.getGithubRunId()
            );
            for (GitHubArtifactResponse artifactResponse : artifacts == null ? List.<GitHubArtifactResponse>of() : artifacts) {
                upsertArtifact(repo, run, artifactResponse);
            }
        }
    }

    private GitHubWorkflowRun upsertRun(GitHubRepo repo, GitHubWorkflowRunResponse response, long syncedAt) {
        Long githubRunId = ValidationUtils.requireId(response.getId(), "GitHub workflow run id");
        Long workflowId = ValidationUtils.requireId(response.getWorkflowId(), "GitHub workflow id");
        String headSha = ValidationUtils.requireText(response.getHeadSha(), "GitHub workflow head SHA");
        String status = ValidationUtils.requireText(response.getStatus(), "GitHub workflow status");

        GitHubWorkflowRun run = gitHubWorkflowRunRepository.findByGithubRunId(githubRunId)
            .orElseGet(GitHubWorkflowRun::new);

        return gitHubWorkflowRunRepository.save(run
            .setGithubRunId(githubRunId)
            .setRepo(repo)
            .setWorkflowId(workflowId)
            .setHeadSha(headSha)
            .setStatus(status)
            .setSyncedAt(syncedAt));
    }

    private GitHubArtifact upsertArtifact(GitHubRepo repo, GitHubWorkflowRun run, GitHubArtifactResponse response) {
        Long githubArtifactId = ValidationUtils.requireId(response.getId(), "GitHub artifact id");
        String name = ValidationUtils.requireText(response.getName(), "GitHub artifact name");
        Long sizeInBytes = ValidationUtils.requireId(response.getSizeInBytes(), "GitHub artifact size");
        Long expiresAt = response.getExpiresAt() == null ? null : response.getExpiresAt().getEpochSecond();
        if (expiresAt == null) {
            throw new IllegalStateException("GitHub artifact expiration timestamp is required");
        }

        GitHubArtifact artifact = gitHubArtifactRepository.findByGithubArtifactId(githubArtifactId)
            .orElseGet(GitHubArtifact::new);

        return gitHubArtifactRepository.save(artifact
            .setGithubArtifactId(githubArtifactId)
            .setRepo(repo)
            .setWorkflowRun(run)
            .setName(name)
            .setSizeInBytes(sizeInBytes)
            .setExpiresAt(expiresAt));
    }

    private Optional<GitHubArtifact> findLatestLocalArtifact(GitHubRepo repo, Long workflowId, String headSha, long nowSeconds) {
        List<GitHubWorkflowRun> runs = findMatchingRuns(repo, workflowId, headSha);
        for (GitHubWorkflowRun run : runs) {
            Optional<GitHubArtifact> artifact = gitHubArtifactRepository
                .findFirstByWorkflowRunAndExpiresAtGreaterThanEqualOrderByExpiresAtDesc(run, nowSeconds);
            if (artifact.isPresent()) {
                return artifact;
            }
        }
        return Optional.empty();
    }

    private boolean isStale(GitHubRepo repo, Long workflowId, String headSha, long nowMillis) {
        return findMatchingRuns(repo, workflowId, headSha).stream()
            .map(GitHubWorkflowRun::getSyncedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .map(lastSyncedAt -> lastSyncedAt < (nowMillis - DEFAULT_STALE_AFTER_MILLIS))
            .orElse(true);
    }

    private List<GitHubWorkflowRun> findMatchingRuns(GitHubRepo repo, Long workflowId, String headSha) {
        if (workflowId != null && headSha != null && !headSha.isBlank()) {
            return gitHubWorkflowRunRepository.findAllByRepoAndWorkflowIdAndHeadShaOrderByGithubRunIdDesc(
                repo,
                workflowId,
                headSha
            );
        }
        if (workflowId != null) {
            return gitHubWorkflowRunRepository.findAllByRepoAndWorkflowIdOrderByGithubRunIdDesc(repo, workflowId);
        }
        if (headSha == null || headSha.isBlank()) {
            return List.of();
        }
        return gitHubWorkflowRunRepository.findAllByRepoAndHeadShaOrderByGithubRunIdDesc(repo, headSha.trim());
    }

    private boolean matches(GitHubWorkflowRunResponse run, Long workflowId, String headSha) {
        if (workflowId != null && !workflowId.equals(run.getWorkflowId())) {
            return false;
        }
        if (headSha != null && !headSha.isBlank()) {
            return headSha.trim().equals(run.getHeadSha());
        }
        return true;
    }

    private void validateLookup(String repoFullName, Long workflowId, String headSha) {
        if (repoFullName == null || repoFullName.isBlank()) {
            throw new IllegalArgumentException("repoFullName is required");
        }
        if (workflowId == null && (headSha == null || headSha.isBlank())) {
            throw new IllegalArgumentException("Either workflowId or headSha is required");
        }
    }

}
