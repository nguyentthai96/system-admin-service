# DTO Pattern

_Generated: 2026-09-30_

## Request DTO

- `UpdateDomainConfigRequest` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/tenant/adapter/in/web/dto/DomainConfigDtos.kt`
- `CreateMenuRequest`, `UpdateMenuRequest` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/menu/adapter/in/web/dto/MenuDtos.kt`
- Target Requests for Versioning:
  - `CreateMilestoneRequest(name: String, description: String?, domainScopes: List<String>)`
  - `RollbackConfigRequest(milestoneId: String?, version: Int?, forceOverwrite: Boolean = false)`
  - `ImportConfigRequest(file: MultipartFile, mode: ImportStrategyMode)`

## Response DTO

- `DomainConfigResponse` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/tenant/adapter/in/web/dto/DomainConfigDtos.kt`
- `MenuResponse`, `MenuTreeResponse` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/menu/adapter/in/web/dto/MenuDtos.kt`
- Target Responses for Versioning:
  - `ConfigAuditHistoryResponse(id, domain, version, changedBy, changedAt, changeReason, diffSummary)`
  - `ConfigSnapshotResponse(snapshotId, domain, milestoneId, stateSummary, createdAt)`
  - `ConfigDiffResponse(domain, entityKey, beforeValue, afterValue, changeType)`
  - `ImportResultResponse(jobId, status, totalRows, inserted, updated, failed, errors)`

## Annotations Pattern

- `@field:NotBlank`, `@field:NotNull`, `@field:Size`
- `@JsonInclude(JsonInclude.Include.NON_NULL)`
- Jackson Module Kotlin data classes

## NOT DETECTED

- XML JAXB DTOs: NOT DETECTED (hệ thống thuần JSON REST API).
