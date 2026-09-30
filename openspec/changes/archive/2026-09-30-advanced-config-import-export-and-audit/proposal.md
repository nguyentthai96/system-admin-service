## Why

The initial Configuration Management System (`config-management-system`) delivered core versioning, snapshot, and milestone capabilities within `system-admin-service`. However, key export/import strategies (`SimpleJsonExportStrategy`, `RelationalJsonExportStrategy`, `MultiSheetExcelExportStrategy`) and import coordinators (`RelationalImportCoordinator`, `TopologicalDependencySorter`, `DefaultSimpleImportHandler`, `TwoPassTreeImportHandler`) were implemented directly inside `system-admin-service/shared/file/`, limiting reuse by other microservices (`auth-service`, `account-service`).

This change **promotes** these proven abstractions into `components/base-core/starters/base-file-starter` for platform-wide reuse, adds **Dynamic JPA Metamodel** introspection to eliminate manual column definition boilerplate, and introduces the **4-mode policy-driven import** (`TRUNCATE_AND_LOAD`, `DELETE_AND_INSERT`, `UPSERT_MERGE`, `PATCH_VALUES`) with safe self-referencing FK handling via Two-Pass Import + Deferred Constraints. Additionally, it enriches the audit trail with CWE-1236 Formula Injection protection and Outbox event auto-recovery on restart.

## What Changes

- **Promote to `base-file-starter` (`components/base-core`)**:
  - Move `SimpleJsonExportStrategy<T>` — Jackson `JsonGenerator` streaming JSON array export.
  - Move `RelationalJsonExportStrategy` — Hierarchical graph export with SHA-256 checksum.
  - Create `MultiSheetExcelExportStrategy` — `SXSSFWorkbook(100)` multi-sheet export with shared Style Pool.
  - Create `DynamicJpaMetamodelSheetExtractor` — Auto-introspect JPA entity metadata for column definitions.
  - Move `TopologicalDependencySorter` — Kahn's DAG algorithm for FK dependency ordering.
  - Create `TableImportHandler<T>` SPI with `ImportStrategyMode` enum (4 modes).
  - Create `DefaultSimpleImportHandler<T, ID>` — Generic flat-table import via `JpaRepository`.
  - Create `TwoPassTreeImportHandler<T, ID>` — Self-referencing FK-safe two-pass hierarchy import.
  - Move `RelationalImportCoordinator` — ACID transactional orchestrator for multi-table import.
  - Add `ExportFormat.JSON` to existing `ExportFormat` enum.

- **Enhance `system-admin-service`**:
  - Refactor existing `shared/file/export/` and `shared/file/import/` to delegate to `base-file-starter` base classes.
  - Register domain-specific `TableImportHandler` implementations for Menu, Department, CommonConfig, FeatureFlags.
  - Add `ConfigDomainRegistry` dynamic domain management.
  - Enhance `ConfigManagementController` with new endpoints: `GET /export/all`, `POST /import` with mode selection.

- **Security & Resilience**:
  - Integrate `ExportSanitizer` (CWE-1236) into all Excel/CSV export paths.
  - Enable `spring.modulith.republish-outstanding-events-on-restart=true` for auto-recovery.

## Capabilities

### New Capabilities

- `dynamic-jpa-metamodel-export`: Auto-generate Excel sheet definitions from JPA entity metadata without manual `ColumnDefinition` boilerplate.
- `four-mode-policy-import`: Flexible configuration loading with `TRUNCATE_AND_LOAD`, `DELETE_AND_INSERT`, `UPSERT_MERGE`, and `PATCH_VALUES` strategies.
- `two-pass-tree-import`: Safe import of self-referencing hierarchical data (Menu tree, Department tree) via deferred FK constraints and two-pass execution.
- `formula-injection-protection`: CWE-1236 sanitization for all exported Excel/CSV cell values.

### Modified Capabilities

- `relational-file-exchange`: Promoted from `system-admin-service/shared/file/` to `base-file-starter` for platform-wide reuse. Added `PATCH_VALUES` mode. No breaking API changes.
- `event-driven-audit`: Enhanced with auto-republish on restart and MongoDB fallback resilience.

## Impact

- **Affected Services**:
  - `components/base-core/starters/base-file-starter`: 10 new/moved classes in `export/` and `import/` packages.
  - `services/system-admin-service`: Refactored `shared/file/` delegates + new domain handlers + enhanced controller endpoints.
- **Database Changes**:
  - No new tables (reuses existing `sys_config_milestone`, `sys_config_snapshot`, `event_publication`).
  - New import uses `SET CONSTRAINTS ALL DEFERRED` within transaction scope.
- **API Endpoints**:
  - `GET /api/v1/configs/export/all` — Multi-sheet Excel export via Dynamic JPA Metamodel.
  - `POST /api/v1/configs/import` — Policy-driven import with mode selection.
  - Existing endpoints unchanged (backward compatible).
- **Dependencies**:
  - Reuses existing `apache-poi`, `jackson-module-kotlin`, `spring-modulith-starter-jpa`.
  - No new external dependencies.
