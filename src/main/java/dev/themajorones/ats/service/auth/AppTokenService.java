package dev.themajorones.ats.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.themajorones.ats.dto.auth.AuthInfoResponse;
import dev.themajorones.ats.dto.auth.AuthTokenResponse;
import dev.themajorones.ats.entity.AppAuthCode;
import dev.themajorones.ats.entity.AppRefreshToken;
import dev.themajorones.ats.repository.AppAuthCodeRepository;
import dev.themajorones.ats.repository.AppRefreshTokenRepository;
import dev.themajorones.ats.repository.GitHubOwnerMembershipRepository;
import dev.themajorones.ats.repository.GitHubUserRepository;
import dev.themajorones.ats.security.jwt.AppPrincipal;
import dev.themajorones.models.entity.GitHubOwnerMembership;
import dev.themajorones.models.entity.GitHubUser;

@Service
public class AppTokenService {

    private final JwtEncoder jwtEncoder;
    private final AppAuthCodeRepository authCodeRepository;
    private final AppRefreshTokenRepository refreshTokenRepository;
    private final GitHubOwnerMembershipRepository membershipRepository;
    private final GitHubUserRepository userRepository;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final Duration loginCodeTtl;
    private final String issuer;
    private final Clock clock;

    public AppTokenService(
        JwtEncoder jwtEncoder,
        AppAuthCodeRepository authCodeRepository,
        AppRefreshTokenRepository refreshTokenRepository,
        GitHubOwnerMembershipRepository membershipRepository,
        GitHubUserRepository userRepository,
        @Value("${security.jwt.access-token-ttl}") Duration accessTokenTtl,
        @Value("${security.jwt.refresh-token-ttl}") Duration refreshTokenTtl,
        @Value("${security.jwt.login-code-ttl}") Duration loginCodeTtl,
        @Value("${security.jwt.issuer}") String issuer
    ) {
        this.jwtEncoder = jwtEncoder;
        this.authCodeRepository = authCodeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.loginCodeTtl = loginCodeTtl;
        this.issuer = issuer;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public String issueLoginCode(GitHubUser user) {
        Instant now = Instant.now(clock);
        AppAuthCode code = new AppAuthCode()
            .setCode(randomToken())
            .setUserId(user.getId())
            .setCreatedAt(now.toEpochMilli())
            .setExpiresAt(now.plus(loginCodeTtl).toEpochMilli());
        return authCodeRepository.save(code).getCode();
    }

    @Transactional
    public AuthTokenResponse exchangeLoginCode(String codeValue) {
        AppAuthCode authCode = requireActiveCode(codeValue);
        if (authCode.getConsumedAt() != null) {
            throw new IllegalArgumentException("Login code has already been used");
        }

        Instant now = Instant.now(clock);
        authCode.setConsumedAt(now.toEpochMilli());
        authCodeRepository.save(authCode);
        Integer userId = authCode.getUserId();
        refreshTokenRepository.revokeAllActiveByUserId(userId, now.toEpochMilli());
        return issueTokens(requireUser(userId), now);
    }

    @Transactional
    public AuthTokenResponse refresh(String refreshTokenValue) {
        AppRefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
            .orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid"));

        Instant now = Instant.now(clock);
        if (refreshToken.getRevokedAt() != null) {
            throw new IllegalArgumentException("Refresh token has been revoked");
        }
        if (refreshToken.getExpiresAt() <= now.toEpochMilli()) {
            throw new IllegalArgumentException("Refresh token has expired");
        }

        refreshToken.setRevokedAt(now.toEpochMilli());
        refreshTokenRepository.save(refreshToken);
        return issueTokens(requireUser(refreshToken.getUserId()), now);
    }

    @Transactional(readOnly = true)
    public AuthInfoResponse buildAuthInfo(GitHubUser user) {
        AppPrincipal principal = buildPrincipal(user);
        return new AuthInfoResponse(
            principal.userId(),
            principal.githubId(),
            principal.login(),
            principal.displayName(),
            principal.organizations()
        );
    }

    @Transactional(readOnly = true)
    public AppPrincipal buildPrincipal(GitHubUser user) {
        List<String> organizations = membershipRepository.findAllByUserId(user.getId()).stream()
            .map(GitHubOwnerMembership::getOwner)
            .map(owner -> owner.getLogin())
            .sorted()
            .toList();
        return AppPrincipal.from(user, organizations);
    }

    private AuthTokenResponse issueTokens(GitHubUser user, Instant now) {
        AppPrincipal principal = buildPrincipal(user);
        Instant accessTokenExpiresAt = now.plus(accessTokenTtl);
        Instant refreshTokenExpiresAt = now.plus(refreshTokenTtl);

        String accessToken = jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), jwtClaims(principal, now, accessTokenExpiresAt))
        ).getTokenValue();
        String refreshTokenValue = randomToken();
        refreshTokenRepository.save(new AppRefreshToken()
            .setToken(refreshTokenValue)
            .setUserId(user.getId())
            .setIssuedAt(now.toEpochMilli())
            .setExpiresAt(refreshTokenExpiresAt.toEpochMilli()));

        return new AuthTokenResponse(
            "Bearer",
            accessToken,
            accessTokenExpiresAt.toEpochMilli(),
            refreshTokenValue,
            refreshTokenExpiresAt.toEpochMilli()
        );
    }

    private JwtClaimsSet jwtClaims(AppPrincipal principal, Instant issuedAt, Instant expiresAt) {
        return JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(String.valueOf(principal.githubId()))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim("userId", principal.userId())
            .claim("githubId", principal.githubId())
            .claim("login", principal.login())
            .claim("displayName", principal.displayName())
            .claim("orgs", principal.organizations())
            .claim("tokenType", "access")
            .build();
    }

    private AppAuthCode requireActiveCode(String codeValue) {
        AppAuthCode authCode = authCodeRepository.findByCode(codeValue)
            .orElseThrow(() -> new IllegalArgumentException("Login code is invalid"));
        Instant now = Instant.now(clock);
        if (authCode.getExpiresAt() <= now.toEpochMilli()) {
            throw new IllegalArgumentException("Login code has expired");
        }
        return authCode;
    }

    private GitHubUser requireUser(Integer userId) {
        return userRepository.findDetailedById(userId)
            .orElseThrow(() -> new IllegalStateException("GitHub user was not found"));
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
