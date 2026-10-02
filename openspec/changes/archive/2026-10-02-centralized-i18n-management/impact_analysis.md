# Impact Analysis: centralized-i18n-management

_Generated: 2026-10-01 | Type: MAINTENANCE | Services: base-core, system-admin-service, auth-service_

## 1. Core Files

| # | File | Service | Action | FR |
|---|------|---------|--------|-----|
| 1 | [`I18nAutoConfiguration.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/configuration/I18nAutoConfiguration.kt) | base-core | MODIFY | FR-007 |
| 2 | [`I18nMessageVersionedDomain.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/impl/I18nMessageVersionedDomain.kt) | system-admin-service | MODIFY | FR-013 |
| 3 | [`I18nConfig.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/I18nConfig.kt) | auth-service | MODIFY | FR-009 |
| 4 | [`GlobalExceptionHandler.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt) | auth-service | MODIFY | FR-016 |
| 5 | [`DatabaseMessageSource.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt) | auth-service | REMOVE | FR-009 |
| 6 | [`I18nMessageEntity.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageEntity.kt) | auth-service | REMOVE | FR-001 |
| 7 | [`I18nMessageRepository.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageRepository.kt) | auth-service | REMOVE | FR-001 |

## 2. Call Tree (Affected Symbols)

### I18nAutoConfiguration.messageSource()
```
I18nAutoConfiguration.messageSource()
  └→ @ConditionalOnMissingBean(MessageSource::class) — auto-provides file bundle MessageSource
  └→ Consumed by: BaseControllerAdvice(validator, messageSource?)
  └→ Overridden by: I18nConfig.messageSource() in auth-service (@Primary)
  └→ Impact: MODIFY to add RedisMessageSource → new auto-config class needed
```

### I18nMessageVersionedDomain.applyRollbackState()
```
I18nMessageVersionedDomain.applyRollbackState()
  └→ Called by: ConfigSnapshotManager.rollbackToSnapshot() (line 297)
  └→ Uses: redisTemplate.delete("i18n:{locale}:{code}") — MUST change to hash ops
  └→ Impact: Redis key pattern change (individual keys → hash per locale)
```

### I18nConfig.messageSource()
```
I18nConfig.messageSource()
  └→ Creates: DatabaseMessageSource(i18nMessageRepository)
  └→ Injected into: AuthControllerAdvice (via Spring DI)
  └→ Used by: resolveMessage() in handleAuthException()
  └→ Impact: REMOVE DatabaseMessageSource, remove @Primary, rely on base-core auto-config
```

### AuthControllerAdvice (GlobalExceptionHandler.kt)
```
AuthControllerAdvice(validator, messageSource)
  └→ extends: BaseControllerAdvice(validator) ← BUG: parent.messageSource = null
  └→ Calls: resolveMessage(msgCode, args, fallback) — parent method
  └→ parent.resolveMessage() uses parent.messageSource (null) → always returns fallback
  └→ Impact: Fix constructor to pass messageSource to parent
```

## 3. Blast Radius

| Depth | Symbol | Service | Risk |
|-------|--------|---------|------|
| d=0 | `I18nAutoConfiguration` | base-core | 🟢 Low — add new auto-config, existing untouched |
| d=0 | `I18nMessageVersionedDomain` | system-admin-service | 🟡 Medium — rollback logic change |
| d=1 | `ConfigSnapshotManager.rollbackToSnapshot()` | system-admin-service | 🟡 Medium — calls applyRollbackState() |
| d=1 | `ConfigSnapshotManagerTest` | system-admin-service | 🟢 Low — test stub |
| d=0 | `I18nConfig` | auth-service | 🟢 Low — simplify (remove code) |
| d=0 | `AuthControllerAdvice` | auth-service | 🟢 Low — constructor param fix |
| d=1 | `DatabaseMessageSource` | auth-service | 🟢 Low — file removal |
| d=1 | `I18nMessageEntity` | auth-service | 🟢 Low — file removal |
| d=1 | `I18nMessageRepository` | auth-service | 🟢 Low — file removal, no other consumers |

**Overall Risk: 🟡 Medium** — chủ yếu từ rollback logic change. Tất cả changes khác là low risk.

## 4. Reuse Map

| Pattern | Source | Reuse Target | Decision |
|---------|--------|-------------|----------|
| Redis Pub/Sub listener | `SecurityRuleCacheConfig` (auth-service) | `I18nCacheInvalidationListener` (base-core) | **EXTRACT** — pattern reusable, implement in base-core |
| Redis Hash operations | `ApiKeyService.syncToRedis()` (system-admin-service) | `I18nRedisSyncService` (system-admin-service) | **REUSE** — same `StringRedisTemplate.opsForHash()` pattern |
| Caffeine cache | `DatabaseMessageSource` (auth-service) | `RedisMessageSource` (base-core) | **REUSE** — same Caffeine builder pattern, different config |
| AbstractMessageSource | `DatabaseMessageSource` (auth-service) | `RedisMessageSource` (base-core) | **REUSE** — same parent class, different backing store |
| @ConditionalOnMissingBean | `I18nAutoConfiguration` (base-core) | `I18nCacheAutoConfiguration` (base-core) | **REUSE** — same pattern |
| VersionedConfigDomain SPI | `I18nMessageVersionedDomain` (system-admin-service) | Same file (modify) | **MODIFY** — add hash ops to existing |

## 5. Context Snapshot

### Key Constraints
- `I18nAutoConfiguration.messageSource()` has `@ConditionalOnMissingBean(MessageSource::class)` — new auto-config must also use this or higher-priority condition
- `I18nConfig.messageSource()` in auth-service is `@Primary` — removing it allows base-core auto-config to take effect
- `AuthControllerAdvice` calls `resolveMessage()` which is parent's method — parent needs `messageSource`
- `ConfigSnapshotManager` calls `domain.applyRollbackState()` at line 297 — any signature change would break interface

### Dependencies to Verify
- `build.gradle.kts` (base-core): needs `caffeine` dependency
- `build.gradle.kts` (base-core): needs `spring-boot-starter-data-redis` as optional dependency
- `build.gradle.kts` (auth-service): `caffeine` dependency can remain (other caches may use it)
