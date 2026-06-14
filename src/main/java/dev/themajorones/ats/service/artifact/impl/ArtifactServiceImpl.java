package dev.themajorones.ats.service.artifact.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import dev.themajorones.ats.dto.artifact.ArtifactResponse;
import dev.themajorones.ats.repository.AndroidRepository;
import dev.themajorones.ats.repository.ArtifactRepository;
import dev.themajorones.ats.repository.TaskLogRepository;
import dev.themajorones.ats.service.artifact.ArtifactKeyFactory;
import dev.themajorones.ats.service.github.GitHubApiClient;
import dev.themajorones.ats.service.artifact.ArtifactDownload;
import dev.themajorones.ats.service.artifact.ArtifactService;
import dev.themajorones.ats.service.storage.ArtifactStorageClient;
import dev.themajorones.ats.service.storage.ArtifactStorageObject;
import dev.themajorones.ats.service.resource.AndroidService;
import dev.themajorones.models.entity.Artifact;
import dev.themajorones.models.entity.ArtifactSource;
import dev.themajorones.models.entity.GitHubRepo;
import dev.themajorones.models.entity.GitHubUser;
import dev.themajorones.models.entity.TaskLog;
import dev.themajorones.models.constants.RabbitMqConstant;
import dev.themajorones.models.constants.TaskLogConstant;
import dev.themajorones.models.dto.InstallApkRequest;
import dev.themajorones.models.dto.TaskCommandEnvelope;
import dev.themajorones.models.util.JsonUtils;
import dev.themajorones.models.util.ValidationUtils;
import tools.jackson.databind.ObjectMapper;

@Service
public class ArtifactServiceImpl implements ArtifactService {

    private static final BigDecimal BYTES_PER_MEGABYTE = BigDecimal.valueOf(1024L * 1024L);
    private static final DateTimeFormatter DISPLAY_NAME_SUFFIX_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String APK_CONTENT_TYPE = "application/vnd.android.package-archive";

    private final ArtifactRepository artifactRepository;
    private final AndroidRepository androidRepository;
    private final TaskLogRepository taskLogRepository;
    private final ArtifactStorageClient artifactStorageClient;
    private final GitHubApiClient gitHubApiClient;
    private final AndroidService androidService;
    private final RabbitOperations rabbitOperations;
    private final ObjectMapper objectMapper;

