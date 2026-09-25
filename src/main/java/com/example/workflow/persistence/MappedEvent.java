package com.example.workflow.persistence;

import com.example.workflow.db.WorkResult;
import com.example.workflow.model.Contracts.WorkCompletedEvent;

public record MappedEvent(WorkCompletedEvent event, WorkResult entity) {}
