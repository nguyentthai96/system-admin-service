# Pre-OpenSpec: centralized-i18n-management

> **Type**: MAINTENANCE
> **Flow**: Command (system-admin writes) + Query (consumers read)
> **Source**: User Idea (no URD) + Feature Research + Brainstorm
> **Classification Evidence**: `i18n` → `I18nMessageEntity` → `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageEntity.kt` + `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/entity/I18nMessageEntity.kt`
> **Archive**: N/A
> **Quality Score**: 92/100

## 📋 Feature Summary

Consolidate bảng `i18n_messages` từ 2 databases (auth-service + system-admin-service) về duy nhất `system-admin-service` database. Xây dựng cơ chế 2-level cache (Caffeine L1 + Redis L2) trong base-core, sử dụng Redis Pub/Sub để invalidate cache real-time khi admin thay đổi messages. Consumer services chỉ đọc từ Redis — không query DB trực tiếp, không gọi API system-admin-service.

| Metric | Giá trị |
|--------|---------|
| Số FR | 16 (Idea: 10, Enriched: 6) |
| Issues | 2 (🔴: 0, 🟡: 2) |
| Open Questions | 0 |
| **Quality Score** | **92/100** |

---

## 1. Actors

- **System Admin**: Quản lý i18n messages (CRUD) qua Admin Dashboard → system-admin-service API
- **auth-service**: Consumer — resolve error messages cho API responses (qua `BaseControllerAdvice.resolveMessage()`)
- **account-service**: Consumer — resolve error messages cho API responses
- **notification-service**: Consumer tiềm năng (chưa sử dụng i18n trực tiếp)
- **system-admin-service**: Data Owner + Consumer — sở hữu bảng `i18n_messages`, đồng bộ Redis, cũng resolve messages cho chính mình
- **base-core**: Shared library — cung cấp `RedisMessageSource` + Pub/Sub listener auto-configured

## 2. Functional Requirements

### FR-001: Consolidate bảng i18n_messages [IDEA]
- **Actor**: DevOps / Agent
- **Action**: Hệ thống phải chỉ giữ duy nhất 1 bảng `i18n_messages` ở system-admin-service database
- **Validation**: auth-service không còn JPA entity/repository cho i18n; system-admin-service DB chứa toàn bộ seed data (auth + common modules)

### FR-002: Redis L2 Cache — Hash per locale [IDEA]
- **Actor**: system-admin-service
- **Action**: Hệ thống phải lưu toàn bộ active i18n messages vào Redis Hash theo locale (`i18n:data:{locale}`) với TTL = 10 ngày
- **Validation**: `HGET i18n:data:en auth.error.invalid_credentials` → return message text; `TTL i18n:data:en` → ≤ 864000 seconds; startup fullSync() re-populate expired keys

### FR-003: Caffeine L1 Cache — Per-instance [IDEA]
- **Actor**: base-core (RedisMessageSource)
- **Action**: Hệ thống phải cache messages trong Caffeine L1 (expireAfterWrite=10min, maxSize=2000) trên mỗi instance
- **Validation**: Same key lookup lần 2 trở đi = L1 hit (< 0.01ms)

### FR-004: Resolution chain L1 → L2 → File bundle [IDEA]
- **Actor**: base-core (RedisMessageSource)
- **Action**: Hệ thống phải resolve messages theo chain: Caffeine L1 → Redis L2 → File bundle fallback
- **Validation**: L1 miss → L2 hit → backfill L1; L2 miss → file bundle result; All miss → return null (parent `AbstractMessageSource` handles default)

### FR-005: Redis Pub/Sub invalidation — Hybrid strategy [IDEA]
- **Actor**: system-admin-service (publisher), base-core (subscriber)
- **Action**: Khi admin update message, hệ thống phải publish invalidation event tới Redis Pub/Sub channel `i18n:invalidation`
- **Validation**: Single update → selective evict message payload; Batch/rollback → full invalidation payload; Consumer L1 cache evicted < 1 giây

### FR-006: Version counter (ETag) [IDEA]
- **Actor**: system-admin-service
- **Action**: Hệ thống phải maintain monotonic version counter (`i18n:version`) trong Redis, increment mỗi khi có change
- **Validation**: `GET i18n:version` → number; mỗi update/delete → version tăng 1

