<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Hybrid", factory: "N/A", feature_type: "EXTEND", transaction_flow: "Hybrid (Streaming + Transactional + Outbox)" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
<!-- self-contained: true -->

## Phase 1: Base Infrastructure Promotion (base-file-starter)

### 1.1 Export Subsystem

- [x] 1.1.1 **Add ExportFormat.JSON to ExportFormat enum**
  - File: `components/base-core/src/main/kotlin/com/ntt/basecore/domain/file/ExportFormat.kt` | Action: [MODIFY]
  - FR: FR-001 — Hỗ trợ export định dạng JSON
  - Pattern: Add `JSON` enum value alongside existing `EXCEL`, `CSV`
  - Dependencies: None

- [x] 1.1.2 **Move SimpleJsonExportStrategy to base-file-starter**
  - File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/SimpleJsonExportStrategy.kt` | Action: [MOVE]
  - Source: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/SimpleJsonExportStrategy.kt`
  - Base: `ExportStrategy<T>` from `com.ntt.basecore.domain.file`
  - FR: FR-001 — Xuất JSON phẳng dạng streaming
  - Pattern: Jackson `JsonGenerator` streaming directly to `OutputStream`
  - Dependencies: `com.fasterxml.jackson.databind.ObjectMapper`, `com.ntt.basecore.domain.file.ExportConfig`

- [x] 1.1.3 **Move RelationalJsonExportStrategy to base-file-starter**
  - File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/RelationalJsonExportStrategy.kt` | Action: [MOVE]
  - Source: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/RelationalJsonExportStrategy.kt`
  - Base: `ExportStrategy<Any>` from `com.ntt.basecore.domain.file`
  - FR: FR-002 — Xuất đồ thị quan hệ JSON có SHA-256
  - Pattern: SHA-256 checksum payload with `RelationalExportPayload` wrapper
  - Dependencies: `java.security.MessageDigest`, `RelationalExportTemplate<R>`

- [x] 1.1.4 **Move MultiSheetExcelExportStrategy to base-file-starter**
  - File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/MultiSheetExcelExportStrategy.kt` | Action: [MOVE]
  - Source: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/MultiSheetExcelExportStrategy.kt`
  - Base: Independent strategy using `SXSSFWorkbook(100)`
  - FR: FR-003 — Xuất Excel nhiều Sheet
  - Pattern: Apache POI streaming with Style Pool and `ExportSanitizer` (CWE-1236 — FR-013)
  - Dependencies: `org.apache.poi.xssf.streaming.SXSSFWorkbook`, `ExportSanitizer`

- [x] 1.1.5 **Create DynamicJpaMetamodelSheetExtractor**
  - File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/DynamicJpaMetamodelSheetExtractor.kt` | Action: [NEW]
  - Base: `@Component`, `@ConditionalOnBean(EntityManager.class)`
  - FR: FR-003 — Auto-introspect JPA Metamodel for column definitions
  - Pattern: `jakarta.persistence.metamodel.Metamodel` scanning, exclude `@Id`/`@Version`, respect `@ExportColumn`
  - Dependencies: `jakarta.persistence.EntityManager`, `jakarta.persistence.metamodel.Metamodel`

- [x] 1.1.6 **Create SheetExportDefinition data class and @ExportColumn annotation**
  - File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/SheetExportDefinition.kt` | Action: [NEW]
  - FR: FR-003 — Data container for auto-generated sheet definitions
  - Pattern: Kotlin data class with `sheetName`, `columns: List<ColumnDefinition<T>>`, `entityClass: KClass<T>`

### 1.2 Import Subsystem

- [x] 1.2.1 **Move TopologicalDependencySorter to base-file-starter**
  - File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/import/TopologicalDependencySorter.kt` | Action: [MOVE]
  - Source: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/import/TopologicalDependencySorter.kt`
  - FR: FR-004 — Thuật toán Kahn sắp xếp phụ thuộc
  - Pattern: Kahn's DAG algorithm, circular dependency detection

- [x] 1.2.2 **Move TableImportHandler and ImportStrategyMode to base-file-starter**
  - File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/import/TableImportHandler.kt` | Action: [MOVE]
  - Source: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/import/` (interface + enum)
  - FR: FR-005 — 4 chế độ nạp cấu hình: TRUNCATE_AND_LOAD, DELETE_AND_INSERT, UPSERT_MERGE, PATCH_VALUES
  - Pattern: SPI interface with `domainName`, `entityClass`, `dependencies`, `process()`, `validate()`

