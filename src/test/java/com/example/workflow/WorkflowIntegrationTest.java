package com.example.workflow;

import com.example.workflow.db.WorkResultRepository;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowIntegrationTest {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
    static { MYSQL.start(); KAFKA.start(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
    @MockitoBean ChatModel chatModel;
    @Autowired TestRestTemplate rest;
    @Autowired WorkResultRepository results;
    @LocalServerPort int port;

    @Test void requestTravelsThroughKafkaAndIsSavedOnce() throws Exception {
        when(chatModel.chat(anyString())).thenReturn("Kafka coordinates event-driven applications. "
                + "It carries records between independent services and can retry consumers safely.");
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
}
