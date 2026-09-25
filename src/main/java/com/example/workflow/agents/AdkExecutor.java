package com.example.workflow.agents;

import com.google.adk.agents.RunConfig;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

public final class AdkExecutor<I, O> {
    private final TypedPipeline<I, O> pipeline;
    public AdkExecutor(TypedPipeline<I, O> pipeline) { this.pipeline = pipeline; }
    public O execute(String workId, I input) {
        InMemoryRunner runner = new InMemoryRunner(pipeline.root());
        Session session = runner.sessionService().createSession(runner.appName(), workId).blockingGet();
        pipeline.start(session.id(), input);
        try {
            runner.runAsync(session.userId(), session.id(), Content.fromParts(Part.fromText(workId)),
                    RunConfig.builder().build()).blockingForEach(event -> {});
            return pipeline.finish(session.id());
        } finally {
            pipeline.discard(session.id());
        }
    }
}
