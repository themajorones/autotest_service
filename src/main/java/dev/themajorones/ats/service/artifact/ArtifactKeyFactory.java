package dev.themajorones.ats.service.artifact;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.util.StringUtils;

import dev.themajorones.models.entity.Artifact;
import dev.themajorones.models.entity.ArtifactSource;

public final class ArtifactKeyFactory {

    private ArtifactKeyFactory() {
    }

    public static String buildKey(Artifact artifact) {
        String source = artifact.getSource() == null ? ArtifactSource.ARTIFACT.name().toLowerCase(Locale.ROOT) : artifact.getSource().name().toLowerCase(Locale.ROOT);
        String logicalName = artifact.getOriginalFileName();
        if (!StringUtils.hasText(logicalName)) {
            logicalName = artifact.getName();
        }
        String slug = slugify(logicalName);
        if (!StringUtils.hasText(slug)) {
            slug = "artifact";
        }
        return "%s/%s/%s.apk".formatted(source, artifact.getId(), slug);
    }

    public static String downloadFileName(Artifact artifact) {
        String logicalName = artifact.getOriginalFileName();
        if (!StringUtils.hasText(logicalName)) {
            logicalName = artifact.getName();
        }
        String baseName = stripApkSuffix(logicalName);
        String slug = slugify(baseName);
        if (!StringUtils.hasText(slug)) {
            slug = "artifact";
        }
        return slug + ".apk";
    }

    private static String stripApkSuffix(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String normalized = value.trim();
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private static String slugify(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        return normalized;
    }
}
