# Integration Map

_Generated: 2026-10-01_

## Redis — Cache (StringRedisTemplate)

- Client: `StringRedisTemplate` (Spring Data Redis)
- Protocol: Redis protocol
- Used in:
  - `I18nMessageVersionedDomain` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/impl/I18nMessageVersionedDomain.kt`
    - Operations: `delete("i18n:{locale}:{code}")` on rollback
  - `MenuPermissionService` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/menu/application/MenuPermissionService.kt`
    - Operations: `opsForValue().get/set`, `keys()`, `delete()`
  - `ApiKeyService` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/apipartner/ApiKeyService.kt`
    - Operations: `opsForHash().putAll()`, `convertAndSend()` (Pub/Sub publish)

## Redis — Pub/Sub (RedisMessageListenerContainer)

- Client: `RedisMessageListenerContainer` + `MessageListenerAdapter` (Spring Data Redis)
- Protocol: Redis Pub/Sub
- Used in:
  - `SecurityRuleCacheConfig` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/security/SecurityRuleCacheConfig.kt`
    - Channel: `auth-service:security-rules:changed`
    - Listener: `SecurityRuleCacheListener` → `dynamicAuthorizationManager.evictCache()`

## Redis — Caffeine L1 (Local Cache)

- Client: `com.github.benmanes.caffeine.cache.Cache` (Caffeine)
- Protocol: In-memory
- Used in:
  - `DatabaseMessageSource` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
    - Config: `maximumSize(500)`, `expireAfterWrite(5, MINUTES)`

## PostgreSQL — JPA Repository

- Client: `JpaRepository` (Spring Data JPA)
- Protocol: JDBC/JPA
- Used in:
  - `I18nMessageRepository` (auth-service) — `findByCodeAndLocaleAndIsActiveTrue()`
  - `I18nMessageRepository` (system-admin-service) — `findByCodeAndLocale()`, `findAllByIsActiveTrue()`

## NOT DETECTED

- Kafka / RabbitMQ (no MQ for i18n)
- REST API clients for i18n (no inter-service HTTP calls)
- WebClient / FeignClient for i18n
