package com.example.workflow.db;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "work_result")
public class WorkResult {
    @Id @Column(name = "work_id") public String workId;
    @Column(name = "event_id", nullable = false, unique = true) public String eventId;
    @Column(nullable = false, columnDefinition = "TEXT") public String summary;
    @Column(name = "highlights_json", nullable = false, columnDefinition = "TEXT") public String highlightsJson;
    @Column(name = "completed_at", nullable = false) public Instant completedAt;
    protected WorkResult() {}
    public WorkResult(String workId, String eventId, String summary, String highlightsJson, Instant completedAt) {
        this.workId = workId; this.eventId = eventId; this.summary = summary;
        this.highlightsJson = highlightsJson; this.completedAt = completedAt;
    }
}
