package dev.themajorones.ats.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import dev.themajorones.models.client.AdbClient;
import dev.themajorones.models.client.DockerClient;
import dev.themajorones.models.client.OllamaClient;

@Configuration
public class ConnectionClientConfig {

    @Bean
    public OllamaClient ollamaClient(RestClient.Builder restClientBuilder) {
        return new OllamaClient(restClientBuilder);
    }

    @Bean
    public DockerClient dockerClient(RestClient.Builder restClientBuilder) {
        return new DockerClient(restClientBuilder);
    }

    @Bean
    public AdbClient adbClient() {
        return new AdbClient();
    }
}
