package com.example.workflow.persistence;

import com.example.workflow.agents.TypedStageAgent;
import com.example.workflow.model.Contracts.WorkCompletedEvent;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public final class ValidationSubagent extends TypedStageAgent<WorkCompletedEvent, ValidatedEvent> {
    public ValidationSubagent() { super("validation_subagent", "Validate the versioned Kafka event"); }
    @Override public Class<WorkCompletedEvent> inputType() { return WorkCompletedEvent.class; }
    @Override public Class<ValidatedEvent> outputType() { return ValidatedEvent.class; }
    @Override protected ValidatedEvent execute(WorkCompletedEvent event) {
        if (event == null || event.schemaVersion() != 1 || event.completedAt() == null || event.result() == null
                || event.result().topic() == null || event.result().topic().isBlank()
                || event.result().summary() == null || event.result().summary().isBlank()
                || event.result().highlights() == null || event.result().highlights().isEmpty())
            throw new IllegalArgumentException("Invalid work completed event");
        UUID.fromString(event.eventId()); UUID.fromString(event.workId());
        return new ValidatedEvent(event);
    }
}
