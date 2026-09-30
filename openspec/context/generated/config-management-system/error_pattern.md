# Error Handling Pattern

_Generated: 2026-09-30_

## Exception Classes

- `SysAdminException` extends `BusinessException` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminExceptions.kt`
- `CircularReferenceException` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminExceptions.kt`
- `MaxDepthExceededException` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminExceptions.kt`
- Target Exception Classes for Versioning:
  - `ConfigRollbackConflictException(message: String, conflicts: List<String>) : SysAdminException`
  - `InvalidSnapshotException(message: String) : SysAdminException`
  - `ImportProcessingException(message: String, errors: List<String>) : SysAdminException`

## Error Code Format

- Class: `SysAdminErrorCode` (implements `ErrorCodeBase` from `base-core`) — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminErrorCode.kt`
- Pattern: `SYS_XXX` (3 digits)
- Examples:
  - `CONFIG_NOT_FOUND` → `SYS_015`
  - `INVALID_CONFIG_TYPE` → `SYS_016`
  - `AUDIT_QUERY_FAILED` → `SYS_017`
- Target Error Codes to Add:
  - `SNAPSHOT_NOT_FOUND` → `SYS_019`
  - `ROLLBACK_CONFLICT` → `SYS_020`
  - `IMPORT_VALIDATION_FAILED` → `SYS_021`

## Global Exception Handler

- `SysAdminControllerAdvice` extends base-core `@RestControllerAdvice` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminControllerAdvice.kt`
- Standard Response: RFC 7807 ProblemDetail / `BaseResponse<Any>` with `code`, `message`, `timestamp`

## NOT DETECTED

- SOAP Faults: NOT DETECTED.
