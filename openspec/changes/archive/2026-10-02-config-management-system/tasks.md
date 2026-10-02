<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "BaseCrudDataFactory", feature_type: "NEWBUILD", transaction_flow: "Command" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
<!-- self-contained: true -->

## 1. Abstract Base Extensions (system-admin-service shared/file)

- [x] 1.1 **Implement SimpleJsonExportStrategy in system-admin-service**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/SimpleJsonExportStrategy.kt` | Action: [NEW]
  - Base: `ExportStrategy<T>` from `com.ntt.basecore.domain.file`
  - FR: FR-002 — Export Dữ liệu Đơn bảng (CSV, Single-sheet Excel, Simple JSON)
  - Pattern: Streaming Jackson `JsonGenerator` writing JSON array directly to `OutputStream`
  - Dependencies: `com.fasterxml.jackson.databind.ObjectMapper`, `com.ntt.basecore.domain.file.ExportConfig`

- [x] 1.2 **Implement RelationalJsonExportStrategy and RelationalExportTemplate**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/RelationalJsonExportStrategy.kt` | Action: [NEW]
  - Base: `ExportStrategy<Any>` from `com.ntt.basecore.domain.file`
  - FR: FR-003 — Export Cấu trúc Quan hệ Phức tạp ra JSON
  - Pattern: Jackson streaming with SHA-256 payload checksum and schema version header
  - Dependencies: `java.security.MessageDigest`, `com.ntt.basecore.domain.file.ExportTemplate`

- [x] 1.3 **Implement MultiSheetExcelExportStrategy and MultiSheetExportTemplate**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/MultiSheetExcelExportStrategy.kt` | Action: [NEW]
  - Base: Independent multi-sheet strategy using shared Apache POI SXSSFWorkbook
  - FR: FR-004 — Export Cấu hình Toàn Miền ra Multi-Sheet Excel
  - Pattern: Apache POI `SXSSFWorkbook(100)` streaming across multiple named sheet tabs
  - Dependencies: `org.apache.poi.xssf.streaming.SXSSFWorkbook`, `com.ntt.basecore.autoconfigure.file.export.ExportSanitizer`

- [x] 1.4 **Implement RelationalImportCoordinator and TableImportHandler hierarchy**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/import/RelationalImportCoordinator.kt` | Action: [NEW]
  - Base: `ImportService` integration from `com.ntt.basecore.autoconfigure.file.import`
  - FR: FR-005, FR-006, FR-007 — Policy-driven import strategies (DELETE_AND_INSERT, UPSERT_MERGE, TRUNCATE_AND_LOAD)
  - Pattern: Topological execution order with `DefaultSimpleImportHandler<T>` fallback
  - Dependencies: `org.springframework.transaction.support.TransactionTemplate`


## 2. Database Schema Migrations (system-admin-service)

- [x] 2.1 **Create Flyway migration for sys_config_milestone and sys_config_snapshot**
  - File: `services/system-admin-service/src/main/resources/db/migration/V9__config_management_tables.sql` | Action: [NEW]
  - Base: Flyway PostgreSQL standard migration
  - FR: FR-008, FR-009 — Audit Trail & Milestone Snapshot persistence
  - Pattern: PostgreSQL DDL with JSONB state_payload, GIN index, and FK to milestone
  - Dependencies: Existing PostgreSQL schema

## 3. Core Versioning Framework (system-admin-service)

- [x] 3.1 **Define VersionedConfigDomain interface and domain registry**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/VersionedConfigDomain.kt` | Action: [NEW]
  - Base: High abstraction SPI for configuration domains
  - FR: FR-001 — Quản lý Cấu hình Đa Miền Tập trung
  - Pattern: Generic interface with `domainName`, `entityClass`, `naturalKeyExtractor`, `fetchCurrentState`, `applyRollbackState`
  - Dependencies: `com.ntt.basecore.domain.file.ExportTemplate`

- [x] 3.2 **Implement ConfigAuditStorageProvider SPI and PostgreSQL JSONB provider**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/storage/ConfigAuditStorageProvider.kt` | Action: [NEW]
  - Base: Pluggable storage SPI with default `PostgreSqlJsonbAuditStorageProvider`
  - FR: FR-008, Non-functional Scalability — Polyglot Database Support
  - Pattern: Spring Data JPA repository querying JSONB column with GIN index
  - Dependencies: `org.springframework.boot.autoconfigure.condition.ConditionalOnProperty`

- [x] 3.3 **Implement BatchAuditCollector buffer with graceful shutdown flush**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/buffer/BatchAuditCollector.kt` | Action: [NEW]
  - Base: `org.springframework.context.SmartLifecycle`, `org.springframework.beans.factory.DisposableBean`
  - FR: FR-013 — Gom Batch Ghi ngầm và Graceful Shutdown
  - Pattern: In-memory `LinkedBlockingQueue` with scheduled flush (100 items / 500ms) and shutdown flush
  - Dependencies: `java.util.concurrent.ScheduledExecutorService`

- [x] 3.4 **Implement ConfigDomainEventListener using Spring Modulith Outbox**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/listener/ConfigDomainEventListener.kt` | Action: [NEW]
  - Base: `@ApplicationModuleListener` from `org.springframework.modulith.events`
  - FR: FR-011, FR-012 — Asynchronous non-blocking events with Zero-Loss Outbox
  - Pattern: Async listener receiving `ConfigDomainChangedEvent` after transaction commit
  - Dependencies: `org.springframework.modulith.events.ApplicationModuleListener`

