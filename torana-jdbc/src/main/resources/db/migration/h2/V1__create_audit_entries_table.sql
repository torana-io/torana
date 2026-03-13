-- Torana Audit Entries Table for H2
-- Version 1

CREATE TABLE audit_entries
(
    id             UUID PRIMARY KEY,
    action         VARCHAR(255)             NOT NULL,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    outcome        VARCHAR(20)              NOT NULL,

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

    -- Flexible data (CLOB for H2)
    metadata       CLOB,
    changes        CLOB,

    -- Error info
    error_message  CLOB,

    -- Schema version for migrations
    schema_version INT                      NOT NULL DEFAULT 1,

    -- Audit timestamp
    created_at     TIMESTAMP WITH TIME ZONE          DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX idx_audit_entries_action ON audit_entries (action);
CREATE INDEX idx_audit_entries_occurred_at ON audit_entries (occurred_at);
CREATE INDEX idx_audit_entries_actor_id ON audit_entries (actor_id);
CREATE INDEX idx_audit_entries_tenant_id ON audit_entries (tenant_id);
CREATE INDEX idx_audit_entries_target ON audit_entries (target_type, target_id);
CREATE INDEX idx_audit_entries_outcome ON audit_entries (outcome);
CREATE INDEX idx_audit_entries_tenant_time ON audit_entries (tenant_id, occurred_at DESC);