### FR-007: Auto-configuration trong base-core [IDEA]
- **Actor**: base-core
- **Action**: Hệ thống phải auto-configure `RedisMessageSource` khi `RedisConnectionFactory` bean tồn tại và chưa có `MessageSource` bean khác
- **Validation**: `@ConditionalOnBean(RedisConnectionFactory)` + `@ConditionalOnMissingBean(MessageSource)` → RedisMessageSource tự đăng ký; Service override vẫn hoạt động

### FR-008: Startup full sync DB → Redis [IDEA]
- **Actor**: system-admin-service
- **Action**: Khi system-admin-service khởi động, hệ thống phải sync toàn bộ active messages từ DB sang Redis Hashes
- **Validation**: After startup → `HGETALL i18n:data:en` chứa tất cả records từ DB; Version counter updated

### FR-009: Gradual migration — auth-service giữ table [IDEA]
- **Actor**: auth-service
- **Action**: Auth-service phải chuyển sang dùng `RedisMessageSource` (auto-configured), xóa code entities nhưng giữ table trong DB
- **Validation**: `DatabaseMessageSource`, `I18nMessageEntity`, `I18nMessageRepository` bị xóa; Table `i18n_messages` vẫn tồn tại trong auth-service DB; Error messages resolve OK qua Redis

### FR-010: Seed data migration via Flyway [IDEA]
- **Actor**: system-admin-service
- **Action**: Hệ thống phải tạo Flyway migrations trong system-admin-service để seed toàn bộ auth module messages (V6 + V12 content)
- **Validation**: `V10__seed_auth_i18n_messages.sql` chứa 28 records (10 en + 10 vi từ V6, 4 en + 4 vi từ V12)

### FR-011: Graceful degradation khi Redis down [ENRICHED]
- **Actor**: base-core (RedisMessageSource)
- **Action**: Khi Redis không available, hệ thống phải fallback tới file bundle messages mà không throw exception
- **Validation**: Redis connection timeout → log WARN → return file bundle message; Service vẫn operational

### FR-012: Startup sync retry khi Redis down [ENRICHED]
- **Actor**: system-admin-service
- **Action**: Nếu fullSync() fail khi startup, hệ thống phải retry tự động sau 3 phút
- **Validation**: Redis down on boot → WARN log → service starts; Redis up after 3 min → sync succeeds → version updated

### FR-013: Modify rollback logic — hash-based operations [ENRICHED]
- **Actor**: system-admin-service
- **Action**: `I18nMessageVersionedDomain.applyRollbackState()` phải sử dụng Redis Hash operations thay vì individual key deletion
- **Validation**: Rollback → `HSET i18n:data:{locale} {code} {message}` thay vì `DELETE i18n:{locale}:{code}`; Pub/Sub full invalidation published

### FR-014: Pub/Sub listener auto-registration [ENRICHED]
- **Actor**: base-core
- **Action**: `RedisMessageListenerContainer` cho i18n invalidation phải tự đăng ký khi `RedisConnectionFactory` tồn tại
- **Validation**: Channel `i18n:invalidation` → listener active; Message received → L1 cache evicted

### FR-015: File bundle fallback configuration [ENRICHED]
- **Actor**: auth-service, base-core
- **Action**: `RedisMessageSource` phải set file bundle MessageSource làm parent (chain resolution)
- **Validation**: `RedisMessageSource.parentMessageSource` = `ReloadableResourceBundleMessageSource`; Redis miss + file bundle hit → return message

### FR-016: Fix AuthControllerAdvice constructor [ENRICHED]
- **Actor**: auth-service
- **Action**: `AuthControllerAdvice` phải truyền `messageSource` cho parent `BaseControllerAdvice` constructor để `resolveMessage()` hoạt động đúng
- **Validation**: `BaseControllerAdvice(validator, messageSource)` thay vì `BaseControllerAdvice(validator)`; `resolveMessage()` return message từ MessageSource thay vì luôn return fallback

## 3. Non-functional Requirements

