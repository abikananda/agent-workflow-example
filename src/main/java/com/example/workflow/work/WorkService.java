package com.example.workflow.work;

import com.example.workflow.db.*;
import com.example.workflow.model.Contracts.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class WorkService {
    private static final Logger log = LoggerFactory.getLogger(WorkService.class);
    private final WorkItemRepository items;
    private final WorkResultRepository results;
    private final WorkAgent agent;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;
    public WorkService(WorkItemRepository items, WorkResultRepository results, WorkAgent workAgent,
                       KafkaTemplate<String, String> kafka, ObjectMapper mapper, @Value("${app.topic}") String topic) {
        this.items = items; this.results = results; this.agent = workAgent;
        this.kafka = kafka; this.mapper = mapper; this.topic = topic;
    }
    public String submit(WorkRequest request) {
        String id = UUID.randomUUID().toString();
        items.saveAndFlush(new WorkItem(id, request.topic()));
        return id;
    }
    @Async
    public CompletableFuture<Void> process(String id, WorkRequest request) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("workId", id)) {
            update(id, "PROCESSING", null);
            Reviewed reviewed = agent.execute(id, request);
            String eventId = UUID.randomUUID().toString();
            try (MDC.MDCCloseable eventContext = MDC.putCloseable("eventId", eventId)) {
                String json = mapper.writeValueAsString(new WorkCompletedEvent(eventId, id, 1, Instant.now(), reviewed));
                kafka.send(topic, id, json).get(30, TimeUnit.SECONDS);
                // The consumer can save before this completes. Never downgrade SAVED.
                items.markPublished(id, Instant.now());
                log.info("Published completed work");
            }
        } catch (Exception ex) {
            Throwable root = ex;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.error("Work processing failed workId={} causeType={} causeMessage={}",
                    id, root.getClass().getSimpleName(), root.getMessage(), ex);
            update(id, "FAILED", root.getClass().getSimpleName() + ": " + root.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }
    private void update(String id, String status, String error) {
        var item = items.findById(id).orElseThrow();
        if (item.status.equals("SAVED")) return;
        item.transition(status, error);
        items.saveAndFlush(item);
    }
    public WorkResponse get(String id) {
        WorkItem item = items.findById(id).orElseThrow(() -> new WorkNotFoundException(id));
        Reviewed reviewed = results.findById(id).map(result -> {
            try { return new Reviewed(item.topic, result.summary,
                    mapper.readValue(result.highlightsJson, mapper.getTypeFactory().constructCollectionType(java.util.List.class, String.class))); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("Corrupt saved highlights", ex); }
        }).orElse(null);
        return new WorkResponse(id, item.status, reviewed, item.errorMessage);
    }
    public static class WorkNotFoundException extends RuntimeException {
        public WorkNotFoundException(String id) { super("Unknown workId " + id); }
    }
}