- [x] 1.2.3 **Move DefaultSimpleImportHandler to base-file-starter**
  - File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/import/DefaultSimpleImportHandler.kt` | Action: [MOVE]
  - Source: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/import/` (inline class)
  - Base: Abstract class implementing `TableImportHandler<T>` via `JpaRepository<T, ID>`
  - FR: FR-006 — Khung thực thi mặc định giảm mã thừa
  - Pattern: `deleteAllInBatch() + saveAll()` for TRUNCATE/DELETE; natural key matching for UPSERT/PATCH

- [x] 1.2.4 **Create TwoPassTreeImportHandler**
  - File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/import/TwoPassTreeImportHandler.kt` | Action: [NEW]
  - Base: Abstract class implementing `TableImportHandler<T>` with `JdbcTemplate`
  - FR: FR-015 — Deferred FK Constraints + Two-Pass Import
  - Pattern: `SET CONSTRAINTS ALL DEFERRED` → Pass 1 (insert with null parent) → Pass 2 (update parent_id)
  - Dependencies: `org.springframework.jdbc.core.JdbcTemplate`, `JpaRepository<T, ID>`

- [x] 1.2.5 **Move RelationalImportCoordinator to base-file-starter**
  - File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/import/RelationalImportCoordinator.kt` | Action: [MOVE]
  - Source: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/import/RelationalImportCoordinator.kt`
  - FR: FR-005 — ACID transactional multi-table import orchestrator
  - Pattern: `@Transactional` wrapping, topological sort, sequential handler execution
  - Dependencies: `TopologicalDependencySorter`, `TransactionTemplate`, `ApplicationEventPublisher`

---

## Phase 2: System-Admin Integration (system-admin-service)

### 2.1 Domain Import Handlers

- [x] 2.1.1 **Create MenuTreeImportHandler**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/menu/MenuTreeImportHandler.kt` | Action: [NEW]
  - Base: `TwoPassTreeImportHandler<MenuEntity, Long>` from `base-file-starter`
  - FR: FR-015, FR-005 — Hierarchical menu tree import with self-referencing FK
  - Pattern: Override `extractId`, `extractParentId`, `setParentId`, `updateParentIdSql`
  - Dependencies: `MenuRepository`, `JdbcTemplate`

- [x] 2.1.2 **Create DepartmentTreeImportHandler**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/DepartmentTreeImportHandler.kt` | Action: [NEW]
  - Base: `TwoPassTreeImportHandler<DepartmentEntity, Long>` from `base-file-starter`
  - FR: FR-015, FR-005 — Department tree import with self-referencing FK
  - Dependencies: `DepartmentRepository`, `JdbcTemplate`

- [x] 2.1.3 **Create CommonConfigImportHandler**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/config/CommonConfigImportHandler.kt` | Action: [NEW]
  - Base: `DefaultSimpleImportHandler<SystemConfigEntity, Long>` from `base-file-starter`
  - FR: FR-006, FR-005 — Flat table config import with natural key matching
  - Pattern: Natural key = `config_key`, supports all 4 modes
  - Dependencies: `SystemConfigRepository`

- [x] 2.1.4 **Create FeatureFlagImportHandler**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/config/FeatureFlagImportHandler.kt` | Action: [NEW]
  - Base: `DefaultSimpleImportHandler<FeatureFlagEntity, Long>` from `base-file-starter`
  - FR: FR-006 — Feature flag import with UPSERT default
  - Dependencies: `FeatureFlagRepository`, `StringRedisTemplate` (cache invalidation)

### 2.2 Domain Registry & Controller Enhancement

- [x] 2.2.1 **Create ConfigDomainRegistry**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/ConfigDomainRegistry.kt` | Action: [NEW]
  - FR: FR-001 — Centralized domain discovery and management
  - Pattern: Spring `@Component` auto-discovering `VersionedConfigDomain` and `TableImportHandler` beans
  - Dependencies: `ApplicationContext`

