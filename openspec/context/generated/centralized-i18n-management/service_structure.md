# Service Structure

_Generated: 2026-10-01_

## base-core

### Detected Packages
- `configuration/`: Auto-configurations (`I18nAutoConfiguration`, `BaseCoreAutoConfiguration`)
- `domain/web/`: `BaseControllerAdvice`, `BaseController`
- `domain/service/`: `AbstractCrudService`, `AbstractSpecificationCrudService`
- `exception/`: `BusinessException`, `NotFoundException`
- `exception/base/`: `ErrorCodeBase`

### Not Found
- `i18n/`: ← needs to be CREATED for `RedisMessageSource`

### Naming Convention
- Auto-config: `*AutoConfiguration`
- Base classes: `Base*`, `Abstract*`
- Controller advice: `*ControllerAdvice`

## auth-service

### Detected Packages
- `shared/i18n/`: `DatabaseMessageSource`, `I18nMessageEntity`, `I18nMessageRepository` ← TO BE REMOVED
- `shared/config/`: `I18nConfig` ← TO BE MODIFIED
- `shared/security/`: `SecurityRuleCacheConfig` ← REFERENCE pattern for Redis Pub/Sub
- `shared/exception/`: `AuthControllerAdvice`, `AuthException`, `AuthErrorCode`

### Naming Convention
- Config: `*Config`
- Entity: `*Entity`
- Repository: `*Repository`

## system-admin-service

### Detected Packages
- `versioning/domain/entity/`: `I18nMessageEntity`, `I18nMessageRepository` ← DATA OWNER
- `versioning/domain/impl/`: `I18nMessageVersionedDomain` ← TO BE MODIFIED
- `versioning/application/`: `ConfigSnapshotManager` ← orchestrator
- `shared/exception/`: `SysAdminControllerAdvice`, `SysAdminException`, `SysAdminErrorCode`
- `menu/application/`: `MenuPermissionService` (Redis pattern reference)
- `apipartner/`: `ApiKeyService` (Redis Pub/Sub pattern reference)

### Naming Convention
- Versioned domain: `*VersionedDomain`
- Service: `*Service`
- Adapter: `*Adapter`
