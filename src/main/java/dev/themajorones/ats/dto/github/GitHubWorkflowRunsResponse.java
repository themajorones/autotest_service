package dev.themajorones.ats.dto.github;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GitHubWorkflowRunsResponse {

    @JsonProperty("workflow_runs")
    private List<GitHubWorkflowRunResponse> workflowRuns;
}
