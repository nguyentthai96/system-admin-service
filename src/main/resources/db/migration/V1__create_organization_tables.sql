-- V1: Create organization tables (FR-011)
-- Departments (tree), Positions, User-Position assignments

CREATE TABLE departments (
    id              BIGINT PRIMARY KEY,
    domain_id       BIGINT NOT NULL,
    parent_id       BIGINT REFERENCES departments(id),
    code            VARCHAR(50) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(500),
    manager_user_id BIGINT,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    tree_level      INTEGER NOT NULL DEFAULT 0,
    tree_path       VARCHAR(1000),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uq_departments_domain_code UNIQUE (domain_id, code)
);

CREATE INDEX idx_departments_domain ON departments(domain_id);
CREATE INDEX idx_departments_parent ON departments(parent_id);

CREATE TABLE positions (
    id              BIGINT PRIMARY KEY,
    department_id   BIGINT NOT NULL REFERENCES departments(id),
    code            VARCHAR(50) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(500),
    sort_order      INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uq_positions_dept_code UNIQUE (department_id, code)
);

CREATE INDEX idx_positions_department ON positions(department_id);

CREATE TABLE user_positions (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    position_id     BIGINT NOT NULL REFERENCES positions(id),
    assigned_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uq_user_positions UNIQUE (user_id, position_id)
);

CREATE INDEX idx_user_positions_user ON user_positions(user_id);
