package dev.themajorones.ats.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

class AppJwtAuthenticationConverterTest {

    private final AppJwtAuthenticationConverter converter = new AppJwtAuthenticationConverter();

    @Test
    void convertsJwtClaimsIntoAppPrincipal() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claim("userId", 42)
            .claim("githubId", 1234L)
            .claim("login", "codex")
            .claim("displayName", "Codex User")
            .claim("orgs", List.of("openai", "platform"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900))
            .build();

        UsernamePasswordAuthenticationToken authentication = (UsernamePasswordAuthenticationToken) converter.convert(jwt);
        AppPrincipal principal = (AppPrincipal) authentication.getPrincipal();

        assertThat(principal.userId()).isEqualTo(42);
        assertThat(principal.githubId()).isEqualTo(1234L);
        assertThat(principal.login()).isEqualTo("codex");
        assertThat(principal.displayName()).isEqualTo("Codex User");
        assertThat(principal.organizations()).containsExactly("openai", "platform");
    }
}
