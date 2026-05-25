package dev.themajorones.ats.service.github.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.themajorones.ats.dto.github.GitHubOwnerResponse;
import dev.themajorones.ats.dto.github.GitHubRepoResponse;
import dev.themajorones.ats.repository.GitHubOwnerRepository;
import dev.themajorones.ats.repository.GitHubRepoRepository;
import dev.themajorones.ats.service.github.GitHubApiClient;
import dev.themajorones.ats.service.github.GitHubRepoSyncService;
import dev.themajorones.models.entity.GitHubOwner;
import dev.themajorones.models.entity.GitHubOwnerType;
import dev.themajorones.models.entity.GitHubRepo;
import dev.themajorones.models.entity.GitHubUser;
import dev.themajorones.models.util.ValidationUtils;

@Service
public class GitHubRepoSyncServiceImpl implements GitHubRepoSyncService {
    private final GitHubApiClient gitHubApiClient;
    private final GitHubOwnerRepository gitHubOwnerRepository;
    private final GitHubRepoRepository gitHubRepoRepository;

    public GitHubRepoSyncServiceImpl(
            GitHubApiClient gitHubApiClient,
            GitHubOwnerRepository gitHubOwnerRepository,
            GitHubRepoRepository gitHubRepoRepository
    ) {
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubOwnerRepository = gitHubOwnerRepository;
        this.gitHubRepoRepository = gitHubRepoRepository;
    }

    @Override
    @Transactional
    public List<GitHubRepo> syncAccessibleRepositories(GitHubUser user) {
        long syncedAt = System.currentTimeMillis();
        List<GitHubRepo> syncedRepos = new ArrayList<>();
        List<GitHubRepoResponse> repositories = gitHubApiClient.getAccessibleRepositories(user.getAccessToken());
        for (GitHubRepoResponse repoResponse : repositories == null ? List.<GitHubRepoResponse>of() : repositories) {
            syncedRepos.add(syncRepository(repoResponse, syncedAt));
        }
        return syncedRepos;
    }

    @Override
    @Transactional
    public Optional<GitHubRepo> syncRepositoryByFullName(GitHubUser user, String repoFullName) {
        return syncAccessibleRepositories(user).stream()
            .filter(repo -> repoFullName.equals(repo.getFullName()))
            .findFirst();
    }

    private GitHubRepo syncRepository(GitHubRepoResponse response, long syncedAt) {
        Long githubId = ValidationUtils.requireId(response.getId(), "GitHub repository id");
        String name = ValidationUtils.requireText(response.getName(), "GitHub repository name");
        String fullName = ValidationUtils.requireText(response.getFullName(), "GitHub repository full name");
        GitHubOwner owner = upsertOwner(response.getOwner(), syncedAt);

        GitHubRepo repo = gitHubRepoRepository.findByGithubId(githubId)
            .or(() -> gitHubRepoRepository.findByFullName(fullName))
            .orElseGet(GitHubRepo::new);

        return gitHubRepoRepository.save(repo
            .setGithubId(githubId)
            .setOwner(owner)
            .setName(name)
            .setFullName(fullName)
            .setPrivateRepo(response.isPrivateRepo()));
    }

    private GitHubOwner upsertOwner(GitHubOwnerResponse response, long syncedAt) {
        if (response == null) {
            throw new IllegalStateException("GitHub repository owner is required");
        }
        Long githubId = ValidationUtils.requireId(response.getId(), "GitHub owner id");
        String login = ValidationUtils.requireText(response.getLogin(), "GitHub owner login");
        String displayName = response.getName() == null || response.getName().isBlank() ? login : response.getName().trim();
        GitHubOwnerType type = "Organization".equalsIgnoreCase(response.getType()) ? GitHubOwnerType.ORG : GitHubOwnerType.USER;

        GitHubOwner owner = gitHubOwnerRepository.findByGithubId(githubId)
            .or(() -> gitHubOwnerRepository.findByLogin(login))
            .orElseGet(GitHubOwner::new);

        return gitHubOwnerRepository.save(owner
            .setGithubId(githubId)
            .setLogin(login)
            .setType(type)
            .setDisplayName(displayName)
            .setSyncedAt(syncedAt));
    }

}
