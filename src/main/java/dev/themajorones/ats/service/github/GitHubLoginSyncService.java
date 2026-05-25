package dev.themajorones.ats.service.github;

import dev.themajorones.models.entity.GitHubUser;

public interface GitHubLoginSyncService {

    GitHubUser syncAuthenticatedUser(
        String accessToken,
        Long accessTokenExpiresAt,
        String refreshToken,
        Long refreshTokenExpiresAt
    );
}