- [x] 3.5 **Implement ConfigSnapshotManager for snapshot, diff, conflict detection and rollback**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/application/ConfigSnapshotManager.kt` | Action: [NEW]
  - Base: Domain application service coordinating versioning lifecycle
  - FR: FR-009, FR-010, FR-014 — Milestone Snapshot, Diff Comparison, and Conflict-Aware Rollback
  - Error: `SysAdminErrorCode.ROLLBACK_CONFLICT`, `SysAdminErrorCode.SNAPSHOT_NOT_FOUND`
  - Pattern: JSON diff calculation with Jackson, comparing entity `@Version`/`updated_at` timestamps
  - Dependencies: `com.fasterxml.jackson.databind.ObjectMapper`, `VersionedConfigDomain` registry

## 4. Domain Implementations & Onboarding

- [x] 4.1 **Implement SystemConfigVersionedDomain for dynamic system parameters**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/impl/SystemConfigVersionedDomain.kt` | Action: [NEW]
  - Base: `VersionedConfigDomain<SystemConfigEntity>`
  - FR: FR-001, FR-006 — System parameter CRUD, upsert import, and rollback
  - Pattern: Spring `@Component` implementing state fetch and upsert rollback
  - Dependencies: `SystemConfigRepository`

- [x] 4.2 **Implement I18nMessageVersionedDomain for multilingual messages**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/impl/I18nMessageVersionedDomain.kt` | Action: [NEW]
  - Base: `VersionedConfigDomain<I18nMessageEntity>`
  - FR: FR-001, FR-006 — Hybrid i18n override management, import, and rollback
  - Pattern: Natural key `msg_key` matching with database override persistence
  - Dependencies: `I18nMessageRepository`, `StringRedisTemplate`

- [x] 4.3 **Extend MenuVersionedDomain with sequence preservation**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/impl/MenuVersionedDomain.kt` | Action: [NEW]
  - Base: `VersionedConfigDomain<MenuEntity>`
  - FR: FR-001, FR-005 — Hierarchical menu tree export, sequence-preserving rollback/import
  - Pattern: Topological tree traversal using `TreeBuilder`, re-indexing `sort_order` and hierarchy paths
  - Dependencies: `MenuRepository`, `com.ntt.sysadminservice.shared.util.TreeBuilder`

## 5. Web API & Exception Handling (system-admin-service)

- [x] 5.1 **Define DTOs for milestones, snapshots, diffs, and rollback requests/responses**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/dto/VersioningDtos.kt` | Action: [NEW]
  - Base: Kotlin data classes with Bean Validation
  - FR: FR-009, FR-010, FR-014 — API Request/Response contract
  - Pattern: `@field:NotBlank`, `@field:NotNull`, `MilestoneResponse`, `ConfigDiffResponse`, `RollbackRequest`
  - Dependencies: `jakarta.validation.constraints.*`

- [x] 5.2 **Implement ConfigManagementController exposing unified REST endpoints**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/ConfigManagementController.kt` | Action: [NEW]
  - Base: Spring `@RestController` under `/api/v1/configs`
  - FR: FR-001, FR-002, FR-004, FR-009, FR-010 — RESTful endpoints for export, import, milestones, diff, rollback
  - Pattern: Standard Spring REST controller returning `BaseResponse<T>`
  - Dependencies: `ConfigSnapshotManager`, `ExportService`, `RelationalImportCoordinator`

- [x] 5.3 **Update SysAdminErrorCode and SysAdminExceptions for versioning errors**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminErrorCode.kt` | Action: [MODIFY]
  - Base: `ErrorCodeBase` from `com.ntt.basecore.exception.base`
  - FR: FR-010, FR-014 — Proper error codes for rollback conflict and snapshot errors
  - Error: `SNAPSHOT_NOT_FOUND` (SYS_019), `ROLLBACK_CONFLICT` (SYS_020), `IMPORT_VALIDATION_FAILED` (SYS_021)
  - Dependencies: `com.ntt.basecore.exception.base.ErrorCodeBase`

## 6. Integration Testing & Verification

- [x] 6.1 **Write unit and slice tests for VersionedConfigDomain and rollback conflict detection**
  - File: `services/system-admin-service/src/test/kotlin/com/ntt/sysadmin/versioning/ConfigSnapshotManagerTest.kt` | Action: [NEW]
  - Base: JUnit 5, `@SpringBootTest`, `@MockitoBean`
  - FR: FR-010, FR-014 — Verification of clean rollback, conflict warning, and force override
  - Pattern: Unit tests asserting 409 Conflict when post-snapshot entity updated

- [x] 6.2 **Write integration test verifying Zero-Loss Event recovery with Spring Modulith**
  - File: `services/system-admin-service/src/test/kotlin/com/ntt/sysadmin/versioning/ZeroLossOutboxIntegrationTest.kt` | Action: [NEW]
  - Base: `@SpringBootTest`, Spring Modulith test support
  - FR: FR-012 — Zero-loss guarantee via `EVENT_PUBLICATION` table
  - Pattern: Verify incomplete events in `EVENT_PUBLICATION` are resumed upon container reboot
  - Dependencies: `org.springframework.modulith.test.ApplicationModuleTest`
