package com.example.workflow.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;

/** Base for deterministic ADK subagents with explicit typed input and output. */
public abstract class TypedStageAgent<I, O> extends BaseAgent {
    private Map<String, Object> scope;
    protected TypedStageAgent(String name, String description) {
        super(name, description, List.of(), List.of(), List.of());
    }
    public abstract Class<I> inputType();
    public abstract Class<O> outputType();
    protected abstract O execute(I input);

    final void bind(Map<String, Object> scope) {
        if (this.scope != null) throw new IllegalStateException("Agent already registered: " + name());
        this.scope = scope;
    }
    @Override protected final Flowable<Event> runAsyncImpl(InvocationContext context) {
        return Flowable.defer(() -> {
            String id = context.session().id();
            I input = inputType().cast(scope.get(id));
            O output = outputType().cast(execute(input));
            scope.put(id, output);
            return Flowable.just(Event.builder().author(name()).build());
        });
    }
    @Override protected final Flowable<Event> runLiveImpl(InvocationContext context) { return runAsyncImpl(context); }
}
