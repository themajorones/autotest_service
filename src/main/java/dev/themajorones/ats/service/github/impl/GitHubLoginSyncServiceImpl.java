package dev.themajorones.ats.service.github.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.themajorones.ats.dto.github.GitHubOwnerResponse;
import dev.themajorones.ats.repository.GitHubOwnerMembershipRepository;
import dev.themajorones.ats.repository.GitHubOwnerRepository;
import dev.themajorones.ats.repository.GitHubUserRepository;
import dev.themajorones.ats.service.github.GitHubApiClient;
import dev.themajorones.ats.service.github.GitHubLoginSyncService;
import dev.themajorones.models.entity.GitHubOwner;
import dev.themajorones.models.entity.GitHubOwnerMembership;
import dev.themajorones.models.entity.GitHubOwnerType;
import dev.themajorones.models.entity.GitHubUser;
import dev.themajorones.models.util.ValidationUtils;

@Service
public class GitHubLoginSyncServiceImpl implements GitHubLoginSyncService {
    private final GitHubApiClient gitHubApiClient;
    private final GitHubOwnerRepository gitHubOwnerRepository;
    private final GitHubUserRepository gitHubUserRepository;
    private final GitHubOwnerMembershipRepository gitHubOwnerMembershipRepository;

    public GitHubLoginSyncServiceImpl(
            GitHubApiClient gitHubApiClient,
            GitHubOwnerRepository gitHubOwnerRepository,
            GitHubUserRepository gitHubUserRepository,
            GitHubOwnerMembershipRepository gitHubOwnerMembershipRepository
    ) {
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubOwnerRepository = gitHubOwnerRepository;
        this.gitHubUserRepository = gitHubUserRepository;
        this.gitHubOwnerMembershipRepository = gitHubOwnerMembershipRepository;
    }

    @Override
    @Transactional
    public GitHubUser syncAuthenticatedUser(
            String accessToken,
            Long accessTokenExpiresAt,
            String refreshToken,
            Long refreshTokenExpiresAt
    ) {
        long syncedAt = System.currentTimeMillis();
        GitHubOwnerResponse currentUser = requireOwnerResponse(gitHubApiClient.getCurrentUser(accessToken), "GitHub user profile");
        GitHubOwner owner = upsertOwner(currentUser, GitHubOwnerType.USER, syncedAt);

        GitHubUser user = gitHubUserRepository.findByOwnerGithubId(owner.getGithubId())
            .orElseGet(GitHubUser::new)
            .setOwner(owner)
            .setAccessToken(accessToken)
            .setAccessTokenExpiresAt(accessTokenExpiresAt)
            .setRefreshToken(refreshToken)
            .setRefreshTokenExpiresAt(refreshTokenExpiresAt)
            .setSyncedAt(syncedAt);
        GitHubUser savedUser = gitHubUserRepository.save(user);

        syncMemberships(savedUser, gitHubApiClient.getOrganizations(accessToken), syncedAt);
        return savedUser;
    }

    private void syncMemberships(GitHubUser user, List<GitHubOwnerResponse> organizations, long syncedAt) {
        Integer userId = user.getId();
        gitHubOwnerMembershipRepository.deleteAllByUserId(userId);

        Set<Long> seenOrgIds = new HashSet<>();
        for (GitHubOwnerResponse organization : organizations == null ? List.<GitHubOwnerResponse>of() : organizations) {
            GitHubOwnerResponse orgResponse = requireOwnerResponse(organization, "GitHub organization");
            if (!seenOrgIds.add(orgResponse.getId())) {
                continue;
            }

            GitHubOwner owner = upsertOwner(orgResponse, GitHubOwnerType.ORG, syncedAt);
            if (!gitHubOwnerMembershipRepository.existsByUserIdAndOwnerId(userId, owner.getId())) {
                gitHubOwnerMembershipRepository.save(new GitHubOwnerMembership()
                    .setUser(user)
                    .setOwner(owner)
                    .setSyncedAt(syncedAt));
            }
        }
    }

    private GitHubOwner upsertOwner(GitHubOwnerResponse response, GitHubOwnerType fallbackType, long syncedAt) {
        GitHubOwnerType ownerType = resolveOwnerType(response.getType(), fallbackType);
        String login = ValidationUtils.requireText(response.getLogin(), "GitHub owner login");
        Long githubId = ValidationUtils.requireId(response.getId(), "GitHub owner id");
        String displayName = response.getName() == null || response.getName().isBlank() ? login : response.getName().trim();

        GitHubOwner owner = gitHubOwnerRepository.findByGithubId(githubId)
            .or(() -> gitHubOwnerRepository.findByLogin(login))
            .orElseGet(GitHubOwner::new);

        return gitHubOwnerRepository.save(owner
            .setGithubId(githubId)
            .setLogin(login)
            .setType(ownerType)
            .setDisplayName(displayName)
            .setSyncedAt(syncedAt));
    }

    private GitHubOwnerType resolveOwnerType(String type, GitHubOwnerType fallbackType) {
        if (type == null || type.isBlank()) {
            return fallbackType;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("ORG")) {
            return GitHubOwnerType.ORG;
        }
        return GitHubOwnerType.USER;
    }

    private GitHubOwnerResponse requireOwnerResponse(GitHubOwnerResponse response, String description) {
        if (response == null) {
            throw new IllegalStateException(description + " was not returned by GitHub");
        }
        return response;
    }

}
