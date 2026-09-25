package com.example.workflow.work;

import com.example.workflow.model.Contracts.*;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.model.chat.ChatModel;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Three ADK subagents: deterministic analysis, LLM execution, deterministic review. */
public class WorkAgent {
    private final ChatModel model;
    public WorkAgent(ChatModel model) { this.model = model; }

    public Reviewed execute(String workId, WorkRequest request) {
        AtomicReference<Reviewed> reviewed = new AtomicReference<>();
        List<BaseAgent> stages = new ArrayList<>();
        stages.add(new AnalysisSubagent(request));
        stages.add(LlmAgent.builder()
                .name("execution_subagent")
                .description("Generate a summary using Ollama through the ADK LangChain4j bridge")
                .model(LangChain4j.builder().chatModel(model).modelName("local-ollama").build())
                .instruction("Write a concise factual summary. Cover {planPrompt}. Include the topic name. "
                        + "Do not invent sources or statistics. Return plain text only.")
                .outputKey("draft")
                .build());
        stages.add(new ReviewSubagent(request, reviewed));
        // To add another agent, insert it here in the ordered list and define its state contract.
        var root = SequentialAgent.builder().name("work_agent").subAgents(stages).build();
        var runner = new InMemoryRunner(root);
        Session session = runner.sessionService().createSession(runner.appName(), workId).blockingGet();
        runner.runAsync(session.userId(), session.id(), Content.fromParts(Part.fromText(request.topic())),
                RunConfig.builder().build()).blockingForEach(event -> {});
        if (reviewed.get() == null) throw new IllegalStateException("Review subagent did not produce a result");
        return reviewed.get();
    }

    /** ADK Java has no separate NonLlmAgent class; extend BaseAgent for deterministic work. */
    public static final class AnalysisSubagent extends BaseAgent {
        private final WorkRequest request;
        public AnalysisSubagent(WorkRequest request) {
            super("analysis_subagent", "Build a summary plan", List.of(), List.of(), List.of());
            this.request = request;
        }
        @Override protected Flowable<Event> runAsyncImpl(InvocationContext context) {
            var plan = new WorkPlan(request.topic().trim(),
                    List.of("Overview", "Key points", "Practical implications"));
            String prompt = plan.topic() + ": " + String.join(", ", plan.sections());
            return Flowable.just(Event.builder().author(name())
                    .actions(EventActions.builder().stateDelta(Map.of("planPrompt", prompt)).build()).build());
        }
        @Override protected Flowable<Event> runLiveImpl(InvocationContext context) { return runAsyncImpl(context); }
    }

    public static final class ReviewSubagent extends BaseAgent {
        private final WorkRequest request;
        private final AtomicReference<Reviewed> output;
        public ReviewSubagent(WorkRequest request, AtomicReference<Reviewed> output) {
            super("review_subagent", "Validate the model summary", List.of(), List.of(), List.of());
            this.request = request; this.output = output;
        }
        @Override protected Flowable<Event> runAsyncImpl(InvocationContext context) {
            return Flowable.defer(() -> {
                Object draft = context.session().state().get("draft");
                if (!(draft instanceof String summary) || summary.isBlank() || summary.length() > 10000)
                    throw new IllegalArgumentException("Missing or invalid model summary");
                if (!summary.toLowerCase().contains(request.topic().trim().toLowerCase()))
                    throw new IllegalArgumentException("Summary omits requested topic");
                output.set(new Reviewed(request.topic().trim(), summary.trim(),
                        List.of("Overview", "Key points", "Practical implications")));
                return Flowable.just(Event.builder().author(name()).build());
            });
        }
        @Override protected Flowable<Event> runLiveImpl(InvocationContext context) { return runAsyncImpl(context); }
    }
}
