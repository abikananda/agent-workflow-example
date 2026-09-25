package com.example.workflow.persistence;

import com.example.workflow.agents.*;
import com.example.workflow.db.*;
import com.example.workflow.model.Contracts.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.UUID;

@Configuration
public class PersistenceAgentConfig {
    record Validated(WorkCompletedEvent event) {}
    record Mapped(WorkCompletedEvent event, WorkResult entity) {}

    @Bean AdkExecutor<WorkCompletedEvent, WorkResult> persistenceAgent(
            WorkResultRepository results, WorkItemRepository items, ObjectMapper mapper) {
        TypedStep<WorkCompletedEvent, Validated> validation = new TypedStep<>() {
            public String name() { return "validation_subagent"; }
            public Class<WorkCompletedEvent> inputType() { return WorkCompletedEvent.class; }
            public Class<Validated> outputType() { return Validated.class; }
            public Validated apply(WorkCompletedEvent e) {
                if (e == null || e.schemaVersion() != 1 || e.completedAt() == null || e.result() == null
                        || e.result().summary() == null || e.result().summary().isBlank()
                        || e.result().highlights() == null || e.result().highlights().isEmpty())
                    throw new IllegalArgumentException("Invalid work completed event");
                UUID.fromString(e.eventId()); UUID.fromString(e.workId());
                return new Validated(e);
            }
        };
        TypedStep<Validated, Mapped> transformation = new TypedStep<>() {
            public String name() { return "transformation_subagent"; }
            public Class<Validated> inputType() { return Validated.class; }
            public Class<Mapped> outputType() { return Mapped.class; }
            public Mapped apply(Validated input) {
                var e = input.event();
                try {
                    return new Mapped(e, new WorkResult(e.workId(), e.eventId(), e.result().summary(),
                            mapper.writeValueAsString(e.result().highlights()), e.completedAt()));
                } catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid highlights", ex); }
            }
        };
        TypedStep<Mapped, WorkResult> storage = new TypedStep<>() {
            public String name() { return "storage_subagent"; }
            public Class<Mapped> inputType() { return Mapped.class; }
            public Class<WorkResult> outputType() { return WorkResult.class; }
            public WorkResult apply(Mapped mapped) {
                var e = mapped.event();
                WorkItem item = items.findById(e.workId()).orElseThrow(() -> new IllegalArgumentException("Unknown workId"));
                var existing = results.findById(e.workId());
                if (existing.isPresent()) {
                    if (!existing.get().eventId.equals(e.eventId())) throw new IllegalStateException("Conflicting event for workId");
                    return existing.get();
                }
                WorkResult saved = results.saveAndFlush(mapped.entity());
                item.transition("SAVED", null);
                items.saveAndFlush(item);
                return saved;
            }
        };
        return new AdkExecutor<>(TypedPipeline.builder("persistence_agent", WorkCompletedEvent.class)
                .then(validation).then(transformation).then(storage).build());
    }
}
