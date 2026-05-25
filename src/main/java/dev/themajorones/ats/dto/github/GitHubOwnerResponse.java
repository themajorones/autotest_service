package dev.themajorones.ats.dto.github;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GitHubOwnerResponse {

    private Long id;
    private String login;
    private String name;
    private String type;
}
