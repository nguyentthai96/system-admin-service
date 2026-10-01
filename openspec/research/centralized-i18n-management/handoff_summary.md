# Handoff Summary — Centralized i18n Messages Management

## Kết quả nghiên cứu

### Vấn đề hiện tại

| # | Vấn đề | Severity |
|---|--------|----------|
| 1 | `i18n_messages` table duplicate ở 2 databases (auth-service + system-admin-service) | 🔴 Critical |
| 2 | Không có cross-service cache invalidation — admin update ở sysadmin, auth-service không biết | 🔴 Critical |
| 3 | `DatabaseMessageSource` chỉ dùng Caffeine L1 (5min TTL) — stale window 0-5 phút | 🟡 High |
| 4 | Không có Redis L2 cache cho i18n — mỗi L1 miss = DB query | 🟡 High |
| 5 | Không có ETag/version-based cache — chỉ TTL-based | 🟡 High |
| 6 | Consumer services (account-service, notification-service) chưa có i18n DB support | 🟢 Medium |

### Hiện trạng code đã implement

| Component | Có sẵn? | Service | Ghi chú |
|-----------|---------|---------|---------|
| `I18nMessageEntity` | ✅ Duplicate | auth + sysadmin | Cần consolidate → chỉ giữ sysadmin |
| `DatabaseMessageSource` (Caffeine only) | ✅ | auth-service | Cần thay bằng `RedisMessageSource` |
| `I18nMessageVersionedDomain` | ✅ | system-admin-service | Versioning/snapshot/rollback đã implement |
| Redis Pub/Sub pattern | ✅ | auth-service | `SecurityRuleCacheConfig` — pattern tham chiếu |
| `ConfigDomainChangedEvent` (Modulith outbox) | ✅ | system-admin-service | Audit đã implement |
| ETag/version cache | ❌ Chưa có | — | **Cần implement** |
| Redis L2 cache cho i18n | ❌ Chưa có | — | **Cần implement** |
| Redis Pub/Sub invalidation cho i18n | ❌ Chưa có | — | **Cần implement** |

## Phương án kiến trúc được chọn

> **Option D: Redis as Shared Read Cache** ✅

```
┌──────────────────────┐         ┌──────────────────┐
│ system-admin-service │ ──DB──→ │ PostgreSQL       │
│ (Data Owner)         │ ──L2──→ │ i18n_messages    │
│ • CRUD i18n          │         └──────────────────┘
│ • DB → Redis sync    │
│ • Pub/Sub publish    │         ┌──────────────────┐
│ • Version increment  │ ──L2──→ │ Redis Cluster    │
└──────────────────────┘ ──PS──→ │ • Hash per locale│
                                 │ • Version counter│
┌──────────────────────┐         │ • Pub/Sub channel│
│ auth-service         │ ←─L2── │                  │
│ (Consumer)           │ ←─PS── └──────────────────┘
│ • RedisMessageSource │
│ • L1 Caffeine        │         ┌──────────────────┐
│ • Pub/Sub listener   │         │ base-core        │
└──────────────────────┘         │ (Shared Library) │
                                 │ • RedisMessage   │
┌──────────────────────┐         │   Source         │
│ account-service      │ ←─L2── │ • L1 Caffeine    │
│ (Consumer)           │ ←─PS── │ • Pub/Sub        │
│ • Auto-configured    │         │   Listener       │
└──────────────────────┘         │ • Auto-config    │
                                 └──────────────────┘
```

## Các alternatives đã evaluate và reject

| Option | Lý do reject |
|--------|-------------|
| **A. API Gateway** (consumer gọi API sysadmin) | Runtime coupling, latency 10-50ms, single point of failure |
| **B. Shared Database** (consumer share DB connection) | Anti-pattern, connection pool contention, schema coupling |
| **C. Event-Driven Full Replication** (Kafka → mỗi service copy DB) | Over-engineering, storage waste, Kafka infrastructure |

## Performance trade-off analysis

| Metric | Hiện tại | Sau triển khai | Cải thiện |
|--------|---------|---------------|-----------|
| Hot path (L1 hit) | ~0.01ms | ~0.01ms | Same |
| L1 miss | ~5-20ms (DB) | ~0.5ms (Redis) | **10-40x faster** |
| Invalidation latency | 0-5 min (TTL) | < 1 sec (Pub/Sub) | **Real-time** |
| Cross-service consistency | ❌ None | ✅ < 1 sec | **New capability** |
| Startup warm cache | Cold start | Redis preload | **Faster** |

## Migration phases

| Phase | Scope | Effort | Risk |
|-------|-------|--------|------|
| **1. base-core Enhancement** | `RedisMessageSource` + Pub/Sub listener + Auto-config | 2-3 days | Low |
| **2. system-admin-service Sync** | `I18nRedisSyncService` + startup sync + API trigger | 1-2 days | Low |
| **3. auth-service Migration** | Remove DB entities, update config, add migration | 1-2 days | Medium |
| **4. Other services** | Auto-configured, just add file bundles | 0.5 day | Low |

## Tài liệu chi tiết

| Document | Path |
|----------|------|
| Research Brief | [`research_brief.md`](./research_brief.md) |
| Business Analysis | [`business_analysis.md`](./business_analysis.md) |
| Technical Specification | [`technical_spec.md`](./technical_spec.md) |

## Next Steps (Khuyến nghị)

1. **Review & approve** kiến trúc này trước khi triển khai
2. Chạy `/wf_brainstorm_openspec` hoặc `/wf_openspec` để sinh implementation tasks
3. Implement theo thứ tự Phase 1 → 2 → 3 → 4
4. Monitor Redis Pub/Sub latency sau triển khai