- [x] 2.2.2 **Enhance ConfigManagementController with export/all and import endpoints**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/ConfigManagementController.kt` | Action: [MODIFY]
  - FR: FR-003 (export all multi-sheet), FR-005 (import with mode)
  - Pattern: `GET /api/v1/configs/export/all` → `MultiSheetExcelExportStrategy` + `DynamicJpaMetamodelSheetExtractor`; `POST /api/v1/configs/import` → `RelationalImportCoordinator` with `ImportStrategyMode` param
  - Dependencies: `MultiSheetExcelExportStrategy`, `DynamicJpaMetamodelSheetExtractor`, `RelationalImportCoordinator`

### 2.3 Refactoring: Update Imports

- [x] 2.3.1 **Update system-admin-service import paths after MOVE**
  - File: Multiple files in `services/system-admin-service/src/` | Action: [MODIFY]
  - Pattern: Replace `com.ntt.sysadmin.shared.file.export.*` → `com.ntt.basecore.autoconfigure.file.export.*`; Replace `com.ntt.sysadmin.shared.file.import.*` → `com.ntt.basecore.autoconfigure.file.import.*`
  - Dependencies: All tasks in Phase 1 completed

- [x] 2.3.2 **Remove old shared/file/export/ and shared/file/import/ files**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/` | Action: [DELETE]
  - Pattern: Remove only MOVED classes (SimpleJsonExportStrategy, RelationalJsonExportStrategy, MultiSheetExcelExportStrategy, TopologicalDependencySorter, RelationalImportCoordinator, DefaultSimpleImportHandler, TableImportHandler, ImportStrategyMode)
  - Dependencies: Task 2.3.1 completed (all imports updated)

---

## Phase 3: Security & Resilience

- [x] 3.1 **Integrate ExportSanitizer into MultiSheetExcelExportStrategy**
  - File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/MultiSheetExcelExportStrategy.kt` | Action: [MODIFY]
  - FR: FR-013 — CWE-1236 Formula Injection protection
  - Pattern: Call `ExportSanitizer.sanitize(cellValue)` before `cell.setCellValue()`
  - Dependencies: `ExportSanitizer` (existing class)

- [x] 3.2 **Enable Outbox auto-republish on restart**
  - File: `services/system-admin-service/src/main/resources/application.yml` | Action: [MODIFY]
  - FR: FR-014 — Auto-recovery of incomplete events
  - Pattern: Add `spring.modulith.republish-outstanding-events-on-restart: true`

- [x] 3.3 **Add CONFIG_CIRCULAR_DEPENDENCY and IMPORT_CHECKSUM_MISMATCH error codes**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminErrorCode.kt` | Action: [MODIFY]
  - FR: FR-004 (circular dependency), FR-002 (checksum validation)
  - Error: `CONFIG_CIRCULAR_DEPENDENCY` (SYS_022, 409 CONFLICT), `IMPORT_CHECKSUM_MISMATCH` (SYS_023, 400 BAD_REQUEST)
  - Dependencies: `com.ntt.basecore.exception.base.ErrorCodeBase`

---

## Phase 4: Testing & Verification

- [x] 4.1 **Unit test DynamicJpaMetamodelSheetExtractor**
  - File: `components/base-core/starters/base-file-starter/src/test/kotlin/com/ntt/basecore/autoconfigure/file/export/DynamicJpaMetamodelSheetExtractorTest.kt` | Action: [NEW]
  - FR: FR-003 — Verify auto-generation of sheet definitions from JPA entities
  - Pattern: JUnit 5 with mock `EntityManager` and `Metamodel`

- [x] 4.2 **Unit test TwoPassTreeImportHandler**
  - File: `components/base-core/starters/base-file-starter/src/test/kotlin/com/ntt/basecore/autoconfigure/file/import/TwoPassTreeImportHandlerTest.kt` | Action: [NEW]
  - FR: FR-015 — Verify two-pass import with self-referencing FK
  - Pattern: JUnit 5 with mock `JpaRepository` and `JdbcTemplate`

- [x] 4.3 **Integration test full export/import cycle with SHA-256 verification**
  - File: `services/system-admin-service/src/test/kotlin/com/ntt/sysadmin/versioning/ExportImportCycleIntegrationTest.kt` | Action: [NEW]
  - FR: FR-001, FR-002, FR-003, FR-005 — Full round-trip: export → import → verify data integrity
  - Pattern: `@SpringBootTest` with Testcontainers PostgreSQL
  - Dependencies: `org.testcontainers:postgresql`

- [x] 4.4 **Integration test Menu tree import with self-referencing FK**
  - File: `services/system-admin-service/src/test/kotlin/com/ntt/sysadmin/menu/MenuTreeImportHandlerIntegrationTest.kt` | Action: [NEW]
  - FR: FR-015 — Import 3-level Menu tree without FK violation
  - Pattern: `@SpringBootTest` verifying `SET CONSTRAINTS ALL DEFERRED` and two-pass execution
