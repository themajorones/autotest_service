package dev.themajorones.ats.service;

import java.util.List;

import dev.themajorones.ats.dto.androidtest.AndroidTestDetailResponse;
import dev.themajorones.ats.dto.androidtest.AndroidTestImageDownload;
import dev.themajorones.ats.dto.androidtest.AndroidTestStepHistoryResponse;
import dev.themajorones.models.dto.RunAndroidTestRequest;
import dev.themajorones.models.entity.TaskLog;

public interface AndroidTestService {

    TaskLog runAndroidTest(RunAndroidTestRequest request);

    AndroidTestDetailResponse getAndroidTest(Integer taskLogId);

    List<AndroidTestStepHistoryResponse> listAndroidTestSteps(Integer taskLogId);

    AndroidTestImageDownload getStepImage(Integer taskLogId, Integer stepNumber);
}
