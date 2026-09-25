package com.example.workflow.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;

final class StepAgent<I, O> extends BaseAgent {
    private final TypedStep<I, O> step;
    private final Map<String, Object> scope;
    StepAgent(TypedStep<I, O> step, Map<String, Object> scope) {
        super(step.name(), step.name(), List.of(), List.of(), List.of());
        this.step = step; this.scope = scope;
    }
    @Override protected Flowable<Event> runAsyncImpl(InvocationContext ctx) {
        return Flowable.defer(() -> {
            String id = ctx.session().id();
            I input = step.inputType().cast(scope.get(id));
            O output = step.outputType().cast(step.apply(input));
            scope.put(id, output);
            return Flowable.just(Event.builder().author(name()).build());
        });
    }
    @Override protected Flowable<Event> runLiveImpl(InvocationContext ctx) { return runAsyncImpl(ctx); }
}
