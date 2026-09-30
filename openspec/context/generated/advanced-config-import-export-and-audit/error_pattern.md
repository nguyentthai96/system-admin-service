# Error Handling Pattern

_Generated: 2026-09-30_

## Exception Classes

- `SysAdminException` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminExceptions.kt`
  - Base business exception extending `RuntimeException`
  - Constructor: `(errorCode: SysAdminErrorCode, customMessage: String? = null, cause: Throwable? = null)`
- `ImportValidationException` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminExceptions.kt`
  - Subclass thrown when import file validation or checksum validation fails
- `SnapshotConflictException` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/shared/exception/SysAdminExceptions.kt`
  - Thrown during rollback when conflicting modifications are detected and forceOverwrite is false

## Error Code Format

- Standard: `SysAdminErrorCode` Enum
  - Format: `<DOMAIN>_<SUBDOMAIN>_<REASON>` (e.g. `CONFIG_DOMAIN_NOT_FOUND`, `SNAPSHOT_NOT_FOUND`, `IMPORT_VALIDATION_ERROR`)
  - HTTP Status Mapping: RFC 7807 `ProblemDetail` via `SysAdminControllerAdvice`
    - `400 BAD_REQUEST`: Validation errors, Invalid mode, Checksum mismatch
    - `404 NOT_FOUND`: Domain not registered, Milestone/Snapshot not found
    - `409 CONFLICT`: Snapshot rollback conflict, Circular dependency in topological sort
    - `500 INTERNAL_SERVER_ERROR`: I/O stream failures, Outbox serialization failure

## NOT DETECTED

- Raw unhandled runtime exceptions leaking stack traces to REST consumers (Project enforces `@ControllerAdvice` ProblemDetail responses)
