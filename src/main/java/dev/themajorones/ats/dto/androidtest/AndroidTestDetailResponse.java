package dev.themajorones.ats.dto.androidtest;

public record AndroidTestDetailResponse(
    Integer id,
    String status,
    String content,
    String result,
    Long startedAt,
    Long endedAt,
    Object request,
    Object summary,
    Integer stepCount
) {
}
