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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Three ADK subagents: deterministic analysis, LLM execution, deterministic review. */
public class WorkAgent {
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
        runner.runAsync(session.userId(), session.id(), Content.fromParts(Part.fromText(request.topic())),
                RunConfig.builder().build()).blockingForEach(event -> {});
        if (reviewed.get() == null) throw new IllegalStateException("Review subagent did not produce a result");
        return reviewed.get();
    }

}
