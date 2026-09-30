## Context

See [proposal.md](proposal.md) for background and motivation. 

The system currently relies on ad-hoc configuration storage across multiple domain entities (`domain_configs`, `menu_items`, `feature_flags`). Existing export/import capabilities in `base-file-starter` (`ExcelExportStrategy`, `CsvExportStrategy`) are strictly single-table and lack hierarchical JSON streaming or multi-sheet Excel generation. Furthermore, previous versioning in `DomainConfigService` was implemented synchronously per-domain with direct table writes, which risks blocking main CRUD operations, lacks cross-domain milestone grouping, and provides no transactional zero-loss guarantee against server restarts or crashes.

## Goals / Non-Goals

**Goals:**
- Provide a unified, high-abstraction interface (`VersionedConfigDomain<E>`) enabling any current or future configuration domain to gain automatic change tracking, point-in-time snapshots, diff comparisons, and conflict-aware rollback.
- Extend `base-file-starter` with reusable `SimpleJsonExportStrategy`, `RelationalJsonExportStrategy`, `MultiSheetExcelExportStrategy`, and policy-driven import strategy modes (`DELETE_AND_INSERT`, `UPSERT_MERGE`, `TRUNCATE_AND_LOAD`) with `DefaultSimpleImportHandler<T>` to eliminate boilerplate.
- Guarantee **Zero-Loss Durability** by writing domain change events into the PostgreSQL `EVENT_PUBLICATION` table in the same local ACID transaction as business mutations via Spring Modulith.
- Optimize write I/O through an in-memory `BatchAuditCollector` (micro-batching 100 events / 500ms) with a graceful shutdown flush hook.
- Implement a pluggable storage SPI (`ConfigAuditStorageProvider`) defaulting to PostgreSQL JSONB (zero extra infrastructure) while allowing seamless scaling to MongoDB.

**Non-Goals:**
- Replacing Git-based application configuration (e.g., `application.yml` or Spring Cloud Config server for bootstrap properties). This design specifically governs dynamic database-backed business configurations.
- Replacing general JPA entity auditing (Hibernate Envers `_AUD` tables). Envers operates at the low-level row mutation layer, whereas this system operates at the domain-level aggregate and milestone snapshot layer.

## Decisions

### 1. Abstract Base Extensions vs. Service-Specific Implementation
- **Decision**: Extend `base-core` and `base-file-starter` with generic `ExportStrategy` implementations (`SimpleJsonExportStrategy`, `RelationalJsonExportStrategy`, `MultiSheetExcelExportStrategy`) and `TableImportHandler` instead of creating ad-hoc exporters in `system-admin-service`.
- **Rationale**: Any microservice in the platform (`auth-service`, `account-service`, `system-admin-service`) needs to export/import complex nested relational data and multi-sheet workbooks. Placing abstractions in `base-core` ensures DRY compliance and standardized file interchange formats.
- **Alternatives Considered**: Writing one-off JSON writers and multi-sheet builders directly inside `system-admin-service`. Rejected due to heavy code duplication and lack of platform-wide reuse.

### 2. Multi-Sheet Excel: Separate Strategy vs. Modifying Existing
- **Decision**: Introduce a dedicated `MultiSheetExcelExportStrategy` and `MultiSheetExportTemplate` rather than modifying `ExcelExportStrategy<T>`.
- **Rationale**:
  - `ExcelExportStrategy<T>` has a strict single-type signature `export(Stream<T>, ...)` optimized for single-sheet streaming. Modifying its contract would break backward compatibility across all existing usages.
  - Adheres strictly to the Single Responsibility Principle (SRP) and Open-Closed Principle (OCP).
  - Common formatting, cell styling, and formula sanitization are shared via an internal helper (`ExcelCellWriter`, `ExportSanitizer`).
- **Alternatives Considered**: Overloading `ExcelExportStrategy` to accept `Map<String, Stream<*>>` with type casting. Rejected due to brittle unchecked casts and regression risks.

### 3. Change Tracking: Event-Driven Transactional Outbox vs. Synchronous Interceptor
- **Decision**: Adopt asynchronous event publishing with Spring Modulith Event Publication Registry (`@ApplicationModuleListener`) backed by the PostgreSQL `EVENT_PUBLICATION` table.
- **Rationale**:
  - Emitting domain events after transaction commit decouples audit persistence from primary CRUD requests, keeping response times under 50ms.
  - Writing to `EVENT_PUBLICATION` within the local business transaction guarantees zero loss: if the JVM crashes before asynchronous audit ingestion finishes, the registry automatically resubmits incomplete events upon restart.
- **Alternatives Considered**: Synchronous AOP interceptor writing directly to audit tables. Rejected because slow audit storage operations directly degrade user API response latency and can roll back valid business operations if audit logging fails.

