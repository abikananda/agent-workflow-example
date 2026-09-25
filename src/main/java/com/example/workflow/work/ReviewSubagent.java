package com.example.workflow.work;

import com.example.workflow.model.Contracts.Reviewed;
import com.example.workflow.model.Contracts.WorkRequest;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** Reviews the ADK model output and produces the typed completed result. */
public final class ReviewSubagent extends BaseAgent {
    private final WorkRequest request;
    private final AtomicReference<Reviewed> output;
    public ReviewSubagent(WorkRequest request, AtomicReference<Reviewed> output) {
        super("review_subagent", "Validate the generated summary", List.of(), List.of(), List.of());
        this.request = request; this.output = output;
    }
    @Override protected Flowable<Event> runAsyncImpl(InvocationContext context) {
        return Flowable.defer(() -> {
            Object draft = context.session().state().get("draft");
            if (!(draft instanceof String summary) || summary.isBlank() || summary.length() > 10000)
                throw new IllegalArgumentException("Missing or invalid model summary");
            if (!summary.toLowerCase(Locale.ROOT).contains(request.topic().trim().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Summary omits requested topic");
            output.set(new Reviewed(request.topic().trim(), summary.trim(),
                    List.of("Overview", "Key points", "Practical implications")));
            return Flowable.just(Event.builder().author(name()).build());
        });
    }
    @Override protected Flowable<Event> runLiveImpl(InvocationContext context) { return runAsyncImpl(context); }
}
