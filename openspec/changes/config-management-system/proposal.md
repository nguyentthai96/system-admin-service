## Why

Currently, system configurations (dynamic parameters, nested menu hierarchies, i18n localization messages, and tenant settings) are scattered and lack unified governance. They do not support full-fidelity backup/migration across environments (Dev, Staging, Production), nor do they provide cross-domain point-in-time snapshots or safe rollbacks. Furthermore, tracking changes synchronously can degrade main CRUD performance, and ad-hoc export/import logic leads to code duplication across microservices. 

This change introduces a centralized, high-abstraction Configuration Management System built on a reusable abstract base (`base-core`/`base-file-starter`) and `system-admin-service`, ensuring zero-loss change auditing via a transactional outbox on primary PostgreSQL and pluggable MongoDB storage.

## What Changes

- **Abstract Base Code Extensions (`base-core` & `base-file-starter`)**:
  - Add `SimpleJsonExportStrategy<T>` and `RelationalJsonExportStrategy<R>` for streaming flat and graph-structured exports.
  - Introduce `MultiSheetExcelExportStrategy` and `MultiSheetExportTemplate` for multi-table/domain Excel export without breaking single-sheet backward compatibility.
  - Provide `RelationalImportCoordinator` and policy-driven import strategy modes (`DELETE_AND_INSERT`, `UPSERT_MERGE`, `TRUNCATE_AND_LOAD`) with `DefaultSimpleImportHandler<T>` to minimize boilerplate code.
- **High-Abstraction Versioning Framework (`system-admin-service`)**:
  - Introduce `VersionedConfigDomain<E>` interface allowing new configuration domains to be versioned with minimal code.
  - Implement milestone-based snapshots (`sys_config_milestone`, `sys_config_snapshot`) enabling cross-domain grouped changes, state switching, and deep diff comparisons.
  - Safe rollback engine with automatic conflict detection against post-snapshot modifications.
- **Non-Blocking Zero-Loss Event Audit**:
  - Decouple audit tracking from primary CRUD transactions using `@ApplicationModuleListener` and Spring Modulith's `EVENT_PUBLICATION` transactional outbox on PostgreSQL.
  - Implement `BatchAuditCollector` in-memory micro-batch buffer (100 events / 500ms) with graceful shutdown flush hooks.
  - Provide pluggable `ConfigAuditStorageProvider` SPI defaulting to PostgreSQL JSONB while supporting MongoDB as an optional scalable document store.

## Capabilities

### New Capabilities

- `config-versioning-snapshot`: Point-in-time state capture, named milestone grouping across domains, visual state diffing, and conflict-aware rollback engine.
- `relational-file-exchange`: Structured export and import of complex relational graphs via JSON and Multi-sheet Excel, supporting `DELETE_AND_INSERT`, `UPSERT_MERGE`, and `TRUNCATE_AND_LOAD` modes.
- `event-driven-audit`: Non-blocking domain change tracking via Spring Modulith Transactional Outbox on primary PostgreSQL with batch ingestion buffer and pluggable polyglot database SPI.

### Modified Capabilities

<!-- No existing spec-level capabilities to modify -->

## Impact

- **Affected Services**:
  - `services/system-admin-service`: Core domain management, versioning APIs, milestone controllers, and audit consumers.
  - `components/base-core`: Domain file export/import starters extended with JSON, multi-sheet, and relational handlers.
- **Database Changes**:
  - PostgreSQL schema additions: `sys_config_milestone`, `sys_config_snapshot` (with GIN index on `state_payload`), and integration with `EVENT_PUBLICATION`.
- **API Endpoints**:
  - New endpoints under `/api/v1/configs/**` for domain CRUD, export/import, milestone lifecycle, diff viewing, and rollback operations.
- **Dependencies**:
  - Reuses existing `spring-modulith-starter-core`, `spring-modulith-starter-jpa`, Apache POI SXSSF, and Jackson modules.