- **Latency**: L1 cache hit < 0.01ms; L2 cache hit < 1ms; Invalidation propagation < 1 giây
- **Availability**: Service operational khi Redis down (file bundle fallback)
- **Memory**: L1 Caffeine maxSize=2000 (~400KB per instance); L2 Redis ~1MB per locale
- **Consistency**: Eventual consistency < 1 giây (Pub/Sub propagation)
- **Scalability**: Tự động scale — mỗi instance có L1 riêng, shared L2

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp.

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-011** [ENRICHED]: Graceful degradation — Redis down fallback. Justification: resilience pattern bắt buộc cho cache dependency
- **FR-012** [ENRICHED]: Startup sync retry. Justification: infrastructure might not be ready on cold start
- **FR-013** [ENRICHED]: Rollback logic update. Justification: hiện tại dùng individual key deletion → phải migrate sang hash operations
- **FR-014** [ENRICHED]: Auto-registration. Justification: zero-config cho consumer services
- **FR-015** [ENRICHED]: File bundle fallback chain. Justification: Spring MessageSource parent chain pattern
- **FR-016** [ENRICHED]: Fix AuthControllerAdvice constructor. Justification: bug hiện tại — parent's `resolveMessage()` luôn return fallback vì messageSource = null

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | L2 cache (Hash) + Pub/Sub invalidation + Version counter | Shared cluster, đã available |
| PostgreSQL | Source of truth cho i18n_messages | system-admin-service DB only |

## 6. Assumptions

- ⚠️ Redis cluster shared giữa tất cả services đã available và accessible
- ⚠️ base-core dependency version consistent across all services
- ⚠️ auth-service DB seed data (V6 + V12) chưa bị manual modify trong production
- ⚠️ Không có service nào ngoài auth-service + system-admin-service đang dùng i18n DB trực tiếp

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 24/25 | FR-007: auto-config condition có thể cần thêm `@ConditionalOnProperty` |
| Đầy đủ (Completeness) | 24/25 | FR-009: chưa specify thời điểm DROP table |
| Nhất quán (Consistency) | 23/25 | FR-013: rollback logic change cần đồng bộ với ConfigSnapshotManager |
| Kiểm thử được (Testability) | 21/25 | FR-005: Pub/Sub integration test khó setup |
| **Tổng** | **92/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích source) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -1 | FR-007 | "auto-configure khi RedisConnectionFactory bean tồn tại" — điều kiện đủ chưa? Cần thêm property flag? | Thêm `@ConditionalOnProperty` với default=true |
| 2 | Completeness | -2 | FR-009 | "giữ table trong DB" — timeline DROP không rõ | Specify Phase 4+ với condition: stable 2 weeks |
| 3 | Completeness | -1 | FR-009 | "giữ table trong DB" — timeline DROP không rõ | Specify Phase 4+ với condition: stable 2 weeks |
| 4 | Consistency | -2 | FR-013 | Rollback logic thay đổi cần verify `ConfigSnapshotManager` flow | Cross-validate with existing rollback tests |
| 5 | Testability | -4 | FR-005, FR-008 | Pub/Sub và startup sync test khó test without embedded Redis | Recommend Testcontainers Redis |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | `BaseControllerAdvice.messageSource` = null khi child không truyền constructor param — `resolveMessage()` luôn return fallback | FR-007, FR-004 | Verify auth-service `AuthControllerAdvice` constructor truyền messageSource cho parent |
| 2 | Risk | 🟡 | Redis Hash `i18n:data:{locale}` không có TTL → nếu system-admin-service không sync, data tồn tại vĩnh viễn | FR-002, FR-006 | Thêm startup validation: nếu version=0 → log ERROR, trigger sync |

> Không có critical issues.

## 9. Open Questions

Không còn câu hỏi mở — tất cả đã được resolved trong brainstorm session:

- [RESOLVED] `AuthControllerAdvice` PHẢI truyền `messageSource` cho parent `BaseControllerAdvice` constructor → thêm FR-016
- [RESOLVED] Redis Hash TTL = 10 ngày (self-healing) → cập nhật FR-002
- [RESOLVED] Không cần Micrometer metrics → loại bỏ khỏi scope

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
i18n / Internationalization / Message Resolution / Cache Management

### 10.2 Flow Type
Command (system-admin writes to DB + Redis) + Query (consumers resolve messages via cache chain)

