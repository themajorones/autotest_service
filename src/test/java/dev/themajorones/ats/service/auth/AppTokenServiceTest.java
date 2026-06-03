package dev.themajorones.ats.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import dev.themajorones.ats.entity.AppAuthCode;
import dev.themajorones.ats.entity.AppRefreshToken;
import dev.themajorones.ats.repository.AppAuthCodeRepository;
import dev.themajorones.ats.repository.AppRefreshTokenRepository;
import dev.themajorones.ats.repository.GitHubOwnerMembershipRepository;
import dev.themajorones.ats.repository.GitHubUserRepository;
import dev.themajorones.models.entity.GitHubOwner;
import dev.themajorones.models.entity.GitHubOwnerMembership;
import dev.themajorones.models.entity.GitHubOwnerType;
import dev.themajorones.models.entity.GitHubUser;

@ExtendWith(MockitoExtension.class)
class AppTokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private AppAuthCodeRepository authCodeRepository;

    @Mock
    private AppRefreshTokenRepository refreshTokenRepository;

    @Mock
    private GitHubOwnerMembershipRepository membershipRepository;

    @Mock
    private GitHubUserRepository userRepository;

    private AppTokenService tokenService;
    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

        tokenService = new AppTokenService(
            jwtEncoder,
            authCodeRepository,
            refreshTokenRepository,
            membershipRepository,
            userRepository,
            Duration.ofMinutes(15),
            Duration.ofDays(30),
            Duration.ofMinutes(5),
            "ats"
        );
    }

    @Test
    void exchangeLoginCodeIssuesJwtAndRefreshToken() {
        GitHubUser user = gitHubUser();
        AppAuthCode authCode = new AppAuthCode()
            .setCode("login-code")
            .setUserId(user.getId())
            .setCreatedAt(1000L)
            .setExpiresAt(System.currentTimeMillis() + 60_000);

        when(authCodeRepository.findByCode("login-code")).thenReturn(java.util.Optional.of(authCode));
        when(authCodeRepository.save(any(AppAuthCode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.save(any(AppRefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.revokeAllActiveByUserId(anyInt(), anyLong())).thenReturn(0);
        when(membershipRepository.findAllByUserId(1)).thenReturn(List.of(membership(user, "openai")));
        when(userRepository.findDetailedById(1)).thenReturn(java.util.Optional.of(user));

        var response = tokenService.exchangeLoginCode("login-code");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();

        var jwt = jwtDecoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(user.getOwner().getGithubId()));
        assertThat(jwt.getClaimAsString("login")).isEqualTo("codex");
        assertThat(jwt.getClaimAsStringList("orgs")).containsExactly("openai");

        verify(authCodeRepository).save(authCode);
    }

    @Test
    void refreshRotatesRefreshTokenAndPreservesPrincipalClaims() {
        GitHubUser user = gitHubUser();
        AppRefreshToken existing = new AppRefreshToken()
            .setToken("refresh-token")
            .setUserId(user.getId())
            .setIssuedAt(1000L)
            .setExpiresAt(System.currentTimeMillis() + 60_000);

        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(java.util.Optional.of(existing));
        when(refreshTokenRepository.save(any(AppRefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.findAllByUserId(1)).thenReturn(List.of(membership(user, "openai")));
        when(userRepository.findDetailedById(1)).thenReturn(java.util.Optional.of(user));

        var response = tokenService.refresh("refresh-token");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotEqualTo("refresh-token");
        assertThat(existing.getRevokedAt()).isNotNull();
    }

    private GitHubUser gitHubUser() {
        GitHubOwner owner = new GitHubOwner()
            .setId(7)
            .setGithubId(1234L)
            .setLogin("codex")
            .setType(GitHubOwnerType.USER)
            .setDisplayName("Codex User")
            .setSyncedAt(1000L);

        return new GitHubUser()
            .setId(1)
            .setOwner(owner)
            .setAccessToken("github-token")
            .setAccessTokenExpiresAt(1234L)
            .setRefreshToken("refresh-token")
            .setRefreshTokenExpiresAt(5678L)
            .setSyncedAt(1000L);
    }

    private GitHubOwnerMembership membership(GitHubUser user, String login) {
        GitHubOwner owner = new GitHubOwner()
            .setId(8)
            .setGithubId(4321L)
            .setLogin(login)
            .setType(GitHubOwnerType.ORG)
            .setDisplayName(login)
            .setSyncedAt(1000L);

        return new GitHubOwnerMembership()
            .setUser(user)
            .setOwner(owner)
            .setSyncedAt(1000L);
    }
}
