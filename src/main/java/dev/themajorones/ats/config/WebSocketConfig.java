package dev.themajorones.ats.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import dev.themajorones.ats.security.jwt.AppJwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;
    private final AppJwtAuthenticationConverter appJwtAuthenticationConverter;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor());
    }

    @Bean
    public ChannelInterceptor webSocketAuthInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = firstText(accessor.getFirstNativeHeader("Authorization"));
                    if (!StringUtils.hasText(token)) {
                        token = firstText(accessor.getFirstNativeHeader("authorization"));
                    }
                    if (!StringUtils.hasText(token) || !token.startsWith("Bearer ")) {
                        throw new IllegalArgumentException("Missing websocket bearer token");
                    }
                    Jwt jwt = jwtDecoder.decode(token.substring("Bearer ".length()).trim());
                    AbstractAuthenticationToken authentication = appJwtAuthenticationConverter.convert(jwt);
                    accessor.setUser(authentication);
                    return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
                }
                return message;
            }
        };
    }

    private String firstText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
