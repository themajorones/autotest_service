package dev.themajorones.ats.web.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.themajorones.ats.dto.auth.AuthExchangeRequest;
import dev.themajorones.ats.dto.auth.AuthInfoResponse;
import dev.themajorones.ats.dto.auth.AuthRefreshRequest;
import dev.themajorones.ats.dto.auth.AuthTokenResponse;
import dev.themajorones.ats.security.jwt.AppPrincipal;
import dev.themajorones.ats.service.auth.AppTokenService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AppTokenService appTokenService;

    public AuthController(AppTokenService appTokenService) {
        this.appTokenService = appTokenService;
    }

    @GetMapping("/success")
    public String success(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "GitHub login successful";
        }
        return "GitHub login successful";
    }

    @GetMapping("/failure")
    public String failure(String error) {
        return "GitHub login failed: " + (error != null ? error : "Unknown error");
    }

    @GetMapping("/info")
    public ResponseEntity<AuthInfoResponse> userInfo(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal != null) {
            return ResponseEntity.ok(new AuthInfoResponse(
                principal.userId(),
                principal.githubId(),
                principal.login(),
                principal.displayName(),
                principal.organizations()
            ));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/exchange")
    public AuthTokenResponse exchange(@RequestBody AuthExchangeRequest request) {
        return appTokenService.exchangeLoginCode(request.code());
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@RequestBody AuthRefreshRequest request) {
        return appTokenService.refresh(request.refreshToken());
    }
}
