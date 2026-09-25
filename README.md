# Agent workflow example

Spring Boot 3.5, Java 17, Google ADK Java, LangChain4j, Kafka, MySQL. One application hosts two independent ADK `SequentialAgent` roots. Work has two custom deterministic `BaseAgent` subagents and one real ADK `LlmAgent` subagent. Google's `google-adk-langchain4j` bridge connects that `LlmAgent` to local Ollama. ADK Java has no `NonLlmAgent` class; deterministic subagents extend `BaseAgent`. Persistence has three deterministic `BaseAgent` subagents. `TypedPipeline` validates persistence stage types and stores the handoff by ADK session ID.

## Requirements

- JDK 17+, Maven 3.9+, Docker (for Kafka and optional integration tests)
- Local MySQL 8 and Ollama with `llama3.2:3b` (`ollama pull llama3.2:3b`)

```sql
CREATE DATABASE agent_workflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'agent_workflow'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON agent_workflow.* TO 'agent_workflow'@'localhost';
```

Set `DB_URL=jdbc:mysql://localhost:3306/agent_workflow?useSSL=false&allowPublicKeyRetrieval=true`, `DB_USER=agent_workflow`, `DB_PASSWORD=choose-a-password`. Optionally set `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, and `KAFKA_BOOTSTRAP_SERVERS`. Windows PowerShell example: `$env:DB_USER='agent_workflow'`; set the other values similarly. No credentials are checked in.

```bash
docker compose up -d
mvn spring-boot:run
```

```bash
curl -i -H 'Content-Type: application/json' -d '{"topic":"Kafka"}' http://localhost:8080/api/work
curl http://localhost:8080/api/work/PUT-WORK-ID-HERE
```

POST returns HTTP 202 with `RECEIVED`. Poll GET for `PROCESSING`, `PUBLISHED`, `SAVED`, or `FAILED`. The final result is available after `SAVED`. Inspect MySQL with `SELECT * FROM work_item; SELECT * FROM work_result;`.

Example Kafka value (the UUIDs must correspond to an existing submitted work):

```json
{"eventId":"3bbed116-9f2b-4be1-b74b-f3a317d005c9","workId":"ff4ab65a-018d-48e7-bd0d-bc921cd90f51","schemaVersion":1,"completedAt":"2026-09-25T12:00:00Z","result":{"topic":"Kafka","summary":"Kafka moves event records between services.","highlights":["Overview","Key points","Practical implications"]}}
```

`workId` is the Kafka key. Consumer offsets are acknowledged after the transactional database write returns. Retries run twice at one-second intervals, then the original record goes to `work.completed.v1.DLT`; the DLT consumer marks a known work item `FAILED`. Duplicate events with the same `workId` and `eventId` return the saved record. A conflicting event is rejected. The database also enforces uniqueness. Kafka publishing waits for broker confirmation. If an application crashes after Kafka confirms a message but before status changes, the consumer can still move the work directly to `SAVED`.

## Add a subagent

For persistence, create `TypedStep<PreviousOutput, NewOutput>` with `name()`, `inputType()`, `outputType()`, and `apply()`. In `PersistenceAgentConfig`, insert `.then(newStep)` at the required position. The compiler checks the surrounding generic types and `TypedPipeline` checks declared runtime classes. For work, add a `BaseAgent` deterministic stage or build another `LlmAgent` and insert it into the `stages` list in `WorkAgent.execute`. Exchange text through ADK session state using `EventActions.stateDelta` and `outputKey`; validate the state value in the next deterministic stage. Leave the controller, consumer, and Kafka producer untouched. Do not assign duplicate agent names within a root.

## Tests

Run `mvn test`. `TypedPipelineTest` checks the actual ADK sequence. `WorkflowIntegrationTest` uses Testcontainers Kafka/MySQL and a mocked LangChain4j model to verify the HTTP → ADK → Kafka → ADK → MySQL path. Docker must be available for integration tests. Flyway creates the tables; Hibernate validates the schema.

## Current limits

This example performs one async processing attempt. An application crash after POST but before publication can leave a row in `RECEIVED` or `PROCESSING`; production deployments should add a transactional outbox or recovery scheduler. The in-memory ADK session exists only while each operation runs. The example has no authentication; secure the API before public deployment.
