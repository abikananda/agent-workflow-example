CREATE TABLE work_item (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    topic VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE work_result (
    work_id VARCHAR(36) NOT NULL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    summary TEXT NOT NULL,
    highlights_json JSON NOT NULL,
    completed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_result_work FOREIGN KEY (work_id) REFERENCES work_item(id)
) ENGINE=InnoDB;
