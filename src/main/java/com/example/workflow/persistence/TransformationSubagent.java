package com.example.workflow.persistence;

import com.example.workflow.agents.TypedStageAgent;
import com.example.workflow.db.WorkResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class TransformationSubagent extends TypedStageAgent<ValidatedEvent, MappedEvent> {
    private final ObjectMapper mapper;
    public TransformationSubagent(ObjectMapper mapper) {
        super("transformation_subagent", "Map a validated event into the MySQL entity");
        this.mapper = mapper;
    }
    @Override public Class<ValidatedEvent> inputType() { return ValidatedEvent.class; }
    @Override public Class<MappedEvent> outputType() { return MappedEvent.class; }
    @Override protected MappedEvent execute(ValidatedEvent input) {
        var event = input.event();
        try {
            return new MappedEvent(event, new WorkResult(event.workId(), event.eventId(),
                    event.result().summary(), mapper.writeValueAsString(event.result().highlights()), event.completedAt()));
        } catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid highlights", ex); }
    }
}
