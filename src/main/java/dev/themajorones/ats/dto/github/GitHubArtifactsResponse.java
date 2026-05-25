package dev.themajorones.ats.dto.github;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GitHubArtifactsResponse {

    private List<GitHubArtifactResponse> artifacts;
}
