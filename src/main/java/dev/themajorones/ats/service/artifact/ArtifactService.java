package dev.themajorones.ats.service.artifact;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import dev.themajorones.ats.dto.artifact.ArtifactResponse;
import dev.themajorones.models.entity.ArtifactSource;
import dev.themajorones.models.entity.GitHubUser;
import dev.themajorones.models.entity.TaskLog;

public interface ArtifactService {

    List<ArtifactResponse> listArtifacts(ArtifactSource source);

    ArtifactResponse uploadArtifact(String name, MultipartFile file);

    ArtifactDownload downloadArtifact(GitHubUser user, Integer artifactId);

    void deleteArtifact(Integer artifactId);

    TaskLog installArtifact(Integer artifactId, Integer androidId);
}
