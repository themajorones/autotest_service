package dev.themajorones.ats.config;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import dev.themajorones.ats.service.auth.AppTokenService;
import dev.themajorones.ats.service.github.GitHubLoginSyncService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {
    private final GitHubLoginSyncService gitHubLoginSyncService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final AppTokenService appTokenService;

    public CustomOAuth2SuccessHandler(
        GitHubLoginSyncService gitHubLoginSyncService,
        OAuth2AuthorizedClientService authorizedClientService,
        AppTokenService appTokenService
    ) {
        this.gitHubLoginSyncService = gitHubLoginSyncService;
        this.authorizedClientService = authorizedClientService;
        this.appTokenService = appTokenService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2AuthorizedClient authorizedClient = authorizedClient(authentication);
        var user = gitHubLoginSyncService.syncAuthenticatedUser(
            authorizedClient.getAccessToken().getTokenValue(),
            authorizedClient.getAccessToken().getExpiresAt() != null ? authorizedClient.getAccessToken().getExpiresAt().toEpochMilli() : null,
            authorizedClient.getRefreshToken() != null ? authorizedClient.getRefreshToken().getTokenValue() : null,
            authorizedClient.getRefreshToken() != null && authorizedClient.getRefreshToken().getExpiresAt() != null ? authorizedClient.getRefreshToken().getExpiresAt().toEpochMilli() : null
        );
        String code = appTokenService.issueLoginCode(user);
        response.sendRedirect("/?authCode=" + code);
    }

    private OAuth2AuthorizedClient authorizedClient(Authentication authentication) {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        return authorizedClientService.loadAuthorizedClient(
            oauthToken.getAuthorizedClientRegistrationId(),
            oauthToken.getName()
        );
    }
}