### 10.3 Candidate Services
- **base-core**: Cần tạo `RedisMessageSource`, `I18nCacheInvalidationListener`, `I18nCacheAutoConfiguration` — keyword: MessageSource, AbstractMessageSource, I18nAutoConfiguration
- **system-admin-service**: Cần tạo `I18nRedisSyncService`, modify `I18nMessageVersionedDomain` — keyword: I18nMessageEntity, I18nMessageRepository, VersionedConfigDomain
- **auth-service**: Cần xóa DB entities, modify `I18nConfig` — keyword: DatabaseMessageSource, I18nMessageEntity, I18nConfig

### Detection Evidence
- Keyword: `i18n`, `MessageSource`, `DatabaseMessageSource` → Module: `shared/i18n/` → File: `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
- Keyword: `I18nMessageVersionedDomain`, `VersionedConfigDomain` → Module: `versioning/domain/impl/` → File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/impl/I18nMessageVersionedDomain.kt`
- Keyword: `I18nAutoConfiguration`, `MessageSource` → Module: `configuration/` → File: `components/base-core/src/main/kotlin/com/ntt/basecore/configuration/I18nAutoConfiguration.kt`

### 10.4 External Integrations
- Redis (cache + Pub/Sub) — shared cluster
- PostgreSQL (system-admin-service DB) — source of truth

### 10.5 Required Modules
- `base-core/configuration/` → new `I18nCacheAutoConfiguration`
- `base-core/i18n/` → new package: `RedisMessageSource`, `I18nCacheInvalidationListener`
- `system-admin-service/i18n/` or `versioning/` → new `I18nRedisSyncService`
- `auth-service/shared/i18n/` → REMOVE all files
- `auth-service/shared/config/` → MODIFY `I18nConfig`

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Admin | Update i18n message via Dashboard | Admin Dashboard → system-admin-service API |
| 2 | system-admin-service | Save to PostgreSQL DB | `I18nMessageRepository.save()` |
| 3 | system-admin-service | Update Redis L2 Hash | `HSET i18n:data:{locale} {code} {message}` |
| 4 | system-admin-service | Increment version | `INCR i18n:version` |
| 5 | system-admin-service | Publish invalidation | `PUBLISH i18n:invalidation {payload}` |
| 6 | All consumer instances | Receive Pub/Sub notification | `I18nCacheInvalidationListener.onMessage()` |
| 7 | All consumer instances | Evict L1 Caffeine cache | `caffeine.invalidate(key)` or `invalidateAll()` |
| 8 | Next API request | Resolve message | L1 miss → L2 hit (fresh) → backfill L1 |

## 12. Traceability Matrix

| FR-ID | Source | Spec Section | Affected Class | Status |
|-------|--------|-------------|---------------|--------|
| FR-001 | Idea | DB consolidation | [MODIFY] `I18nMessageVersionedDomain`, [REMOVE] auth `I18nMessageEntity` | Mapped |
| FR-002 | Idea | Redis L2 | [ADD] `I18nRedisSyncService` | Mapped |
| FR-003 | Idea | Caffeine L1 | [ADD] `RedisMessageSource` | Mapped |
| FR-004 | Idea | Resolution chain | [ADD] `RedisMessageSource.resolveCode()` | Mapped |
| FR-005 | Idea | Pub/Sub | [ADD] `I18nCacheInvalidationListener`, `I18nRedisSyncService.publishInvalidation()` | Mapped |
| FR-006 | Idea | Version/ETag | [ADD] `I18nRedisSyncService` version logic | Mapped |
| FR-007 | Idea | Auto-config | [ADD] `I18nCacheAutoConfiguration` | Mapped |
| FR-008 | Idea | Startup sync | [ADD] `I18nRedisSyncService.fullSync()` + `@EventListener` | Mapped |
| FR-009 | Idea | Migration | [REMOVE] auth `DatabaseMessageSource`, [MODIFY] `I18nConfig` | Mapped |
| FR-010 | Idea | Seed data | [ADD] `V10__seed_auth_i18n_messages.sql` | Mapped |
| FR-011 | Enriched | Resilience | [ADD] `RedisMessageSource` try-catch + fallback | Mapped |
| FR-012 | Enriched | Retry | [ADD] `I18nRedisSyncService` `@Scheduled` retry | Mapped |
| FR-013 | Enriched | Rollback fix | [MODIFY] `I18nMessageVersionedDomain.applyRollbackState()` | Mapped |
| FR-014 | Enriched | Listener auto-reg | [ADD] `I18nCacheAutoConfiguration.i18nCacheListenerContainer()` | Mapped |
| FR-015 | Enriched | Fallback chain | [ADD] `RedisMessageSource.setParentMessageSource()` | Mapped |
| FR-016 | Enriched | Constructor fix | [MODIFY] `AuthControllerAdvice` constructor call | Mapped |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- **Độ phức tạp**: Medium — chủ yếu là infrastructure refactoring, không có business logic mới
- **Rủi ro chính**: `BaseControllerAdvice` messageSource injection gap — cần verify cách child class truyền messageSource cho parent constructor
- **Điểm mạnh**: Đã có proven Pub/Sub pattern (`SecurityRuleCacheConfig`), versioning infrastructure (`VersionedConfigDomain`), và auto-config pattern (`I18nAutoConfiguration`) → reuse cao

