# DTO Pattern

_Generated: 2026-09-30_

## Request DTO

- `CreateMilestoneRequest` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/dto/ConfigVersioningDtos.kt`
  - Annotations: `@field:NotBlank`, `@field:NotEmpty`
  - Fields: `name: String`, `description: String?`, `domainNames: List<String>`
- `RollbackRequest` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/dto/ConfigVersioningDtos.kt`
  - Fields: `forceOverwrite: Boolean = false`, `reason: String?`

## Response DTO

- `MilestoneResponse` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/dto/ConfigVersioningDtos.kt`
  - Fields: `id: String`, `name: String`, `description: String?`, `createdBy: String?`, `createdAt: Instant`, `status: String`, `snapshots: List<SnapshotSummaryResponse>`
- `SnapshotSummaryResponse` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/dto/ConfigVersioningDtos.kt`
  - Fields: `id: String`, `domainName: String`, `checksumSha256: String`, `recordCount: Int`, `createdAt: Instant`
- `ConfigDiffResponse` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/dto/ConfigVersioningDtos.kt`
  - Fields: `domainName: String`, `snapshotId: String`, `snapshotCreatedAt: Instant`, `addedCount: Int`, `removedCount: Int`, `modifiedCount: Int`, `entities: List<EntityDiffResponse>`
- `EntityDiffResponse` & `FieldDiffResponse` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/dto/ConfigVersioningDtos.kt`
  - Fields: `naturalKey: String`, `diffType: String`, `fieldDiffs: List<FieldDiffResponse>`
- `RollbackResponse` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/dto/ConfigVersioningDtos.kt`
  - Fields: `domainName: String`, `snapshotId: String`, `milestoneId: String?`, `recordsRestored: Int`, `conflictsOverridden: Int`, `executedAt: Instant`
- `ImportSummaryResponse` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/dto/ConfigVersioningDtos.kt`
  - Fields: `totalRecordsProcessed: Int`, `tablesProcessed: List<String>`, `mode: String`, `success: Boolean`, `message: String`

## Payload Containers

- `RelationalExportPayload<R>` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/RelationalJsonExportStrategy.kt`
  - Fields: `schemaVersion: String`, `domainName: String`, `exportedAt: Instant`, `checksumSha256: String`, `data: R`

## NOT DETECTED

- XML DTOs (Dự án chuẩn hóa 100% JSON và Multipart form-data)
