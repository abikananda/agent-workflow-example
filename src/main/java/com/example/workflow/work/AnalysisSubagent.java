package com.example.workflow.work;

import com.example.workflow.model.Contracts.WorkPlan;
import com.example.workflow.model.Contracts.WorkRequest;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;

/** Creates a deterministic plan and publishes the model prompt into ADK session state. */
public final class AnalysisSubagent extends BaseAgent {
    private final WorkRequest request;
    public AnalysisSubagent(WorkRequest request) {
        super("analysis_subagent", "Build a summary plan", List.of(), List.of(), List.of());
        this.request = request;
    }
    @Override protected Flowable<Event> runAsyncImpl(InvocationContext context) {
        return Flowable.defer(() -> {
            WorkPlan plan = new WorkPlan(request.topic().trim(),
                    List.of("Overview", "Key points", "Practical implications"));
            String prompt = plan.topic() + ": " + String.join(", ", plan.sections());
            return Flowable.just(Event.builder().author(name())
                    .actions(EventActions.builder().stateDelta(Map.of("planPrompt", prompt)).build()).build());
        });
    }
    @Override protected Flowable<Event> runLiveImpl(InvocationContext context) { return runAsyncImpl(context); }
}
