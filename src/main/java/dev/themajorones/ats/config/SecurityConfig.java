package dev.themajorones.ats.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import dev.themajorones.ats.security.CustomOAuth2FailureHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,
        CustomOAuth2SuccessHandler successHandler,
        CustomOAuth2FailureHandler failureHandler
    ) throws Exception {

        http.cors(cors -> cors.disable())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/auth/**",
                    "/health",
                    "/assets/**",
                    "/oauth2/**",
                    "/login/**",
                    "/actuator/**"
                ).permitAll()
                .requestMatchers(HttpMethod.POST, "/webhook/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptionHandling -> exceptionHandling
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.pathPattern("/api/**")
                )
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            );

        return http.build();
    }
}
