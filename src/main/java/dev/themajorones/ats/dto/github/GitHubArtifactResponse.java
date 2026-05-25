package dev.themajorones.ats.dto.github;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GitHubArtifactResponse {

    private Long id;
    private String name;

    @JsonProperty("size_in_bytes")
    private Long sizeInBytes;

    @JsonProperty("expires_at")
    private Instant expiresAt;
}
