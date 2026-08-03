package dev.themajorones.ats.dto.androidtest;

public record AndroidTestStepHistoryResponse(
    Integer id,
    Integer taskLogId,
    Integer stepNumber,
    Long startedAt,
    Long endedAt,
    String foreground,
    String uiHash,
    String uiContext,
    String visionProvider,
    String visionText,
    String action,
    String state,
    Integer targetElementId,
    Integer targetX,
    Integer targetY,
    Integer swipeX1,
    Integer swipeY1,
    Integer swipeX2,
    Integer swipeY2,
    Integer swipeDurationMs,
    String inputText,
    String reasoning,
    String decisionJson,
    String actionResult,
    String error,
    String imageStorageKey
) {
}
