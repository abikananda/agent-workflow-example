package com.example.workflow.db;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "work_item")
public class WorkItem {
    @Id public String id;
    @Column(nullable = false) public String topic;
    @Column(nullable = false) public String status;
    @Column(name = "error_message") public String errorMessage;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    protected WorkItem() {}
    public WorkItem(String id, String topic) {
        this.id = id; this.topic = topic; this.status = "RECEIVED";
        this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public void transition(String status, String error) {
        this.status = status; this.errorMessage = error == null ? null : error.substring(0, Math.min(1000, error.length()));
        this.updatedAt = Instant.now();
    }
}
