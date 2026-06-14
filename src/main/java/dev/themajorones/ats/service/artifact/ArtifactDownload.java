package dev.themajorones.ats.service.artifact;

import java.io.InputStream;

public record ArtifactDownload(
    InputStream inputStream,
    long contentLength,
    String contentType,
    String fileName
) {
}
