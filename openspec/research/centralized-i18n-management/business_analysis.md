# Business Analysis — Centralized i18n Messages Management

## 1. Stakeholders

| Actor | Vai trò | Mối quan tâm |
|-------|---------|--------------|
| System Admin | Primary | CRUD i18n messages qua Admin Dashboard, rollback, export/import |
| Auth Service | Consumer | Resolve error messages cho API responses |
| Account Service | Consumer | Resolve error messages cho API responses |
| Notification Service | Consumer | Template messages cho notifications (future) |
| All Future Services | Consumer | Tự động có i18n support khi dùng base-core |
| DevOps | Secondary | Deployment, monitoring, Redis cluster management |

## 2. Use Cases

### UC-001: Resolve I18n Message at Runtime

| Field | Value |
|-------|-------|
| **Mô tả ngữ nghĩa** | Bất kỳ service nào cần i18n message đều resolve qua 2-level cache (L1 Caffeine → L2 Redis → DB fallback). Không cần biết DB ở đâu, chỉ cần gọi `MessageSource.getMessage()`. |
| **Trigger** | API request cần resolve error message hoặc notification text |
| **Pre-conditions** | Redis available; i18n data seeded |
| **Post-conditions** | Message resolved; L1/L2 cache populated |
| **Basic Flow** | 1. Check Caffeine L1 cache → HIT → return<br>2. Check Redis L2 cache (hash `i18n:v{version}:{locale}`) → HIT → populate L1, return<br>3. Miss both → **Consumer service KHÔNG query DB trực tiếp** → return fallback (file bundle hoặc default) |
| **Exception Flow** | Redis down → graceful fallback to file bundles → return default description |

### UC-002: Admin Updates I18n Message

| Field | Value |
|-------|-------|
| **Mô tả ngữ nghĩa** | Admin update message qua system-admin-service API. Change phải propagate lập tức tới TẤT CẢ service instances qua Redis Pub/Sub. |
| **Trigger** | Admin gọi CRUD API trên system-admin-service |
| **Pre-conditions** | Admin authenticated; message exists |
| **Post-conditions** | DB updated; Redis L2 updated; All L1 caches invalidated via Pub/Sub; Version incremented |
| **Basic Flow** | 1. Admin update message via API<br>2. system-admin-service saves to DB<br>3. system-admin-service updates Redis L2 hash<br>4. system-admin-service increments cache version in Redis<br>5. system-admin-service publishes invalidation event to Redis Pub/Sub channel<br>6. All service instances receive Pub/Sub → evict L1 Caffeine cache<br>7. Next request → L1 miss → L2 HIT (fresh data) |

### UC-003: Service Startup — Warm Cache

| Field | Value |
|-------|-------|
| **Mô tả ngữ nghĩa** | Khi service khởi động, preload tất cả active i18n messages từ Redis L2 vào L1 Caffeine. Nếu Redis trống, service vẫn khởi động ok với file-based fallback. |
| **Trigger** | Service startup (ApplicationReadyEvent) |
| **Basic Flow** | 1. Service starts → check Redis `i18n:version` key<br>2. Load all messages from Redis hash `i18n:messages:{locale}` → populate L1<br>3. Store local version = Redis version |
| **Exception Flow** | Redis empty/down → use file-based bundles only → service operational |

### UC-004: Version-Based Cache Validation (ETag Pattern)

| Field | Value |
|-------|-------|
| **Mô tả ngữ nghĩa** | Consumer service maintain local `knownVersion`. Khi nhận Pub/Sub notification hoặc periodic check, so sánh `knownVersion` vs Redis `i18n:version`. Nế mismatch → full refresh L1 cache. Tối ưu hơn evict từng key. |
| **Trigger** | Pub/Sub notification received hoặc periodic version check |
| **Basic Flow** | 1. Receive notification `i18n:version` changed<br>2. Compare local version vs Redis version<br>3. If different → invalidate entire L1 Caffeine cache<br>4. Update local `knownVersion` |

## 3. Business Rules

| ID | Rule | Rationale |
|----|------|-----------|
| BR-001 | i18n_messages table chỉ tồn tại trong `system-admin-service` database | Single Source of Truth — chỉ 1 service sở hữu data |
| BR-002 | Consumer services KHÔNG được query `i18n_messages` table trực tiếp | Data Ownership — tránh coupling database |
| BR-003 | Message resolution chain: L1 Caffeine → L2 Redis → File bundles → Default | Performance layering + graceful degradation |
| BR-004 | Cache invalidation phải propagate trong < 1 giây (near real-time) | UX — admin thay đổi message, user thấy ngay |
| BR-005 | Service phải operational khi Redis down (file bundles fallback) | Resilience — cache is enhancement, not dependency |
| BR-006 | Version counter (ETag) tăng mỗi khi có bất kỳ change nào | Bulk invalidation — tránh evict từng key |
| BR-007 | All active messages for a locale được cache dưới dạng HashMap | Batch loading — giảm Redis round-trips |

## 4. Traceability Matrix

| Requirement | Use Case | Priority |
|-------------|----------|----------|
| Centralize DB ownership | UC-001, UC-002 | Critical |
| 2-level cache (L1 + L2) | UC-001, UC-003 | Critical |
| Real-time Pub/Sub invalidation | UC-002, UC-004 | Critical |
| Version-based cache (ETag) | UC-004 | High |
| Service startup warm cache | UC-003 | High |
| File bundle fallback | UC-001 | High |
| Graceful Redis degradation | UC-001, UC-003 | Medium |
