package dev.themajorones.ats.service.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ArtifactStorageProperties.class)
public class ArtifactStorageConfig {

    @Bean
    public ArtifactStorageClient artifactStorageClient(ArtifactStorageProperties properties) {
        return new S3ArtifactStorageClient(properties);
    }

    @Bean
    public ImageStorageClient imageStorageClient(ArtifactStorageProperties properties) {
        return new S3ImageStorageClient(properties);
    }
}
