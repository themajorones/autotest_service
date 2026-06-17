package dev.themajorones.ats.dto.androidtest;

import java.io.InputStream;

public record AndroidTestImageDownload(
    InputStream inputStream,
    long contentLength,
    String contentType
) {
}
