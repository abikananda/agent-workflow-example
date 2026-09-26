package com.example.workflow;

import com.example.workflow.db.WorkResultRepository;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:workflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@EmbeddedKafka(partitions = 1, topics = {"work.completed.v1", "work.completed.v1.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class WorkflowIntegrationTest {
    @MockitoBean ChatModel chatModel;
    @Autowired TestRestTemplate rest;
    @Autowired WorkResultRepository results;
    @LocalServerPort int port;

    @Test void requestTravelsThroughKafkaAndIsSavedOnce() throws Exception {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("Kafka coordinates event-driven applications. "
                        + "It carries records between independent services and can retry consumers safely."))
                .build());
        String base = "http://localhost:" + port + "/api/work";
        ResponseEntity<Map> response = rest.postForEntity(base, Map.of("topic", "Kafka"), Map.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        String id = (String) response.getBody().get("workId");
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        Map status = Map.of();
        while (System.nanoTime() < deadline) {
            status = rest.getForObject(base + "/" + id, Map.class);
            if ("SAVED".equals(status.get("status")) || "FAILED".equals(status.get("status"))) break;
            Thread.sleep(250);
        }
        assertEquals("SAVED", status.get("status"), "workflow state: " + status);
        assertTrue(results.existsById(id));
    }

    @Test void modelFailureIsVisibleOnWorkStatus() throws Exception {
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("Model unavailable for test"));
        String base = "http://localhost:" + port + "/api/work";
        ResponseEntity<Map> response = rest.postForEntity(base, Map.of("topic", "Unavailable model"), Map.class);
        String id = (String) response.getBody().get("workId");
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        Map status = Map.of();
        while (System.nanoTime() < deadline) {
            status = rest.getForObject(base + "/" + id, Map.class);
            if ("FAILED".equals(status.get("status"))) break;
            Thread.sleep(200);
        }
        assertEquals("FAILED", status.get("status"), "workflow state: " + status);
        assertTrue(String.valueOf(status.get("error")).contains("Model unavailable for test"));
    }
}
