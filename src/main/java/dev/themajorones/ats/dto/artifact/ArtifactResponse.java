package dev.themajorones.ats.dto.artifact;

import java.math.BigDecimal;

public record ArtifactResponse(
    Integer id,
    String source,
    String name,
    BigDecimal size,
    Long githubArtifactId,
    Integer repoId,
    String repoFullName,
    Long workflowRunId,
    Long workflowId,
    String headSha,
    String storageKey,
    String originalFileName,
    String contentType
) {
}
