package com.example.workflow.persistence;

import com.example.workflow.db.WorkItemRepository;
import com.example.workflow.model.Contracts.WorkCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class WorkConsumer {
    private static final Logger log = LoggerFactory.getLogger(WorkConsumer.class);
    private final PersistenceService persistence;
    private final ObjectMapper mapper;
    private final WorkItemRepository items;
    public WorkConsumer(PersistenceService persistence, ObjectMapper mapper, WorkItemRepository items) {
        this.persistence = persistence; this.mapper = mapper; this.items = items;
    }
    @KafkaListener(topics = "${app.topic}", groupId = "persistence-agent-v1")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        try (MDC.MDCCloseable work = MDC.putCloseable("workId", record.key())) {
            WorkCompletedEvent event = mapper.readValue(record.value(), WorkCompletedEvent.class);
            try (MDC.MDCCloseable eventContext = MDC.putCloseable("eventId", event.eventId())) {
                if (!record.key().equals(event.workId())) throw new IllegalArgumentException("Kafka key differs from workId");
                persistence.persist(event);
                ack.acknowledge();
                log.info("Persisted completed work");
            }
        }
    }
    @KafkaListener(topics = "${app.topic}.DLT", groupId = "persistence-agent-dlt-v1")
    public void deadLetter(ConsumerRecord<String, String> record) {
        items.findById(record.key()).ifPresent(item -> {
            if (!"SAVED".equals(item.status)) {
                item.transition("FAILED", "Kafka persistence retries exhausted; inspect the dead-letter topic");
                items.saveAndFlush(item);
            }
        });
        log.error("Dead-lettered event workId={}", record.key());
    }
}
