package dev.themajorones.ats.dto.auth;

import java.util.List;

public record AuthInfoResponse(
    Integer userId,
    Long githubId,
    String login,
    String displayName,
    List<String> organizations
) {
}
