package dev.themajorones.ats.dto.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GitHubRepoResponse {

    private Long id;
    private GitHubOwnerResponse owner;
    private String name;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("private")
    private boolean privateRepo;
}
