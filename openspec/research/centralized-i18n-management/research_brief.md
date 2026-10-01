# Research Brief — Centralized i18n Messages Management

## 1. Feature Overview

| Field | Value |
|-------|-------|
| **Feature Name** | Centralized i18n Messages Management |
| **Input Mode** | Idea (Free Description) |
| **Source** | User requirement — consolidate i18n_messages to single DB, share via Redis |
| **Created At** | 2026-10-01 |

## 2. Keywords & Search Queries

1. distributed i18n cache invalidation microservices
2. Redis pub/sub two-level cache Spring Boot
3. ETag version-based cache invalidation
4. centralized i18n single source of truth microservices
5. Caffeine Redis multi-level cache pattern
6. Spring MessageSource distributed architecture
7. cache stampede prevention i18n
8. Redis keyspace notification cache version

## 3. Problem Statement

Bảng `i18n_messages` đang tồn tại song song ở **2 databases riêng biệt**:
- `auth-service` database → `I18nMessageEntity` + `DatabaseMessageSource` (Caffeine cache 5min TTL)
- `system-admin-service` database → `I18nMessageEntity` + `I18nMessageVersionedDomain` (versioning/snapshot/rollback)

**Vấn đề cốt lõi:**
- Vi phạm **Single Source of Truth** — cùng 1 bảng, 2 nơi quản lý → data drift
- Vi phạm **Data Ownership** — i18n messages không thuộc domain auth
- Không có cơ chế **cross-service cache invalidation** — khi system-admin update message, auth-service không biết
- `DatabaseMessageSource` chỉ dùng Caffeine local cache (5min TTL) → **không có Redis L2 cache**
- Không có **ETag/version-based cache** → TTL-based = stale data window 0-5 phút
- Không có **Redis Pub/Sub invalidation** cho i18n (đã có cho security rules)

## 4. Current System Analysis

### 4.1 Related Features (Đã Implement)

| Component | Location | Status | Mô tả |
|-----------|----------|--------|--------|
| `I18nMessageEntity` | `auth-service/shared/i18n/` | ✅ DONE | JPA entity cho `i18n_messages` table |
| `I18nMessageRepository` | `auth-service/shared/i18n/` | ✅ DONE | JPA repository: `findByCodeAndLocaleAndIsActiveTrue()` |
| `DatabaseMessageSource` | `auth-service/shared/i18n/` | ✅ DONE | Custom `AbstractMessageSource` + Caffeine cache (5min, maxSize=500) |
| `I18nConfig` | `auth-service/shared/config/` | ✅ DONE | Composite chain: DB → file bundles |
| `I18nMessageEntity` | `system-admin-service/versioning/domain/entity/` | ✅ DONE | JPA entity (duplicate) |
| `I18nMessageRepository` | `system-admin-service/versioning/domain/entity/` | ✅ DONE | JPA repository (embedded in entity file) |
| `I18nMessageVersionedDomain` | `system-admin-service/versioning/domain/impl/` | ✅ DONE | Versioned config domain + Redis key eviction on rollback |
| `SecurityRuleCacheConfig` | `auth-service/shared/security/` | ✅ DONE | **Pattern tham chiếu** — Redis Pub/Sub cho cache invalidation |

### 4.2 Existing Patterns

| Pattern | Implementation | Service |
|---------|---------------|---------|
| Redis Pub/Sub cache invalidation | `SecurityRuleCacheConfig` — channel `auth-service:security-rules:changed` | auth-service |
| Spring Modulith Event Outbox | `ConfigDomainChangedEvent` → `EVENT_PUBLICATION` table | system-admin-service |
| Versioned Config Domain SPI | `VersionedConfigDomain<E>` interface + `ConfigSnapshotManager` | system-admin-service |
| Caffeine L1 cache | `DatabaseMessageSource` — 5min TTL, maxSize=500 | auth-service |
| Redis L2 cache | Chỉ dùng cho key-level eviction: `i18n:{locale}:{code}` | system-admin-service |
| `BaseControllerAdvice.resolveMessage()` | I18n message resolution cho error handling | base-core |
| File-based MessageSource | `messages/*.properties` with `ReloadableResourceBundleMessageSource` | auth-service |

### 4.3 Tech Stack Constraints

| Tech | Version | Dùng cho |
|------|---------|---------|
| Spring Boot | 3.x+ | Framework chính |
| Kotlin | 2.x | Language chính |
| PostgreSQL | 15+ | Database |
| Redis | 7.x | Distributed cache |
| Caffeine | 3.x | Local cache |
| Spring Modulith | Latest | Event-driven outbox |
| base-core | Internal | Shared library |

### 4.4 Integration Points

| Module | Service | Interaction |
|--------|---------|-------------|
| `BaseControllerAdvice.resolveMessage()` | base-core | Resolve i18n messages cho API error responses |
| `AuthControllerAdvice` | auth-service | Uses `MessageSource` → `DatabaseMessageSource` → DB |
| `AccountControllerAdvice` | account-service | Uses `BaseControllerAdvice.resolveMessage()` |
| `SysAdminControllerAdvice` | system-admin-service | Uses `BaseControllerAdvice.resolveMessage()` |
| `notification-service` | notification-service | **Chưa dùng** i18n messages trực tiếp |
| Admin Dashboard | frontend | CRUD i18n messages via API |
