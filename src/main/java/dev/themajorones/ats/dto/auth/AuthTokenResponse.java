package dev.themajorones.ats.dto.auth;

public record AuthTokenResponse(
    String tokenType,
    String accessToken,
    long accessTokenExpiresAt,
    String refreshToken,
    long refreshTokenExpiresAt
) {
}
