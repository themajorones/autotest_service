package dev.themajorones.ats.web.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import dev.themajorones.ats.dto.androidtest.AndroidTestDetailResponse;
import dev.themajorones.ats.dto.androidtest.AndroidTestImageDownload;
import dev.themajorones.ats.dto.androidtest.AndroidTestStepHistoryResponse;
import dev.themajorones.ats.service.AndroidTestService;
import dev.themajorones.models.dto.RunAndroidTestRequest;
import dev.themajorones.models.entity.TaskLog;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AndroidTestResource {

    private final AndroidTestService androidTestService;

    @PostMapping("/api/tests/android")
    public ResponseEntity<TaskLog> runAndroidTest(@RequestBody RunAndroidTestRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(androidTestService.runAndroidTest(request));
    }

    @GetMapping("/api/tests/android/{taskLogId}")
    public AndroidTestDetailResponse getAndroidTest(@PathVariable Integer taskLogId) {
        return androidTestService.getAndroidTest(taskLogId);
    }

    @GetMapping("/api/tests/android/{taskLogId}/steps")
    public List<AndroidTestStepHistoryResponse> listAndroidTestSteps(@PathVariable Integer taskLogId) {
        return androidTestService.listAndroidTestSteps(taskLogId);
    }

    @GetMapping("/api/tests/android/{taskLogId}/steps/{stepNumber}/image")
    public ResponseEntity<StreamingResponseBody> getAndroidTestStepImage(
        @PathVariable Integer taskLogId,
        @PathVariable Integer stepNumber
    ) {
        AndroidTestImageDownload download = androidTestService.getStepImage(taskLogId, stepNumber);
        StreamingResponseBody body = outputStream -> {
            try (var inputStream = download.inputStream()) {
                inputStream.transferTo(outputStream);
            }
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG);
        if (download.contentLength() >= 0) {
            builder.contentLength(download.contentLength());
        }
        return builder.body(body);
    }
}
