package com.example.workflow.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class Contracts {
    private Contracts() {}
    public record WorkRequest(@NotBlank @Size(max = 300) String topic) {}
    public record WorkPlan(String topic, List<String> sections) {}
    public record Draft(WorkPlan plan, String summary) {}
    public record Reviewed(String topic, String summary, List<String> highlights) {}
    public record WorkCompletedEvent(String eventId, String workId, int schemaVersion,
                                     Instant completedAt, Reviewed result) {}
    public record WorkResponse(String workId, String status, Reviewed result, String error) {}
}
