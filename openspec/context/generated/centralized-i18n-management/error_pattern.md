# Error Handling Pattern

_Generated: 2026-10-01_

## Exception Classes

- `SysAdminException` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminExceptions.kt`
  - extends: `BusinessException` (base-core)
  - Subclasses: `CircularReferenceException`, `SnapshotNotFoundException`, `RollbackConflictException`, `ImportValidationException`

- `SysAdminControllerAdvice` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminControllerAdvice.kt`
  - extends: `BaseControllerAdvice` (base-core)
  - Uses: `resolveMessage()` from parent

## Error Code Format

- `SysAdminErrorCode` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminErrorCode.kt`
  - Pattern: `enum class` with `(errorCode, msgCode, description, httpStatus)`
  - Example: `CIRCULAR_REFERENCE("SYS_001", "sysadmin.circular_reference", "Circular reference detected", HttpStatus.BAD_REQUEST)`
  - Delegates to `ErrorCodeBase` via `toErrorCodeBase()`

## i18n Resolution Chain (Current)

```
BaseControllerAdvice.resolveMessage(msgCode, args, fallback)
  → MessageSource?.getMessage(msgCode, args, locale)
  → NoSuchMessageException → return fallback
```

## NOT DETECTED

- Custom error response wrapper (uses Spring `ProblemDetail` — RFC 7807)
