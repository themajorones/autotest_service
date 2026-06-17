package dev.themajorones.ats.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import dev.themajorones.ats.security.jwt.AppJwtAuthenticationConverter;

class WebSocketConfigTest {

    @Test
    void websocketAuthInterceptorAttachesAuthenticatedPrincipal() {
        JwtDecoder jwtDecoder = Mockito.mock(JwtDecoder.class);
        AppJwtAuthenticationConverter converter = Mockito.mock(AppJwtAuthenticationConverter.class);
        WebSocketConfig config = new WebSocketConfig(jwtDecoder, converter);
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claim("userId", 1)
            .claim("githubId", 2)
            .claim("login", "octocat")
            .claim("displayName", "Octo Cat")
            .claim("orgs", java.util.List.of())
            .issuedAt(java.time.Instant.now())
            .expiresAt(java.time.Instant.now().plusSeconds(60))
            .build();
        AbstractAuthenticationToken authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "octocat",
            "token",
            java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(converter.convert(any())).thenReturn(authentication);

        var interceptor = config.webSocketAuthInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(message, Mockito.mock(MessageChannel.class));

        assertThat(result).isNotNull();
        assertThat(StompHeaderAccessor.wrap(result).getUser()).isSameAs(authentication);
        verify(jwtDecoder).decode("token");
        verify(converter).convert(jwt);
    }
}
