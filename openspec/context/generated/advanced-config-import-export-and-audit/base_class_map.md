# Base Class Map

_Generated: 2026-09-30 | Services: base-file-starter, system-admin-service_

## Controller

- `ConfigManagementController` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/ConfigManagementController.kt`
- `DomainConfigController` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/tenant/adapter/in/web/DomainConfigController.kt`
- `MenuController` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/menu/MenuController.kt`
- `DepartmentController` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/adapter/in/web/DepartmentController.kt`

## Base Strategy & Framework Classes

- `ExportStrategy<T>` (interface) — `components/base-core/src/main/kotlin/com/ntt/basecore/domain/file/ExportStrategy.kt`
- `ExcelExportStrategy<T>` (implements ExportStrategy<T>) — `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/ExcelExportStrategy.kt`
- `CsvExportStrategy<T>` (implements ExportStrategy<T>) — `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/CsvExportStrategy.kt`
- `ExportSanitizer` (utility for CWE-1236) — `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/ExportSanitizer.kt`
- `SimpleJsonExportStrategy<T>` (implements ExportStrategy<T>) — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/SimpleJsonExportStrategy.kt`
- `RelationalJsonExportStrategy` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/RelationalJsonExportStrategy.kt`
- `MultiSheetExcelExportStrategy` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/MultiSheetExcelExportStrategy.kt`

## Handler & Coordinator

- `TableImportHandler<E>` (interface) — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/import/RelationalImportCoordinator.kt`
- `DefaultSimpleImportHandler<T, ID>` (implements TableImportHandler<T>) — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/import/RelationalImportCoordinator.kt`
- `RelationalImportCoordinator` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/import/RelationalImportCoordinator.kt`

## Storage SPI & Buffer

- `ConfigAuditStorageProvider` (interface) — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/storage/ConfigAuditStorageProvider.kt`
- `PostgreSqlJsonbAuditStorageProvider` (implements ConfigAuditStorageProvider) — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/storage/ConfigAuditStorageProvider.kt`
- `BatchAuditCollector` (implements SmartLifecycle, DisposableBean) — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/buffer/BatchAuditCollector.kt`

## Base Entity Hierarchy

- `TreeEntity<ID>` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/persistence/TreeEntity.kt`
- `ConfigMilestoneEntity` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/storage/entity/ConfigMilestoneEntity.kt`
- `ConfigSnapshotEntity` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/storage/entity/ConfigSnapshotEntity.kt`

## NOT DETECTED

- Client / Gateway (Tính năng nội bộ admin quản trị cấu hình, không gọi ra external bank payment gateway)
