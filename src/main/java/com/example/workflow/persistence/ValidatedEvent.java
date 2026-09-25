package com.example.workflow.persistence;

import com.example.workflow.model.Contracts.WorkCompletedEvent;

public record ValidatedEvent(WorkCompletedEvent event) {}
