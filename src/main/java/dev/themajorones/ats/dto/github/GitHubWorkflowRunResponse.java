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
public class GitHubWorkflowRunResponse {

    private Long id;

    @JsonProperty("workflow_id")
    private Long workflowId;

    @JsonProperty("head_sha")
    private String headSha;

    private String status;
}
