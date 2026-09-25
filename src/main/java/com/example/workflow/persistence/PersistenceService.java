package com.example.workflow.persistence;

import com.example.workflow.agents.AdkExecutor;
import com.example.workflow.db.WorkResult;
import com.example.workflow.model.Contracts.WorkCompletedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistenceService {
    private final AdkExecutor<WorkCompletedEvent, WorkResult> agent;
    public PersistenceService(AdkExecutor<WorkCompletedEvent, WorkResult> persistenceAgent) { this.agent = persistenceAgent; }
    @Transactional
    public void persist(WorkCompletedEvent event) { agent.execute(event.workId(), event); }
}
