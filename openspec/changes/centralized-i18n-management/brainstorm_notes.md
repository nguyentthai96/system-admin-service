---
type: brainstorm_notes
change: centralized-i18n-management
date: 2026-10-01
selected_direction: "Redis as Shared Read Cache + 2-Level Cache + Hybrid Invalidation"
pre_flow: "Command (system-admin writes) + Query (consumers read)"
pre_feature_type: "MAINTENANCE"
status: complete
updated: 2026-10-01T10:24:00+07:00
---

# Brainstorm Notes: Centralized i18n Messages Management

## Date
2026-10-01

## Context

Bảng `i18n_messages` duplicate ở 2 databases (auth-service + system-admin-service). Không có cơ chế cross-service cache invalidation, ETag/version cache, hay Redis L2 cache cho i18n. Research `/wf_feature_research` đã phân tích 4 architecture alternatives và chọn **Option D: Redis as Shared Read Cache**.

Brainstorm này deep-dive vào implementation details, edge cases, và design decisions chưa được cover trong research phase.

## Questions Asked & Answers

### Q1: RedisMessageSource nên auto-configure như thế nào trong base-core?
→ **A: `@ConditionalOnBean(RedisConnectionFactory) + @ConditionalOnMissingBean(MessageSource)`** — tự động kích hoạt khi Redis available, nhưng service vẫn có thể override. Consistent với pattern `I18nAutoConfiguration` hiện tại.

### Q2: Seed data i18n messages từ các service khác nhau sẽ nạp vào system-admin-service DB bằng cách nào?
→ **A: Flyway migration trong system-admin-service** — tổ chức file theo module (V10__seed_auth_i18n.sql, V11__seed_account_i18n.sql). Deterministic, version-controlled, rollback-safe.

### Q3: Chọn chiến lược invalidation cho Caffeine L1 cache?
→ **A: Hybrid A+B** — single update = selective evict (`caffeine.invalidate(key)`), batch update/rollback = invalidate ALL (`caffeine.invalidateAll()`). Best of both worlds.

### Q4: Xử lý bảng i18n_messages ở auth-service database?
→ **A: Chưa DROP ngay** — Phase 3 chỉ thay đổi config để dùng Redis, giữ table như fallback. Phase 4+ mới DROP khi đã stable.

### Q5: Nếu Redis down khi system-admin-service startup?
→ **A: Try-catch + warn log + scheduled retry** — service vẫn khởi động, retry sync sau vài phút. Graceful degradation.

### Q6: Scope của centralized i18n trong lần triển khai này?
→ **A: Chỉ error messages** — scope hiện tại. Architecture sẵn sàng mở rộng cho notification templates, UI labels, etc.

