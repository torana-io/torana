-- Torana Audit Entries Table for MySQL
-- Version 1

CREATE TABLE audit_entries
(
    id             CHAR(36) PRIMARY KEY,
    action         VARCHAR(255) NOT NULL,
    occurred_at    DATETIME(6) NOT NULL,
    outcome        VARCHAR(20)  NOT NULL,

    -- Actor
    actor_id       VARCHAR(255),
    actor_type     VARCHAR(50),
    actor_name     VARCHAR(255),

    -- Tenant
    tenant_id      VARCHAR(255),
    tenant_name    VARCHAR(255),

    -- Target
    target_type    VARCHAR(255),
    target_id      VARCHAR(255),
    target_name    VARCHAR(255),

    -- Request Context
    request_id     VARCHAR(255),
    request_method VARCHAR(10),
    request_path   VARCHAR(2048),
    client_ip      VARCHAR(45),
    user_agent     VARCHAR(512),

    -- Trace Context
    trace_id       VARCHAR(64),
    span_id        VARCHAR(64),
    parent_span_id VARCHAR(64),

    -- Flexible data (JSON for MySQL)
    metadata       JSON,
    changes        JSON,

    -- Error info
    error_message  TEXT,

    -- Schema version for migrations
    schema_version INT          NOT NULL DEFAULT 1,

    -- Audit timestamp
    created_at     DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),

    -- Indexes
    INDEX          idx_action (action),
    INDEX          idx_occurred_at (occurred_at),
    INDEX          idx_actor_id (actor_id),
    INDEX          idx_tenant_id (tenant_id),
    INDEX          idx_target (target_type, target_id),
    INDEX          idx_outcome (outcome),
    INDEX          idx_tenant_time (tenant_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
