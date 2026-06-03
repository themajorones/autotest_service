package dev.themajorones.ats.security.jwt;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.List;

import dev.themajorones.models.entity.GitHubUser;

public record AppPrincipal(
    Integer userId,
    Long githubId,
    String login,
    String displayName,
    List<String> organizations
) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return login;
    }

    public static AppPrincipal from(GitHubUser user, List<String> organizations) {
        return new AppPrincipal(
            user.getId(),
            user.getOwner().getGithubId(),
            user.getOwner().getLogin(),
            user.getOwner().getDisplayName(),
            organizations == null ? List.of() : List.copyOf(organizations)
        );
    }
}
