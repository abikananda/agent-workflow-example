package com.example.workflow.persistence;

import com.example.workflow.agents.AdkExecutor;
import com.example.workflow.agents.TypedPipeline;
import com.example.workflow.db.WorkResult;
import com.example.workflow.model.Contracts.WorkCompletedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistenceAgentConfig {
    @Bean AdkExecutor<WorkCompletedEvent, WorkResult> persistenceAgent(
            ValidationSubagent validation, TransformationSubagent transformation, StorageSubagent storage) {
        return new AdkExecutor<>(TypedPipeline.builder("persistence_agent", WorkCompletedEvent.class)
                .then(validation).then(transformation).then(storage).build());
    }
}
