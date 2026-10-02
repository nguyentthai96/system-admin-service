# Design: centralized-i18n-management

_Type: MAINTENANCE | Flow: Command + Query | Date: 2026-10-01_

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                     base-core (shared library)                │
│  ┌───────────────────────┐   ┌────────────────────────────┐  │
│  │  I18nCacheAutoConfig   │   │  I18nAutoConfiguration     │  │
│  │  @ConditionalOnBean    │   │  (existing — untouched)    │  │
│  │  (RedisConnectionFact) │   │  file bundle fallback      │  │
│  │          │              │   └────────────────────────────┘  │
│  │          ▼              │                                   │
│  │  ┌──────────────────┐  │   ┌────────────────────────────┐  │
│  │  │ RedisMessageSource│  │   │ I18nCacheInvalidation     │  │
│  │  │ L1: Caffeine      │──┼──▶│ Listener                  │  │
│  │  │ L2: Redis Hash    │  │   │ Channel: i18n:invalidation│  │
│  │  │ L3: File bundle   │  │   └────────────────────────────┘  │
│  │  └──────────────────┘  │                                   │
│  └───────────────────────┘                                    │
└──────────────────────────────────────────────────────────────┘
                    ↑ dependency
┌──────────────────────────────────────────────────────────────┐
│              system-admin-service (DATA OWNER)                │
│  ┌──────────────────────┐   ┌──────────────────────────────┐ │
│  │ I18nRedisSyncService │   │ I18nMessageVersionedDomain   │ │
│  │ - fullSync()         │   │ (existing — MODIFY)          │ │
│  │ - syncSingle()       │   │ - applyRollbackState()       │ │
│  │ - publishInvalidation│   │   → hash ops + publish       │ │
│  │ - @EventListener     │   └──────────────────────────────┘ │
│  │ - @Scheduled retry   │                                    │
│  └──────────────────────┘                                    │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│              auth-service (CONSUMER — migration)              │
│  ┌──────────────────────┐                                    │
│  │ I18nConfig (MODIFY)  │   REMOVED:                         │
│  │ - remove @Primary    │   ✗ DatabaseMessageSource           │
│  │ - remove messageSource│   ✗ I18nMessageEntity              │
│  │ - keep localeResolver│   ✗ I18nMessageRepository           │
│  └──────────────────────┘                                    │
│  ┌──────────────────────┐                                    │
│  │ AuthControllerAdvice │   FIX: pass messageSource to parent │
│  │ (MODIFY constructor) │                                    │
│  └──────────────────────┘                                    │
└──────────────────────────────────────────────────────────────┘
```

## 2. Component Design

### 2.1 RedisMessageSource (base-core — NEW)

**Package**: `com.ntt.basecore.i18n`
**Extends**: `org.springframework.context.support.AbstractMessageSource`

```kotlin
// Key design decisions:
// - L1 Caffeine: expireAfterWrite=10min, maxSize=2000
// - L2 Redis: HGET i18n:data:{locale} {code}
// - Fallback: parent MessageSource (file bundles)
// - Graceful degradation: Redis down → log WARN → null → parent chain
```

| Method | Purpose |
|--------|---------|
| `resolveCode(code, locale)` | L1 → L2 → null (parent handles) |
| `invalidateKey(code, locale)` | Evict specific L1 entry |
| `invalidateAll()` | Clear all L1 entries |

### 2.2 I18nCacheInvalidationListener (base-core — NEW)

**Package**: `com.ntt.basecore.i18n`
**Implements**: `org.springframework.data.redis.connection.MessageListener`

```kotlin
// Message format from Pub/Sub channel "i18n:invalidation":
// - Selective:  {"type":"SINGLE","code":"auth.error.xxx","locale":"en"}
// - Full:       {"type":"FULL"}
```

| Type | Action |
|------|--------|
| `SINGLE` | `redisMessageSource.invalidateKey(code, locale)` |
| `FULL` | `redisMessageSource.invalidateAll()` |

### 2.3 I18nCacheAutoConfiguration (base-core — NEW)

**Package**: `com.ntt.basecore.configuration`

```kotlin
@AutoConfiguration(after = [I18nAutoConfiguration::class])
@ConditionalOnBean(RedisConnectionFactory::class)
class I18nCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageSource::class)
    fun redisMessageSource(
        redisTemplate: StringRedisTemplate,
        @Autowired(required = false) fileBundleSource: ReloadableResourceBundleMessageSource?
    ): RedisMessageSource

    @Bean
    fun i18nCacheInvalidationListener(redisMessageSource: RedisMessageSource): I18nCacheInvalidationListener

    @Bean
    fun i18nCacheListenerContainer(
        connectionFactory: RedisConnectionFactory,
        listener: I18nCacheInvalidationListener
    ): RedisMessageListenerContainer
}
```

**Condition logic:**
- `RedisConnectionFactory` bean exists → auto-configure Redis-backed MessageSource
- `@ConditionalOnMissingBean(MessageSource::class)` → service can override
- `I18nAutoConfiguration` still provides file bundle as fallback parent

### 2.4 I18nRedisSyncService (system-admin-service — NEW)

**Package**: `com.ntt.sysadmin.i18n.application`

| Method | Trigger | Action |
|--------|---------|--------|
| `fullSync()` | `@EventListener(ApplicationReadyEvent)` | Read all active from DB → `HSET` per locale → `EXPIRE` 10 days → `INCR version` |
| `syncSingle(code, locale, message)` | Called after CRUD ops | `HSET i18n:data:{locale} {code} {msg}` → `INCR version` → publish `SINGLE` |
| `deleteSingle(code, locale)` | Called after delete ops | `HDEL i18n:data:{locale} {code}` → `INCR version` → publish `SINGLE` |
| `publishFullInvalidation()` | Called after batch/rollback | `INCR version` → publish `FULL` |
| `scheduledRetrySync()` | `@Scheduled(fixedDelay=180000)` | If `!synced` → retry `fullSync()` |

### 2.5 I18nMessageVersionedDomain — MODIFY

**Change**: `applyRollbackState()` lines 68-73

```diff
- // Invalidate Redis cache for updated message
- try {
-     redisTemplate.delete("i18n:${snapshotItem.locale}:${snapshotItem.code}")
- } catch (ex: Exception) {
-     log.warn("Redis eviction failed...")
- }
+ // Update Redis Hash for restored message
+ try {
+     if (snapshotItem.isActive) {
+         redisTemplate.opsForHash<String, String>()
+             .put("i18n:data:${snapshotItem.locale}", snapshotItem.code, snapshotItem.message)
+     } else {
+         redisTemplate.opsForHash<String, String>()
+             .delete("i18n:data:${snapshotItem.locale}", snapshotItem.code)
+     }
+ } catch (ex: Exception) {
+     log.warn("Redis hash update failed...")
+ }
```

After loop → inject `I18nRedisSyncService.publishFullInvalidation()`.

### 2.6 I18nConfig (auth-service) — MODIFY

```diff
- @Bean
- @Primary
- fun messageSource(i18nMessageRepository: I18nMessageRepository): MessageSource {
-     val fileMessageSource = ReloadableResourceBundleMessageSource()
-     ...
-     val dbMessageSource = DatabaseMessageSource(i18nMessageRepository)
-     dbMessageSource.setParentMessageSource(fileMessageSource)
-     return dbMessageSource
- }
+ // messageSource bean removed — base-core I18nCacheAutoConfiguration provides RedisMessageSource
+ // File bundle fallback provided by I18nAutoConfiguration.messageSource()
```

Keep: `localeResolver()` bean (explicit locale config).

### 2.7 AuthControllerAdvice (auth-service) — MODIFY

```diff
  class AuthControllerAdvice(
      validator: LocalValidatorFactoryBean,
      private val messageSource: MessageSource
- ) : BaseControllerAdvice(validator) {
+ ) : BaseControllerAdvice(validator, messageSource) {
```

## 3. Redis Data Schema

```
Key Pattern              Type    TTL      Purpose
──────────────────────────────────────────────────────────
i18n:data:{locale}       Hash    10 days  All messages for locale
  field: {code}                           Message key
  value: {message}                        Translated text
i18n:version             String  none     Monotonic version counter
──────────────────────────────────────────────────────────

Channel: i18n:invalidation
Payload: {"type":"SINGLE|FULL","code":"...","locale":"..."}
```

## 4. Message Resolution Flow

```
API Request (Accept-Language: vi)
  └→ BaseControllerAdvice.resolveMessage("auth.error.xxx", args, fallback)
       └→ RedisMessageSource.resolveCode("auth.error.xxx", Locale("vi"))
            ├→ L1: Caffeine.getIfPresent("auth.error.xxx:vi")
            │   └→ HIT → return MessageFormat
            │   └→ MISS ↓
            ├→ L2: redisTemplate.opsForHash().get("i18n:data:vi", "auth.error.xxx")
            │   └→ HIT → backfill L1 → return MessageFormat
            │   └→ MISS ↓
            └→ return null → AbstractMessageSource → parent (file bundle)
                 └→ HIT → return MessageFormat
                 └→ MISS → NoSuchMessageException → fallback text
```

## 5. Cache Invalidation Flow

```
Admin updates message "auth.error.xxx" (locale=vi, message="Lỗi xác thực mới")
  └→ system-admin-service controller → save DB
       └→ I18nRedisSyncService.syncSingle("auth.error.xxx", "vi", "Lỗi xác thực mới")
            ├→ HSET i18n:data:vi auth.error.xxx "Lỗi xác thực mới"
            ├→ INCR i18n:version
            └→ PUBLISH i18n:invalidation {"type":"SINGLE","code":"auth.error.xxx","locale":"vi"}
                 └→ All consumer instances receive:
                      └→ I18nCacheInvalidationListener.onMessage()
                           └→ RedisMessageSource.invalidateKey("auth.error.xxx", "vi")
                                └→ Caffeine L1 evict "auth.error.xxx:vi"

Next request → L1 MISS → L2 HIT (fresh data) → backfill L1
```

## 6. Flyway Migration (Seed Data)

`V10__seed_auth_i18n_messages.sql` in system-admin-service:
- Copy 28 records from auth-service V6 + V12 migrations
- Module = 'auth' for all records
- Source: V6 (20 records: 10 en + 10 vi) + V12 (8 records: 4 en + 4 vi)

## 7. Dependency Changes

| Service | Dependency | Action |
|---------|-----------|--------|
| base-core | `com.github.ben-manes.caffeine:caffeine` | ADD (optional) |
| base-core | `org.springframework.boot:spring-boot-starter-data-redis` | ADD (optional) |
| auth-service | `com.github.ben-manes.caffeine:caffeine` | KEEP (other caches may use) |
| auth-service | auth i18n JPA entities | REMOVE imports |