### 4. Polyglot Storage SPI: PostgreSQL Primary Default with Optional MongoDB
- **Decision**: Implement `ConfigAuditStorageProvider` with `PostgreSqlJsonbAuditStorageProvider` as the 100% ready out-of-the-box primary implementation, with `MongoDbAuditStorageProvider` as a pluggable extension.
- **Rationale**:
  - PostgreSQL is the primary platform database. Utilizing its `JSONB` column type and GIN indexing provides high-performance schemaless querying for snapshots without introducing extra infrastructure or operational overhead.
  - MongoDB can be plugged in seamlessly via configuration (`app.config.audit.storage-type=mongodb`) when audit volumes scale to millions of historical records or when document-based sharding is needed.
- **Alternatives Considered**: Mandating MongoDB as a hard requirement. Rejected because it forces a secondary database dependency on environments that only need standard admin configuration management.

### 5. High-Abstraction Domain SPI (`VersionedConfigDomain<E>`)
- **Architecture**:
```kotlin
interface VersionedConfigDomain<E : Any> {
    val domainName: String
    val entityClass: Class<E>
    val naturalKeyExtractor: (E) -> String
    fun fetchCurrentState(): List<E>
    fun applyRollbackState(snapshotPayload: String)
    fun exportTemplate(): ExportTemplate<E>?
}
```
- A centralized `ConfigSnapshotManager` discovers all registered `VersionedConfigDomain` Spring beans dynamically, enabling cross-domain milestones and multi-domain rollbacks without hardcoded switch-cases.

### 6. Database DDL Schema (PostgreSQL Primary)
```sql
CREATE TABLE sys_config_milestone (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(30) DEFAULT 'ACTIVE'
);

CREATE TABLE sys_config_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    milestone_id VARCHAR(64) REFERENCES sys_config_milestone(id),
    domain_name VARCHAR(50) NOT NULL,
    state_payload JSONB NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    record_count INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_snapshot_domain_time ON sys_config_snapshot(domain_name, created_at DESC);
CREATE INDEX idx_snapshot_payload_gin ON sys_config_snapshot USING GIN (state_payload);
```

## Risks / Trade-offs

- **[Risk] Foreign Key constraint violation during `DELETE_AND_INSERT` import** → *Mitigation*: The `RelationalImportCoordinator` performs dependency graph topological sorting to delete child entities first and re-insert in parent-then-child sequence within an atomic transaction.
- **[Risk] Large dataset export causing Out-Of-Memory (OOM)** → *Mitigation*: Both `ExcelExportStrategy` and `MultiSheetExcelExportStrategy` strictly employ Apache POI `SXSSFWorkbook` with a 100-row sliding window in memory, accompanied by JPA database streaming cursors.
- **[Risk] Buffer loss upon unexpected JVM shutdown** → *Mitigation*: The `BatchAuditCollector` implements Spring's `SmartLifecycle` / `DisposableBean` to flush any remaining in-memory events before the process shuts down cleanly. Moreover, the PostgreSQL `EVENT_PUBLICATION` outbox persists events before completion, enabling restart recovery even after abrupt crashes (`kill -9`).
- **[Risk] Rollback overriding critical hotfixes applied post-snapshot** → *Mitigation*: The rollback engine compares the `updated_at` / `@Version` timestamp of each current entity against the snapshot timestamp. Any post-snapshot modifications trigger a 409 Conflict with a detailed key list unless `forceOverwrite=true` is explicitly provided.

## Migration Plan

1. **Phase 1: Base Infrastructure Deployment**:
   - Add new export strategies (`SimpleJsonExportStrategy`, `MultiSheetExcelExportStrategy`) and import coordinators into `base-file-starter`.
   - Update Flyway migrations in `system-admin-service` to create `sys_config_milestone` and `sys_config_snapshot` tables.
2. **Phase 2: Core Versioning Framework Implementation**:
   - Implement `VersionedConfigDomain<E>`, `BatchAuditCollector`, and `PostgreSqlJsonbAuditStorageProvider`.
   - Configure Spring Modulith event publication registry for `ConfigDomainChangedEvent`.
3. **Phase 3: Domain Onboarding**:
   - Implement `VersionedConfigDomain` for `system_configs`, `i18n_messages`, `menu_items`, and `domain_configs`.
4. **Phase 4: API & Verification**:
   - Expose REST endpoints for export/import, milestone management, diff viewing, and rollback.
   - Run end-to-end integration tests verifying zero loss under crash simulation.

## Open Questions

- *None*: All critical design questions regarding JSON export, multi-sheet strategy separation, outbox reliability, and polyglot database storage have been resolved during the brainstorm phase.