### Related Features / Precedents
- **config-management-system** (`openspec/changes/config-management-system/`): `VersionedConfigDomain`, `ConfigSnapshotManager`, `ConfigDomainChangedEvent` — infrastructure đã implement cho i18n versioning
- **SecurityRuleCacheConfig**: Redis Pub/Sub cache invalidation pattern — exact clone cho i18n

### Integration Notes
- Redis: Dùng shared cluster (đã available). Key namespace: `i18n:data:{locale}`, `i18n:version`. Channel: `i18n:invalidation`
- PostgreSQL: Chỉ system-admin-service sở hữu. Consumer services KHÔNG query DB trực tiếp

### Suggested Approach
- **Phase 1**: base-core — `RedisMessageSource` + listener + auto-config
- **Phase 2**: system-admin-service — `I18nRedisSyncService` + modify `I18nMessageVersionedDomain` + seed data
- **Phase 3**: auth-service — remove DB code + modify config
- **Phase 4**: verify + cleanup
- **Reuse**: `SecurityRuleCacheConfig` pattern, `I18nAutoConfiguration` pattern, `VersionedConfigDomain` SPI

### Context from Confluence Images
N/A

### Change Impact Map (MAINTENANCE)

```
FR-001 → [REMOVE] I18nMessageEntity (auth-service/shared/i18n/I18nMessageEntity.kt)
          [REMOVE] I18nMessageRepository (auth-service/shared/i18n/I18nMessageRepository.kt)
          [REMOVE] DatabaseMessageSource (auth-service/shared/i18n/DatabaseMessageSource.kt)
FR-002 → [ADD] I18nRedisSyncService (system-admin-service — NEW)
FR-003 → [ADD] RedisMessageSource (base-core — NEW)
FR-004 → [ADD] RedisMessageSource.resolveCode() (base-core — NEW)
FR-005 → [ADD] I18nCacheInvalidationListener (base-core — NEW)
          [ADD] I18nRedisSyncService.publishInvalidation() (system-admin-service — NEW)
FR-006 → [ADD] I18nRedisSyncService version logic (system-admin-service — NEW)
FR-007 → [ADD] I18nCacheAutoConfiguration (base-core — NEW)
FR-008 → [ADD] I18nRedisSyncService.fullSync() (system-admin-service — NEW)
FR-009 → [MODIFY] I18nConfig (auth-service/shared/config/I18nConfig.kt)
FR-010 → [ADD] V10__seed_auth_i18n_messages.sql (system-admin-service — NEW)
FR-011 → [ADD] RedisMessageSource try-catch (base-core — NEW)
FR-012 → [ADD] I18nRedisSyncService @Scheduled retry (system-admin-service — NEW)
FR-013 → [MODIFY] I18nMessageVersionedDomain.applyRollbackState() (system-admin-service)
FR-014 → [ADD] I18nCacheAutoConfiguration.i18nCacheListenerContainer() (base-core — NEW)
FR-015 → [REUSE] Spring AbstractMessageSource.setParentMessageSource() (base-core)
```
