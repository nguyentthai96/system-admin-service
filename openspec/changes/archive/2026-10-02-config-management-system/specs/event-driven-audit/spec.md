## Purpose

Provides asynchronous, non-blocking configuration change tracking and audit trail recording with zero-loss durability guarantees via PostgreSQL Transactional Outbox, background micro-batch ingestion, and pluggable polyglot database storage.

## ADDED Requirements

### Requirement: Non-Blocking Domain Change Event Emission
The system SHALL emit domain change events asynchronously after primary business transactions have successfully committed, ensuring zero latency overhead on main CRUD APIs.

#### Scenario: Asynchronous audit event capture after commit
- **WHEN** an administrator creates, updates, or deletes a configuration record and the primary database transaction commits
- **THEN** the system triggers an asynchronous event listener on a dedicated background thread pool to capture the change details without delaying the HTTP 200 response

### Requirement: Zero-Loss Transactional Outbox on Primary PostgreSQL
The system SHALL record every configuration change event in the `EVENT_PUBLICATION` table in the same local ACID transaction as the business data change on the default PostgreSQL database, ensuring event replayability upon application crash or restart.

#### Scenario: Server crash recovery with uncommitted audit events
- **WHEN** the application server terminates unexpectedly while background audit processing is underway
- **THEN** upon application restart, the system scans for incomplete entries in `EVENT_PUBLICATION` where completion timestamp is null and resumes audit processing without losing history

### Requirement: In-Memory Micro-Batch Buffer with Graceful Shutdown
The system SHALL accumulate audit events in an in-memory batch buffer and persist them in micro-batches based on item count or time window, with guaranteed complete flush on process termination.

#### Scenario: Periodic micro-batch persistence
- **WHEN** the in-memory buffer reaches 100 accumulated audit events or 500 milliseconds have elapsed since the last flush
- **THEN** the system executes a single bulk insert operation into the audit storage table

#### Scenario: Graceful buffer flush on SIGTERM
- **WHEN** the application receives a shutdown signal (SIGTERM)
- **THEN** the graceful shutdown hook halts incoming events, flushes all buffered audit records to disk storage, and cleanly releases resources before process exit

### Requirement: Pluggable Polyglot Audit Storage SPI
The system SHALL provide a pluggable storage interface defaulting to PostgreSQL JSONB format, with support for seamless routing to MongoDB when configured via application properties.

#### Scenario: Default storage in PostgreSQL JSONB
- **WHEN** the system operates with default settings without MongoDB configured
- **THEN** audit history and state snapshots are persisted in the PostgreSQL `sys_config_snapshot` table with GIN-indexed JSONB payloads

#### Scenario: Dynamic routing to MongoDB document store
- **WHEN** application configuration specifies `app.config.audit.storage-type=mongodb`
- **THEN** the system routes all snapshot and audit writes to the MongoDB `config_audit_snapshots` collection while keeping business tables in PostgreSQL