### Q7: AuthControllerAdvice có nên truyền messageSource cho parent BaseControllerAdvice constructor?
→ **A: Có — đồng ý.** Cần sửa `AuthControllerAdvice` constructor call: `BaseControllerAdvice(validator)` → `BaseControllerAdvice(validator, messageSource)`. Điều này fix bug hiện tại (parent's `resolveMessage()` luôn return fallback).

### Q8: Redis Hash `i18n:data:{locale}` nên có TTL hay persist forever?
→ **A: TTL = 10 ngày.** Self-healing — nếu sync bị miss, data tự expire sau 10 ngày. Startup fullSync() sẽ re-populate. Đủ dài để không bị expire giữa chừng trong normal operation.

### Q9: Cần Micrometer metrics cho cache hit ratio không?
→ **A: Không cần.** Scope giữ gọn — monitoring có thể thêm sau nếu cần.

## Approaches Considered

### Approach 1: Option D — Redis as Shared Read Cache ✅ (CHOSEN)
- **Architecture:** system-admin-service owns DB → sync to Redis L2 → Pub/Sub invalidation → consumers read Redis only
- **Pros:** Fast (~0.5ms L1 miss), decoupled, simple, existing infra, file bundle fallback
- **Cons:** Eventual consistency (< 1 sec), Redis dependency
- **Why chosen:** Best trade-off performance vs complexity vs independence

### Approach 2: API Gateway (consumer → HTTP → sysadmin)
- **Pros:** True single source, real-time consistency
- **Cons:** Runtime coupling, latency 10-50ms, single point of failure, thundering herd
- **Why rejected:** Violates microservice independence, performance unacceptable

### Approach 3: Event-Driven Full Replication (Kafka → mỗi service clone)
- **Pros:** Full independence, no Redis dependency
- **Cons:** Over-engineering, storage waste, Kafka infra, eventual consistency anyway
- **Why rejected:** Disproportionate complexity for i18n (20-50 entries)

## Selected Direction

**Redis as Shared Read Cache** with:
1. `RedisMessageSource` in base-core (auto-configured when Redis available)
2. `I18nRedisSyncService` in system-admin-service (DB → Redis sync + Pub/Sub)
3. **Hybrid invalidation** (selective per-key + full invalidate for batch)
4. **Version counter** (`i18n:version`) for ETag-like cache validation
5. **Graceful degradation** (file bundle fallback khi Redis down)
6. **Gradual migration** (auth-service giữ table, chỉ switch config)

## Pre-classifications (preliminary)
- Feature type: MAINTENANCE (refactoring existing feature, no new business capability)
- Flow type: Command (system-admin writes) + Query (consumers read)
- Affected modules:
  - `base-core` — new `RedisMessageSource`, `I18nCacheAutoConfiguration`
  - `system-admin-service` — new `I18nRedisSyncService`, modify `I18nMessageVersionedDomain`
  - `auth-service` — remove DB entities, modify `I18nConfig`
  - `account-service` — auto-configured (no changes needed)

## Codebase Investigation Findings

### Discovery 1: BaseControllerAdvice messageSource injection gap

```kotlin
// BaseControllerAdvice constructor:
abstract class BaseControllerAdvice(
    private val validator: LocalValidatorFactoryBean,
    private val messageSource: MessageSource? = null  // ← default null
)

// AuthControllerAdvice constructor:
class AuthControllerAdvice(
    validator: LocalValidatorFactoryBean,
    private val messageSource: MessageSource  // ← child's own field
) : BaseControllerAdvice(validator)          // ← parent.messageSource = NULL!
```

**Impact:** `resolveMessage()` trong parent dùng parent's `messageSource` (null) → luôn return fallback. Auth-service's `messageSource` field riêng không được parent dùng.

**Implication:** Chuyển sang `RedisMessageSource` sẽ **tự động fix** vấn đề này nếu base-core auto-config inject đúng vào parent constructor. Tuy nhiên, cần verify auth-service's `AuthControllerAdvice` cũng pass `messageSource` cho parent.

### Discovery 2: Redis key pattern conflict

```
system-admin-service hiện tại (rollback):
  redisTemplate.delete("i18n:${locale}:${code}")  ← format: i18n:{locale}:{code}

Research đề xuất (L2 Hash):
  HSET i18n:data:{locale} {code} {message}        ← format: hash per locale
```

**Must fix:** `I18nMessageVersionedDomain.applyRollbackState()` cần update từ `delete("i18n:{locale}:{code}")` sang hash-based operations.

### Discovery 3: Existing Pub/Sub pattern reference

```
SecurityRuleCacheConfig pattern (auth-service):
  Channel: "auth-service:security-rules:changed"
  Listener: RedisMessageListenerContainer + MessageListenerAdapter
  Action: dynamicAuthorizationManager.evictCache()
```

**Reuse:** Exact same pattern cho i18n. Đã proven in production.

### Discovery 4: Data state across services

```
auth-service DB:  28 i18n records (V6: 20 + V12: 8), all module="auth"
system-admin-service DB:  0 i18n records (table exists but no seed data)
```

**Migration path:** Seed V10 in system-admin-service = copy V6 + V12 data.

## Open Questions for Design Phase

- [RESOLVED] RedisMessageSource placement → base-core auto-config
- [RESOLVED] Seed data strategy → Flyway migration per module
- [RESOLVED] L1 invalidation strategy → Hybrid A+B
- [RESOLVED] auth-service table handling → Keep as fallback, DROP later
- [RESOLVED] Redis down on startup → Try-catch + retry
- [RESOLVED] Feature scope → Error messages only
- [RESOLVED] BaseControllerAdvice constructor fix — `AuthControllerAdvice` PHẢI pass `messageSource` cho parent constructor. User confirmed.
- [RESOLVED] Monitoring/metrics — KHÔNG cần Micrometer metrics. Scope giữ gọn.
- [RESOLVED] Redis Hash TTL — TTL = 10 ngày. Self-healing, startup fullSync() re-populate.

## Open Questions for URD Analysis
- N/A — requirements clear from research + brainstorm
