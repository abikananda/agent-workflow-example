package com.example.workflow.persistence;

import com.example.workflow.agents.TypedStageAgent;
import com.example.workflow.db.WorkItemRepository;
import com.example.workflow.db.WorkResult;
import com.example.workflow.db.WorkResultRepository;
import org.springframework.stereotype.Component;

@Component
public final class StorageSubagent extends TypedStageAgent<MappedEvent, WorkResult> {
    private final WorkResultRepository results;
    private final WorkItemRepository items;
    public StorageSubagent(WorkResultRepository results, WorkItemRepository items) {
        super("storage_subagent", "Save the reviewed result idempotently");
        this.results = results; this.items = items;
    }
    @Override public Class<MappedEvent> inputType() { return MappedEvent.class; }
    @Override public Class<WorkResult> outputType() { return WorkResult.class; }
    @Override protected WorkResult execute(MappedEvent mapped) {
        var event = mapped.event();
        var item = items.findById(event.workId()).orElseThrow(() -> new IllegalArgumentException("Unknown workId"));
        var existing = results.findById(event.workId());
        if (existing.isPresent()) {
            if (!existing.get().eventId.equals(event.eventId()))
                throw new IllegalStateException("Conflicting event for workId");
            return existing.get();
        }
        WorkResult saved = results.saveAndFlush(mapped.entity());
        item.transition("SAVED", null);
        items.saveAndFlush(item);
        return saved;
    }
}
