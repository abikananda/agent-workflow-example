package com.example.workflow.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WorkItemRepository extends JpaRepository<WorkItem, String> {
    @Transactional @Modifying
    @Query("update WorkItem w set w.status = 'PUBLISHED', w.updatedAt = CURRENT_TIMESTAMP where w.id = :id and w.status = 'PROCESSING'")
    int markPublished(@Param("id") String id);
}
