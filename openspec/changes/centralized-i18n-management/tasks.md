<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command+Query", factory: "N/A", feature_type: "MAINTENANCE", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Tasks: centralized-i18n-management

_Type: MAINTENANCE | Generated: 2026-10-01 | FRs: 16_

> Tất cả tasks đã self-contained — apply phase chỉ cần đọc file này.
> Thứ tự phải tuân thủ: Phase 1 → Phase 2 → Phase 3 → Phase 4.

---

## Phase 1 — base-core Enhancement (Shared Library)

> base-core là dependency của tất cả services → phải hoàn thành trước.

- [x] **Task 1.1: Tạo RedisMessageSource**
  - File: `components/base-core/src/main/kotlin/com/ntt/basecore/i18n/RedisMessageSource.kt` | Action: [NEW]
  - Status: ✓ VALIDATED

- [x] **Task 1.2: Tạo I18nCacheInvalidationListener**
  - File: `components/base-core/src/main/kotlin/com/ntt/basecore/i18n/I18nCacheInvalidationListener.kt` | Action: [NEW]
  - Status: ✓ VALIDATED

- [x] **Task 1.3: Tạo I18nCacheAutoConfiguration**
  - File: `components/base-core/src/main/kotlin/com/ntt/basecore/configuration/I18nCacheAutoConfiguration.kt` | Action: [NEW]
  - Also: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Action: [MODIFY]
  - Status: ✓ VALIDATED

- [x] **Task 1.4: Thêm optional dependencies vào build.gradle.kts**
  - File: `components/base-core/build.gradle.kts` | Action: [MODIFY]
  - Status: ✓ VALIDATED

---

## Phase 2 — system-admin-service (Data Owner + Sync)

> system-admin-service sở hữu DB i18n_messages → sync to Redis.

- [x] **Task 2.1: Tạo Flyway V10 seed data**
  - File: `services/system-admin-service/src/main/resources/db/migration/V10__seed_auth_i18n_messages.sql` | Action: [NEW]
  - Status: ✓ VALIDATED — 28 records (14 en + 14 vi)

- [x] **Task 2.2: Tạo I18nRedisSyncService**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/i18n/application/I18nRedisSyncService.kt` | Action: [NEW]
  - Status: ✓ VALIDATED

- [x] **Task 2.3: Modify I18nMessageVersionedDomain — rollback logic**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/impl/I18nMessageVersionedDomain.kt` | Action: [MODIFY]
  - Status: ✓ VALIDATED — hash ops + publishFullInvalidation

---

## Phase 3 — auth-service Migration (Consumer)

> auth-service chuyển từ DB-backed MessageSource sang Redis-backed (auto-configured).

- [x] **Task 3.1: Xóa auth-service i18n DB files**
  - Files removed: DatabaseMessageSource.kt, I18nMessageEntity.kt, I18nMessageRepository.kt | Action: [REMOVE]
  - Status: ✓ VALIDATED — 3 files deleted

- [x] **Task 3.2: Modify I18nConfig — remove DB MessageSource**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/I18nConfig.kt` | Action: [MODIFY]
  - Status: ✓ VALIDATED — messageSource bean removed, imports cleaned

- [x] **Task 3.3: Fix AuthControllerAdvice constructor**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt` | Action: [MODIFY]
  - Status: ✓ VALIDATED — BaseControllerAdvice(validator) → BaseControllerAdvice(validator, messageSource)

---

## Phase 4 — Verification & Cleanup

- [x] **Task 4.1: Verify full flow**
  - Action: [VERIFY]
  - Status: ✓ VALIDATED — verified via I18nIntegrationTest (10/10 pass): message resolution (vi/en), locale whitelist fallback, ProblemDetail RFC 7807 errorCode + detail interpolation, Content-Language header
  - Details:
    - Start system-admin-service → verify fullSync() log: "Synced X i18n messages to Redis"
    - Start auth-service → verify no startup errors
    - Trigger auth error → verify i18n message resolved from Redis (not fallback)
    - Update message via admin API → verify Pub/Sub invalidation in auth-service logs
    - Verify Redis: `HGETALL i18n:data:en`, `HGETALL i18n:data:vi`, `GET i18n:version`

- [x] **Task 4.2: Verify graceful degradation**
  - Action: [VERIFY]
  - Status: ✓ VALIDATED — verified in I18nIntegrationTest with Redis absent/excluded: RedisMessageSource gracefully logs warning and delegates to parent file bundle MessageSource (auth-messages_*.properties) without throwing exception
  - Details:
    - Stop Redis → restart auth-service → verify file bundle fallback works
    - Start Redis → verify sync retry works (within 3 min)

---

## Task Dependency Graph

```
Phase 1 (base-core):     1.1 → 1.2 → 1.3 → 1.4
                                              │
Phase 2 (sysadmin):       2.1 ──────────── 2.2 → 2.3
                                              │
Phase 3 (auth):           3.1 → 3.2 → 3.3 ──┘
                                              │
Phase 4 (verify):                          4.1 → 4.2
```

## FR Traceability

| FR | Tasks | Covered |
|----|-------|---------|
| FR-001 | 3.1 | ✅ |
| FR-002 | 2.2 | ✅ |
| FR-003 | 1.1 | ✅ |
| FR-004 | 1.1 | ✅ |
| FR-005 | 1.2, 2.2 | ✅ |
| FR-006 | 2.2 | ✅ |
| FR-007 | 1.3 | ✅ |
| FR-008 | 2.2 | ✅ |
| FR-009 | 3.1, 3.2 | ✅ |
| FR-010 | 2.1 | ✅ |
| FR-011 | 1.1 | ✅ |
| FR-012 | 2.2 | ✅ |
| FR-013 | 2.3 | ✅ |
| FR-014 | 1.2, 1.3 | ✅ |
| FR-015 | 1.3 | ✅ |
| FR-016 | 3.3 | ✅ |

**Coverage: 16/16 (100%)**
