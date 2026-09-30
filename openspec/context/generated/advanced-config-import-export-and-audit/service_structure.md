# Service Structure

_Generated: 2026-09-30_

## base-core (starters/base-file-starter)

### Detected Packages

- `com.ntt.basecore.domain.file`: Định nghĩa hợp đồng trừu tượng xuất file (`ExportStrategy`, `ExportFormat`, `ExportConfig`, `ColumnDefinition`).
- `com.ntt.basecore.autoconfigure.file.export`: Chứa các bộ chuyển đổi định dạng tệp tin (`ExcelExportStrategy`, `CsvExportStrategy`, `ExportSanitizer`, `ExportService`).
- `com.ntt.basecore.autoconfigure.file.import`: Chứa dịch vụ nạp file không đồng bộ (`ImportService`, `ImportProgressNotifier`).

### Target Packages (to be added)

- `com.ntt.basecore.autoconfigure.file.export`: Bổ sung `SimpleJsonExportStrategy`, `RelationalJsonExportStrategy`, `MultiSheetExcelExportStrategy`, `DynamicJpaMetamodelSheetExtractor`.
- `com.ntt.basecore.autoconfigure.file.import`: Bổ sung `TopologicalDependencySorter`, `TableImportHandler`, `DefaultSimpleImportHandler`, `TwoPassTreeImportHandler`, `RelationalImportCoordinator`.

## system-admin-service

### Detected Packages

- `com.ntt.sysadmin.versioning.adapter.in.web`: REST Controllers và DTOs cho Milestone, Snapshot, Diff, Rollback và Export/Import (`ConfigManagementController`).
- `com.ntt.sysadmin.versioning.application`: Dịch vụ điều phối nghiệp vụ snapshot (`ConfigSnapshotManager`).
- `com.ntt.sysadmin.versioning.domain`: Registry quản lý các domain cấu hình (`ConfigDomainRegistry`, `VersionedConfigDomain`).
- `com.ntt.sysadmin.versioning.listener`: Outbox event listeners (`ConfigDomainEventListener`, `ConfigDomainChangedEvent`).
- `com.ntt.sysadmin.versioning.buffer`: Bộ đệm vi mẻ bộ nhớ RAM có vòng đời SmartLifecycle (`BatchAuditCollector`).
- `com.ntt.sysadmin.versioning.storage`: SPI và JPA Repositories lưu trữ kiểm toán (`ConfigAuditStorageProvider`, `PostgreSqlJsonbAuditStorageProvider`).
- `com.ntt.sysadmin.menu`: Quản lý danh mục menu phân tầng (`MenuController`, `MenuPermissionService`, `MenuEntities`).
- `com.ntt.sysadminservice.organization`: Quản lý sơ đồ phòng ban và chức danh (`DepartmentController`, `PositionController`, `DepartmentEntity`).
- `com.ntt.sysadminservice.config`: Quản lý cấu hình chung và Feature Flags (`FeatureFlagEntity`, `FeatureFlagService`).

### Naming Convention

- Controller: `*Controller.kt` (gắn annotation `@RestController`, `@RequestMapping("/api/v1/...")`)
- DTO: `*Request.kt`, `*Response.kt` (Kotlin data class)
- Service/Manager: `*Service.kt`, `*Manager.kt`
- Entity: `*Entity.kt` (kế thừa SnowflakeEntity / TreeEntity)
- Repository: `*Repository.kt` (kế thừa Spring Data `JpaRepository`)
- Event: `*Event.kt`
- Handler/Strategy: `*Strategy.kt`, `*Handler.kt`
