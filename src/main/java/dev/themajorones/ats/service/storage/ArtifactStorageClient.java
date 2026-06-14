package dev.themajorones.ats.service.storage;

import java.io.InputStream;

public interface ArtifactStorageClient {

    void ensureBucketExists();

    void putObject(String key, InputStream inputStream, long contentLength, String contentType);

    ArtifactStorageObject getObject(String key);

    void deleteObject(String key);
}
