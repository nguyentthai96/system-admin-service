# Base Class Map

_Generated: 2026-09-30 | Services: system-admin-service, base-core_

## Controller

- Standard Spring Boot `@RestController` (Không có Abstract Controller base class)
  - `MenuController` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/menu/MenuController.kt`
  - `DomainConfigController` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/tenant/adapter/in/web/DomainConfigController.kt`
  - `DepartmentController` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/organization/adapter/in/web/DepartmentController.kt`

## Entity Base & Tree Persistence

- `SnowflakePersistentAuditableEntity` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/model/id/SnowflakePersistentAuditableEntity.kt`
- `TreeEntity` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/persistence/TreeEntity.kt`
- `TreeBuilder` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/util/TreeBuilder.kt`

## File Export & Import Base

- `ExportStrategy<T>` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/domain/file/ExportStrategy.kt`
- `ExportTemplate<T>` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/domain/file/ExportTemplate.kt`
- `ExportService` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/ExportService.kt`
- `ExcelExportStrategy<T>` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/ExcelExportStrategy.kt`
- `CsvExportStrategy<T>` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/CsvExportStrategy.kt`
- `ExportSanitizer` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/ExportSanitizer.kt`
- `ImportService` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/import/ImportService.kt`
- `ImportRowMapper<T>` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/domain/file/ImportStrategy.kt`
- `ImportRowValidator<T>` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/domain/file/ImportStrategy.kt`
- `ImportProgressNotifier` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/domain/file/ImportProgressNotifier.kt`

## Event & Outbox Infrastructure

- `EventPublicationAutoConfiguration` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-modulith-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/modulith/event/EventPublicationAutoConfiguration.kt`
- `ModulithAutoConfiguration` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-modulith-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/modulith/ModulithAutoConfiguration.kt`

## Factory

- `BatchImportJobFactory` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/batch/BatchImportJobFactory.kt`

## NOT DETECTED

- Base Transaction Handler: NOT DETECTED (dịch vụ sử dụng direct `@Service` + `@Transactional` thay vì Handler pattern của CPBank NServer).
- Client / Gateway Base: NOT DETECTED.
