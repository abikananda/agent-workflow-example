package com.example.workflow.agents;

import com.google.adk.agents.RunConfig;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class AdkExecutor<I, O> {
    private static final Logger log = LoggerFactory.getLogger(AdkExecutor.class);
    private final TypedPipeline<I, O> pipeline;
    public AdkExecutor(TypedPipeline<I, O> pipeline) { this.pipeline = pipeline; }
    public O execute(String workId, I input) {
        InMemoryRunner runner = new InMemoryRunner(pipeline.root());
        Session session = runner.sessionService().createSession(runner.appName(), workId).blockingGet();
        pipeline.start(session.id(), input);
        try {
            runner.runAsync(session.userId(), session.id(), Content.fromParts(Part.fromText(workId)),
                    RunConfig.builder().build())
                    .doOnNext(event -> log.info("ADK stage event workId={} eventId={} agent={} finalResponse={}",
                            workId, MDC.get("eventId"), event.author(), event.finalResponse()))
                    .doOnError(error -> log.error("ADK workflow failed workId={} eventId={} sessionId={}",
                            workId, MDC.get("eventId"), session.id(), error))
                    .blockingForEach(event -> {});
            return pipeline.finish(session.id());
        } finally {
            pipeline.discard(session.id());
        }
    }
}
