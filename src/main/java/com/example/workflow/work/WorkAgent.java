package com.example.workflow.work;

import com.example.workflow.model.Contracts.*;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

/** Three ADK subagents: deterministic analysis, LLM execution, deterministic review. */
public class WorkAgent {
    private static final Logger log = LoggerFactory.getLogger(WorkAgent.class);
    private final ChatModel model;
    public WorkAgent(ChatModel model) { this.model = model; }

    public Reviewed execute(String workId, WorkRequest request) {
        AtomicReference<Reviewed> reviewed = new AtomicReference<>();
        List<BaseAgent> stages = new ArrayList<>();
        stages.add(new AnalysisSubagent(request));
        stages.add(new ExecutionSubagent(model).create());
        stages.add(new ReviewSubagent(request, reviewed));
        // To add another agent, insert it here in the ordered list and define its state contract.
        var root = SequentialAgent.builder().name("work_agent").subAgents(stages).build();
        var runner = new InMemoryRunner(root);
        Session session = runner.sessionService().createSession(runner.appName(), workId).blockingGet();
        try {
            runner.runAsync(session.userId(), session.id(), Content.fromParts(Part.fromText(request.topic())),
                    RunConfig.builder().build())
                    .doOnNext(event -> log.info("ADK stage event workId={} agent={} finalResponse={}",
                            workId, event.author(), event.finalResponse()))
                    .timeout(90, TimeUnit.SECONDS)
                    .doOnError(error -> log.error("ADK workflow failed workId={} sessionId={}",
                            workId, session.id(), error))
                    .blockingForEach(event -> {});
        } catch (RuntimeException error) {
            throw new IllegalStateException("ADK workflow failed for workId=" + workId
                    + ": " + rootCause(error).getMessage(), error);
        }
        if (reviewed.get() == null) throw new IllegalStateException("Review subagent did not produce a result");
        return reviewed.get();
    }
    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause;
    }
}
