package dev.themajorones.ats.service.github;

import java.util.Optional;
import dev.themajorones.models.entity.GitHubArtifact;
import dev.themajorones.models.entity.GitHubUser;

public interface GitHubArtifactService {

    Optional<GitHubArtifact> findLatestArtifact(GitHubUser user, String repoFullName, Long workflowId, String headSha);

    byte[] downloadArtifact(GitHubUser user, GitHubArtifact artifact);
}
