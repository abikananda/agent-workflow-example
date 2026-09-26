# Agent workflow example

Spring Boot 3.5, Java 17, Google ADK Java, LangChain4j, Kafka, MySQL. One application hosts two independent ADK `SequentialAgent` roots. Every subagent is a separate class. Work uses `AnalysisSubagent`, `ExecutionSubagent` (factory for a real ADK `LlmAgent`), and `ReviewSubagent`. Google's `google-adk-langchain4j` bridge connects that `LlmAgent` to local Ollama. ADK Java has no `NonLlmAgent` class; deterministic subagents extend `BaseAgent`. Persistence uses `ValidationSubagent`, `TransformationSubagent`, and `StorageSubagent`, which extend `TypedStageAgent` (a typed `BaseAgent`). `TypedPipeline` validates their input and output contracts.

## Requirements

- JDK 17+, Maven 3.9+, a locally running Apache Kafka broker at `localhost:9092`
- Local MySQL 8 and Ollama with `llama3.2:3b` (`ollama pull llama3.2:3b`)

Install and start Kafka using the [Apache Kafka quickstart](https://kafka.apache.org/quickstart/). On Windows, run Kafka's `bin\windows\*.bat` scripts from its extracted directory. Configure the broker to advertise an address reachable from this application; the default `KAFKA_BOOTSTRAP_SERVERS` is `localhost:9092`. The application creates its two Kafka topics on startup. Kafka must be running before you start the application.

```sql
CREATE DATABASE agent_workflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'agent_workflow'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON agent_workflow.* TO 'agent_workflow'@'localhost';
```

Set `DB_URL=jdbc:mysql://localhost:3306/agent_workflow?useSSL=false&allowPublicKeyRetrieval=true`, `DB_USER=agent_workflow`, `DB_PASSWORD=choose-a-password`. Optionally set `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, and `KAFKA_BOOTSTRAP_SERVERS`. Windows PowerShell example: `$env:DB_USER='agent_workflow'`; set the other values similarly. No credentials are checked in.

For a work item that stays in `PROCESSING`, check Ollama from PowerShell with `Invoke-RestMethod http://localhost:11434/api/tags` and confirm `llama3.2:3b` is installed. Then poll `GET /api/work/{workId}` and inspect the Spring Boot console for `ADK stage event` or `ADK workflow failed`. `OLLAMA_TIMEOUT` defaults to `PT60S`; the full ADK workflow has a 90-second limit. A failed work item contains the root error in its `error` field. Submit a new POST after fixing the cause; an earlier in-progress request is not automatically replayed.

Start your local Kafka broker, MySQL, and Ollama, then run `mvn spring-boot:run` from this project directory.

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

For persistence, create a separate class extending `TypedStageAgent<PreviousOutput, NewOutput>`. Implement `inputType()`, `outputType()`, and `execute()`. Register it with `.then(newStep)` at the required position in `PersistenceAgentConfig`. The compiler checks adjoining generic types and `TypedPipeline` checks the declared runtime classes. For work, create a separate `BaseAgent` class for deterministic behavior or a factory for another `LlmAgent`; insert the resulting agent into `WorkAgent.execute`'s ordered `stages` list. Exchange text through ADK session state using `EventActions.stateDelta` and `outputKey`; validate the value when reading it. Leave the controller, consumer, and Kafka producer untouched. Use unique agent names within a root.

## Tests

Run `mvn test`. `TypedPipelineTest` checks the actual ADK sequence. `WorkflowIntegrationTest` uses Spring's embedded Kafka broker, an H2 test database, and a mocked LangChain4j model to verify HTTP → ADK → Kafka → ADK → database without Docker or external services. Flyway creates the MySQL tables in the running application; the integration test uses Hibernate to create an equivalent temporary H2 schema. The MySQL Flyway migration is therefore not exercised by that test.

## Current limits

This example performs one async processing attempt. An application crash after POST but before publication can leave a row in `RECEIVED` or `PROCESSING`; production deployments should add a transactional outbox or recovery scheduler. The in-memory ADK session exists only while each operation runs. The example has no authentication; secure the API before public deployment.