    public ArtifactServiceImpl(
        ArtifactRepository artifactRepository,
        AndroidRepository androidRepository,
        TaskLogRepository taskLogRepository,
        ArtifactStorageClient artifactStorageClient,
        GitHubApiClient gitHubApiClient,
        AndroidService androidService,
        RabbitOperations rabbitOperations,
        ObjectMapper objectMapper
    ) {
        this.artifactRepository = artifactRepository;
        this.androidRepository = androidRepository;
        this.taskLogRepository = taskLogRepository;
        this.artifactStorageClient = artifactStorageClient;
        this.gitHubApiClient = gitHubApiClient;
        this.androidService = androidService;
        this.rabbitOperations = rabbitOperations;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArtifactResponse> listArtifacts(ArtifactSource source) {
        List<Artifact> artifacts;
        if (source == null) {
            artifacts = artifactRepository.findAllByOrderByIdDesc();
        } else {
            artifacts = artifactRepository.findAllBySourceOrderByIdDesc(source);
        }
        return artifacts.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ArtifactResponse uploadArtifact(String name, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("APK file is required");
        }
        String originalFileName = ValidationUtils.requireText(file.getOriginalFilename(), "APK file name");
        if (!originalFileName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            throw new IllegalArgumentException("Only APK files are supported");
        }

        String normalizedName = StringUtils.hasText(name)
            ? name.trim()
            : stripApkSuffix(originalFileName);
        String displayName = buildDisplayName(normalizedName);
        if (artifactRepository.existsByNameIgnoreCase(displayName)) {
            throw new IllegalStateException("Artifact display name already exists: " + displayName);
        }

        Artifact artifact = artifactRepository.saveAndFlush(new Artifact()
            .setSource(ArtifactSource.UPLOAD)
            .setName(displayName)
            .setSize(toMegabytes(file.getSize()))
            .setOriginalFileName(originalFileName)
            .setContentType(defaultContentType(file.getContentType())));

        artifact.setStorageKey(ArtifactKeyFactory.buildKey(artifact));
        artifactRepository.save(artifact);

        try {
            artifactStorageClient.putObject(
                artifact.getStorageKey(),
                file.getInputStream(),
                file.getSize(),
                artifact.getContentType()
            );
            return toResponse(artifact);
        } catch (IOException ex) {
            if (StringUtils.hasText(artifact.getStorageKey())) {
                try {
                    artifactStorageClient.deleteObject(artifact.getStorageKey());
                } catch (RuntimeException ignored) {
                    // Best-effort cleanup only.
                }
            }
            artifactRepository.delete(artifact);
            throw new IllegalStateException("Unable to store artifact", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ArtifactDownload downloadArtifact(GitHubUser user, Integer artifactId) {
        Artifact artifact = artifactRepository.findById(artifactId)
            .orElseThrow(() -> new IllegalArgumentException("Artifact was not found"));

        String fileName = ArtifactKeyFactory.downloadFileName(artifact);
        String contentType = StringUtils.hasText(artifact.getContentType())
            ? artifact.getContentType()
            : APK_CONTENT_TYPE;

        if (StringUtils.hasText(artifact.getStorageKey())) {
            ArtifactStorageObject stored = artifactStorageClient.getObject(artifact.getStorageKey());
            return new ArtifactDownload(stored.inputStream(), stored.contentLength(), defaultContentType(stored.contentType()), fileName);
        }

        if (artifact.getSource() == ArtifactSource.ARTIFACT && artifact.getGithubArtifactId() != null) {
            byte[] bytes = downloadLegacyGithubArtifact(user, artifact);
            return new ArtifactDownload(new ByteArrayInputStream(bytes), bytes.length, contentType, fileName);
        }

        throw new IllegalStateException("Artifact file is not available");
    }

    @Override
    @Transactional
    public void deleteArtifact(Integer artifactId) {
        Artifact artifact = artifactRepository.findById(artifactId)
            .orElseThrow(() -> new IllegalArgumentException("Artifact was not found"));
        if (StringUtils.hasText(artifact.getStorageKey())) {
            try {
                artifactStorageClient.deleteObject(artifact.getStorageKey());
            } catch (RuntimeException ignored) {
                // Best-effort cleanup only. The database row still gets removed.
            }
        }
        artifactRepository.delete(artifact);
    }

    @Override
    @Transactional
    public TaskLog installArtifact(Integer artifactId, Integer androidId) {
        Artifact artifact = artifactRepository.findById(artifactId)
            .orElseThrow(() -> new IllegalArgumentException("Artifact was not found"));
        if (!StringUtils.hasText(artifact.getStorageKey())) {
            throw new IllegalStateException("Artifact file is not available for install");
        }
        if (androidId == null) {
            throw new IllegalArgumentException("Android device is required");
        }

        var android = androidRepository.findById(androidId)
            .orElseThrow(() -> new IllegalArgumentException("Android device was not found"));
        String androidStatus = androidService.checkHealth(androidId);
        if (!"CONNECTED".equals(androidStatus) && !"RUNNING".equals(androidStatus)) {
            throw new IllegalStateException("Android device is not active: " + androidStatus);
        }
        if (!StringUtils.hasText(android.getAdbHost()) || android.getAdbPort() == null) {
            throw new IllegalStateException("Android device does not have an ADB address");
        }

        InstallApkRequest request = new InstallApkRequest()
            .setArtifactId(artifactId)
            .setAndroidId(androidId);

        TaskLog taskLog = taskLogRepository.save(new TaskLog()
            .setType(TaskLogConstant.Type.INSTALL_APK)
            .setStatus(TaskLogConstant.Status.PENDING)
            .setContent(JsonUtils.writeJson(objectMapper, request, "Unable to serialize install request")));

        TaskCommandEnvelope envelope = new TaskCommandEnvelope()
            .setTaskLogId(taskLog.getId())
            .setType(TaskLogConstant.Type.INSTALL_APK)
            .setContent(taskLog.getContent());

        rabbitOperations.convertAndSend(
            RabbitMqConstant.DIRECT_EXCHANGE,
            RabbitMqConstant.Queue.Artifact.ROUTING_KEY,
            JsonUtils.writeJson(objectMapper, envelope, "Unable to serialize JSON")
        );

        taskLog.setStatus(TaskLogConstant.Status.QUEUED);
        return taskLogRepository.save(taskLog);
    }

    private byte[] downloadLegacyGithubArtifact(GitHubUser user, Artifact artifact) {
        if (user == null) {
            throw new IllegalArgumentException("GitHub user is required to download legacy artifacts");
        }
        String accessToken = ValidationUtils.requireText(user.getAccessToken(), "GitHub access token");
        GitHubRepo repo = artifact.getRepo();
        if (repo == null || repo.getOwner() == null) {
            throw new IllegalArgumentException("Legacy artifact repository and owner are required");
        }
        return gitHubApiClient.downloadArtifact(
            accessToken,
            ValidationUtils.requireText(repo.getOwner().getLogin(), "GitHub owner login"),
            ValidationUtils.requireText(repo.getName(), "GitHub repository name"),
            ValidationUtils.requireId(artifact.getGithubArtifactId(), "GitHub artifact id")
        );
    }

    private ArtifactResponse toResponse(Artifact artifact) {
        Integer repoId = artifact.getRepo() == null ? null : artifact.getRepo().getId();
        String repoFullName = artifact.getRepo() == null ? null : artifact.getRepo().getFullName();
        Long workflowRunId = artifact.getWorkflowRun() == null ? null : artifact.getWorkflowRun().getGithubRunId();
        Long workflowId = artifact.getWorkflowRun() == null ? null : artifact.getWorkflowRun().getWorkflowId();
        String headSha = artifact.getWorkflowRun() == null ? null : artifact.getWorkflowRun().getHeadSha();
        return new ArtifactResponse(
            artifact.getId(),
            artifact.getSource() == null ? null : artifact.getSource().name(),
            artifact.getName(),
            artifact.getSize(),
            artifact.getGithubArtifactId(),
            repoId,
            repoFullName,
            workflowRunId,
            workflowId,
            headSha,
            artifact.getStorageKey(),
            artifact.getOriginalFileName(),
            artifact.getContentType()
        );
    }

    private BigDecimal toMegabytes(long bytes) {
        return BigDecimal.valueOf(bytes)
            .divide(BYTES_PER_MEGABYTE, 2, RoundingMode.HALF_UP);
    }

    private String defaultContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : APK_CONTENT_TYPE;
    }

    private String stripApkSuffix(String value) {
        if (!StringUtils.hasText(value)) {
            return "artifact";
        }
        String normalized = value.trim();
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private String buildDisplayName(String baseName) {
        String normalizedBase = StringUtils.hasText(baseName) ? baseName.trim() : "artifact";
        return normalizedBase + " - " + LocalDateTime.now().format(DISPLAY_NAME_SUFFIX_FORMAT);
    }
}
