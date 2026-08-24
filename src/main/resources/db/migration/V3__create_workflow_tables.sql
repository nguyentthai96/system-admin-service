-- V3: Create workflow tables (FR-013)
-- Approval workflow engine: definitions, instances, steps

CREATE TABLE workflow_definitions (
    id              BIGINT PRIMARY KEY,
    domain_id       BIGINT NOT NULL,
    code            VARCHAR(50) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(500),
    steps_json      JSONB NOT NULL DEFAULT '[]',
    version         INTEGER NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uq_workflow_def_code UNIQUE (domain_id, code, version)
);

CREATE TABLE workflow_instances (
    id                  BIGINT PRIMARY KEY,
    workflow_def_id     BIGINT NOT NULL REFERENCES workflow_definitions(id),
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           VARCHAR(100) NOT NULL,
    submitted_by        BIGINT NOT NULL,
    current_step        INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    submitted_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMP WITH TIME ZONE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

CREATE INDEX idx_workflow_instances_status ON workflow_instances(status);

CREATE TABLE workflow_steps (
    id                  BIGINT PRIMARY KEY,
    instance_id         BIGINT NOT NULL REFERENCES workflow_instances(id),
    step_order          INTEGER NOT NULL,
    approver_user_id    BIGINT,
    approver_role       VARCHAR(100),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    decision            VARCHAR(30),
    comments            VARCHAR(2000),
    decided_at          TIMESTAMP WITH TIME ZONE,
    escalation_timeout  INTEGER DEFAULT 86400,
    escalated           BOOLEAN NOT NULL DEFAULT FALSE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

CREATE INDEX idx_workflow_steps_instance ON workflow_steps(instance_id);
CREATE INDEX idx_workflow_steps_status ON workflow_steps(status);
