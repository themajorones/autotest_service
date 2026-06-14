package dev.themajorones.ats.web.rest;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import dev.themajorones.ats.dto.artifact.ArtifactResponse;
import dev.themajorones.models.dto.InstallApkRequest;
import dev.themajorones.ats.security.jwt.AppPrincipal;
import dev.themajorones.ats.service.artifact.ArtifactDownload;
import dev.themajorones.ats.service.artifact.ArtifactService;
import dev.themajorones.ats.repository.GitHubUserRepository;
import dev.themajorones.models.entity.ArtifactSource;
import dev.themajorones.models.entity.GitHubUser;
import dev.themajorones.models.entity.TaskLog;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/artifacts")
public class ArtifactResource {

    private static final MediaType APK_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.android.package-archive");

    private final ArtifactService artifactService;
    private final GitHubUserRepository userRepository;

    @GetMapping
    public List<ArtifactResponse> listArtifacts(@RequestParam(name = "source", required = false) String source) {
        return artifactService.listArtifacts(parseSource(source));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArtifactResponse> uploadArtifact(
        @RequestParam(name = "name", required = false) String name,
        @RequestParam(name = "file") MultipartFile file
    ) {
        ArtifactResponse response = artifactService.uploadArtifact(name, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<StreamingResponseBody> downloadArtifact(
        @PathVariable Integer id,
        @AuthenticationPrincipal AppPrincipal principal
    ) {
        GitHubUser user = principal == null ? null : userRepository.findDetailedById(principal.userId()).orElse(null);
        ArtifactDownload download = artifactService.downloadArtifact(user, id);
        StreamingResponseBody body = outputStream -> {
            try (var inputStream = download.inputStream()) {
                inputStream.transferTo(outputStream);
            }
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
            .contentType(parseContentType(download.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString());
        if (download.contentLength() >= 0) {
            builder.contentLength(download.contentLength());
        }
        return builder.body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArtifact(@PathVariable Integer id) {
        artifactService.deleteArtifact(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/install")
    public ResponseEntity<TaskLog> installArtifact(
        @PathVariable Integer id,
        @RequestBody InstallApkRequest request
    ) {
        Integer androidId = request == null ? null : request.getAndroidId();
        TaskLog taskLog = artifactService.installArtifact(id, androidId);
        return ResponseEntity.accepted().body(taskLog);
    }

    private ArtifactSource parseSource(String source) {
        if (source == null || source.isBlank() || "all".equalsIgnoreCase(source)) {
            return null;
        }
        return switch (source.trim().toLowerCase()) {
            case "upload", "uploaded" -> ArtifactSource.UPLOAD;
            case "artifact" -> ArtifactSource.ARTIFACT;
            default -> throw new IllegalArgumentException("Unknown artifact source: " + source);
        };
    }

    private MediaType parseContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return APK_MEDIA_TYPE;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            return APK_MEDIA_TYPE;
        }
    }
}
