package dev.themajorones.ats.service.github;

import java.util.List;
import java.util.Optional;
import dev.themajorones.models.entity.GitHubRepo;
import dev.themajorones.models.entity.GitHubUser;

public interface GitHubRepoSyncService {

    List<GitHubRepo> syncAccessibleRepositories(GitHubUser user);

    Optional<GitHubRepo> syncRepositoryByFullName(GitHubUser user, String repoFullName);
}
