package dev.themajorones.ats.service.storage;

import java.io.InputStream;

public record ArtifactStorageObject(
    InputStream inputStream,
    long contentLength,
    String contentType
) {
}
