---
type: brainstorm_notes
change: config-management-system
date: 2026-09-30
selected_direction: "HIGH ABSTRACT VersionedConfigDomain + REUSE base-file-starter"
pre_flow: "Command (write-heavy: snapshot, rollback, import)"
pre_feature_type: "NEWBUILD"
status: complete
---

# Brainstorm Notes: Configuration Management System

## Date
2026-09-30

## Context
Thiết kế hệ thống quản lý cấu hình đa domain (Menu, Config, i18n, Department) với:
- Export/Import (Excel, CSV, JSON) — backup & migrate
- Audit Trail — 5 lần thay đổi gần nhất
- Snapshot/Rollback — named milestones, grouped changes
- High abstraction — dễ thêm config domain mới

**From Research**: Đã research Envers, Configu, Spring Cloud Config → quyết định BUILD custom.

## Questions Asked & Answers

### Q1: Export/Import approach?
**A**: REUSE base-file-starter hoàn toàn. Mỗi config domain chỉ cần implement `ExportTemplate<T>` + `ImportRowMapper<T>` + `ImportRowValidator<T>`.

### Q2: Versioning/Snapshot abstraction level?
**A**: HIGH ABSTRACT. Tạo `VersionedConfigDomain<E>` interface → mỗi domain implement → tự động có change tracking, snapshot, rollback.

### Q3: i18n approach?
**A**: Hybrid — File-based làm fallback (default messages), DB chỉ override. Admin quản lý override messages.

### Q4: Vị trí VersionedConfigDomain?
**A**: Trong system-admin-service. Move sang base-core khi đã chứng minh stable qua 2-3 domains.

### Q5: Priority domains?
**A**: system_configs → i18n_messages → menu_items → departments (theo thứ tự complexity tăng dần)

### Q6: History depth?
**A**: 5 lần gần nhất mặc định (có pagination xem thêm), retention 6 tháng.

---

## Approaches Considered

### Approach 1: LOW ABSTRACT — Envers + Manual Export ❌
- Pros: Ít code, Hibernate auto-manage
- Cons: Không có cross-domain snapshot, không named milestones, không custom export templates
- **Rejected**: Không đáp ứng requirement cross-domain grouping

### Approach 2: MEDIUM ABSTRACT — Shared Service + Manual Calls ⚠️
- Pros: Flexible, domain services tự gọi khi cần
- Cons: Mỗi domain phải tự gọi `configChangeService.recordChange(...)` → dễ quên, boilerplate
- **Rejected**: Không đủ "abstract" theo yêu cầu user

### Approach 3: HIGH ABSTRACT — VersionedConfigDomain Interface ✅ SELECTED
- Pros: Mỗi domain chỉ implement interface → auto change tracking, snapshot, rollback
- Cons: Phức tạp hơn khi implement, cần framework-level thinking
- **Selected**: Đáp ứng yêu cầu "abstract ở mức cao"

---

## Selected Direction

### Architecture Overview

```
    ┌─────────────────────────────────────────────────────────────────┐
    │                    system-admin-service                         │
    │                                                                 │
    │  ┌─────────────────────────────────────────────────────────────┐ │
    │  │  shared/versioning/  ← "Config Versioning Framework"       │ │
    │  │                                                             │ │
    │  │  ┌──────────────────────────────────────────────────────┐   │ │
    │  │  │ VersionedConfigDomain<E>  (INTERFACE)                │   │ │
    │  │  │  ├── configDomain: String                            │   │ │
    │  │  │  ├── entityTable: String                             │   │ │
    │  │  │  ├── findById(id): E?                                │   │ │
    │  │  │  ├── findAll(domainId): List<E>                      │   │ │
    │  │  │  ├── toSnapshot(entity): JsonNode                    │   │ │
    │  │  │  ├── fromSnapshot(json): E                           │   │ │
    │  │  │  ├── computeDiff(old, new): Map<String,FieldChange>  │   │ │
    │  │  │  └── applyState(entity): E  (for rollback)           │   │ │
    │  │  └──────────────────────────────────────────────────────┘   │ │
    │  │                        ▲                                    │ │
    │  │          implements    │                                    │ │
    │  │  ┌─────────────────────┼─────────────────────────────────┐  │ │
    │  │  │                     │                                 │  │ │
    │  │  ├── SystemConfigVersionedDomain                         │  │ │
    │  │  ├── I18nMessageVersionedDomain                          │  │ │
    │  │  ├── MenuVersionedDomain                                 │  │ │
    │  │  └── DepartmentVersionedDomain                           │  │ │
    │  │                                                          │  │ │
    │  └──────────────────────────────────────────────────────────┘  │ │
    │                                                                 │
    │  ┌─────────────────────────────────────────────────────────────┐ │
    │  │  shared/versioning/application/                             │ │
    │  │                                                             │ │
    │  │  ConfigChangeTracker  (uses VersionedConfigDomain registry) │ │
    │  │  ├── recordChange(domain, entityId, operation, old, new)    │ │
    │  │  ├── getHistory(domain, entityId, limit=5)                  │ │
    │  │  └── getDiff(changeId)                                      │ │
    │  │                                                             │ │
    │  │  ConfigSnapshotManager                                      │ │
    │  │  ├── createSnapshot(name, description, domain?)              │ │
    │  │  ├── commitSnapshot(id)                                     │ │
    │  │  ├── applySnapshot(id)                                      │ │
    │  │  ├── rollbackSnapshot(id)                                   │ │
    │  │  └── compareSnapshots(id1, id2)                             │ │
    │  └─────────────────────────────────────────────────────────────┘ │
    │                                                                 │
    │  ┌─────────────────────────────────────────────────────────────┐ │
    │  │  REUSE from base-file-starter (ZERO new export code!)       │ │
    │  │                                                             │ │
    │  │  ExportTemplate<T> implementations (1 per domain):          │ │
    │  │  ├── @Bean fun menuExportTemplate(): ExportTemplate<Menu>   │ │
    │  │  ├── @Bean fun configExportTemplate(): ExportTemplate<Cfg>  │ │
    │  │  ├── @Bean fun i18nExportTemplate(): ExportTemplate<I18n>   │ │
    │  │  └── @Bean fun deptExportTemplate(): ExportTemplate<Dept>   │ │
    │  │                                                             │ │
    │  │  ImportRowMapper<T> + ImportRowValidator<T> (1 pair/domain)  │ │
    │  │                                                             │ │
    │  │  → ExportService auto-discovers templates via Spring DI     │ │
    │  │  → ImportService auto-handles async, batch, progress        │ │
    │  └─────────────────────────────────────────────────────────────┘ │
    └─────────────────────────────────────────────────────────────────┘
```

### How "Adding a New Config Domain" Works

```
Để thêm 1 config domain MỚI (ví dụ: "Payment Config"):

1. Tạo entity:
   @Entity
   class PaymentConfigEntity : SnowflakePersistentAuditableEntity() { ... }

2. Implement VersionedConfigDomain:
   @Component
   class PaymentConfigVersionedDomain : VersionedConfigDomain<PaymentConfigEntity> {
       override val configDomain = "PAYMENT_CONFIG"
       override val entityTable = "payment_configs"
       override fun toSnapshot(entity) = objectMapper.valueToTree(entity)
       override fun fromSnapshot(json) = objectMapper.treeToValue(json, PaymentConfigEntity::class.java)
       override fun computeDiff(old, new) = DiffUtils.computeFieldDiff(old, new)
       override fun applyState(entity) = repository.save(entity)
   }

3. Implement ExportTemplate (for base-file-starter):
   @Bean
   fun paymentConfigExport() = object : ExportTemplate<PaymentConfigEntity> {
       override val templateId = "payment-config"
       override val columns = listOf(
           ColumnDefinition("Code", "code"),
           ColumnDefinition("Value", "value"),
           ColumnDefinition("Gateway", "gateway")
       )
       override fun dataQuery(filter) = repository.streamAll()
   }

4. Implement ImportRowMapper + ImportRowValidator (for base-file-starter)

DONE! Tự động có:
✅ Export CSV/Excel (via ExportService)
✅ Import async with progress (via ImportService)
✅ Change tracking (via ConfigChangeTracker)
✅ Snapshot/Rollback (via ConfigSnapshotManager)
✅ Audit trail (via config_change_history)
```

### Key Insight: Base-file-starter Mapping

```
┌──────────────────────────────────────────────────────────────────┐
│  ĐÃ CÓ TRONG BASE-FILE-STARTER     │  CẦN LÀM TRONG SYSTEM-ADMIN  │
│  (KHÔNG VIẾT LẠI!)                   │  (CHỈ IMPLEMENT INTERFACES)    │
├──────────────────────────────────────┼────────────────────────────────┤
│  ExportService                       │  ExportTemplate<T> per domain  │
│    ├── Template registry             │    ├── templateId              │
│    ├── Strategy selection            │    ├── columns                 │
│    └── Auto-split ZIP                │    └── dataQuery()             │
│                                      │                                │
│  CsvExportStrategy (streaming)       │  (nothing — auto-registered)   │
│  ExcelExportStrategy (SXSSFWorkbook) │  (nothing — auto-registered)   │
│  ExportSanitizer (cell injection)    │  (nothing — auto-configured)   │
│                                      │                                │
│  ImportService                       │  ImportRowMapper<T> per domain │
│    ├── File validation               │    └── mapRow()                │
│    ├── Async processing              │  ImportRowValidator<T>         │
│    ├── Batch chunking                │    └── validate()              │
│    ├── Progress tracking             │                                │
│    └── Virtual Thread support        │  Processor function per domain │
│                                      │    └── (items) -> saveAll()    │
│  ImportProgressNotifier              │                                │
│    ├── Polling (default)             │  (nothing — auto-configured)   │
│    └── SSE (optional)                │                                │
└──────────────────────────────────────┴────────────────────────────────┘

Missing in base-file-starter (consider adding):
  ⚠️ JSON export strategy (only CSV + Excel exist)
  ⚠️ Multi-sheet Excel export (current = 1 sheet per template)
```

### i18n Hybrid Approach

```
┌─────────────────────────────────────────────────────────────────┐
│  i18n Message Resolution                                         │
│                                                                  │
│  1. Frontend requests: GET /api/public/i18n/{locale}            │
│                                                                  │
│  2. Backend resolution:                                          │
│     ┌─────────────┐    ┌──────────────────┐   ┌───────────────┐ │
│     │ Redis Cache  │──▶│ DB i18n_messages  │──▶│ File fallback │ │
│     │  (60min TTL) │   │  (override only)  │   │ (defaults)    │ │
│     └─────────────┘    └──────────────────┘   └───────────────┘ │
│                                                                  │
│  3. Merge strategy:                                              │
│     file_messages.merge(db_overrides) → final bundle            │
│                                                                  │
│  4. Admin CRUD:                                                  │
│     - Admin sees FULL bundle (file + DB overrides)              │
│     - Admin creates/edits → saves to DB (override)              │
│     - Admin deletes → removes override (falls back to file)     │
│     - File defaults shown as "readonly" in admin UI             │
│                                                                  │
│  5. Export: exports ONLY DB overrides (file defaults excluded)   │
│  6. Import: imports as DB overrides                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## Pre-classifications (preliminary)
- Feature type: **NEWBUILD**
- Flow type: **Command** (write-heavy: snapshot, rollback, import) + **Query** (history, export)
- Affected modules:
  - `system-admin-service/shared/versioning/` (NEW module)
  - `system-admin-service/config/` (system configs CRUD — NEW)
  - `system-admin-service/i18n/` (i18n messages CRUD — NEW)
  - `system-admin-service/menu/` (EXTEND with versioning)
  - `system-admin-service/organization/` (EXTEND with versioning)
  - `base-core/base-file-starter` (consider: add JSON export strategy)

---

---

## Base-Core Reuse & Extension Mapping

| Need | base-core Component | Action | Details |
|------|---------------------|--------|---------|
| Export CSV | `CsvExportStrategy` | REUSE as-is | Streaming CSV row-by-row |
| Export Excel (Single sheet) | `ExcelExportStrategy` | REUSE as-is | Apache POI SXSSF streaming 100 rows buffer |
| Export Excel (Multi-sheet) | `MultiSheetExcelExportStrategy` | **NEW in base-core** | Specialized strategy for multi-sheet/graph tables (Debate Resolved: Separate strategy) |
| Export JSON (Simple) | `SimpleJsonExportStrategy` | **NEW in base-core** | Streaming Jackson generator for single table / flat entity |
| Export JSON (Relational Graph) | `RelationalJsonExportStrategy` | **NEW in base-core** | Graph/hierarchical multi-table with FK ordering & checksum |
| Multi-sheet Template | `MultiSheetExportTemplate` | **NEW in base-core** | Template specifying multiple sheets & data streams |
| Export orchestration | `ExportService` | EXTEND | Support JSON format + route `MultiSheetExportTemplate` |
| Export column config | `ExportConfig`, `ColumnDefinition` | REUSE as-is | Shared column metadata |
| Import async | `ImportService` | REUSE as-is | Async processing with job status & Virtual Threads |
| Import batch | `BatchImportJobFactory` | REUSE as-is | Route large files (>50MB) to Spring Batch |
| Import progress | `ImportProgressNotifier` | REUSE as-is | SSE / Polling progress tracker |
| Import validation | `ImportRowValidator` | REUSE interface | Per-domain row validator |
| Import Relational Engine | `RelationalImportCoordinator` | **NEW in base-core** | Multi-table coordinator with `ImportStrategyMode` per table |
| Entity base class | `SnowflakePersistentAuditableEntity` | REUSE as-is | Snowflake ID + Auditing fields |
| Transactional Outbox | `spring-modulith-starter-jpa` | REUSE as-is | `EVENT_PUBLICATION` table for zero-loss events |
| Audit Event Listener | `@ApplicationModuleListener` | REUSE as-is | Non-blocking async handling after commit |
| Audit Storage SPI | `ConfigAuditStorageProvider` | **NEW in system-admin** | Pluggable storage: PostgreSQL JSONB (default) vs MongoDB |
| Batch Audit Buffer | `BatchAuditBuffer` | **NEW in system-admin** | Micro-batching + graceful flush on JVM shutdown |

---

## Deep Dive Architecture & Resolved Decisions

### 1. Abstract Base Code: JSON & Complex Relational Export/Import Engine

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       BASE-CORE ABSTRACT EXTENSIONS                         │
│                                                                             │
│  [Export Format]                                                            │
│  enum ExportFormat { CSV, EXCEL, JSON }                                     │
│                                                                             │
│  [Export Strategy Hierarchy]                                                │
│  ExportStrategy<T>                                                          │
│  ├── CsvExportStrategy<T> (existing)                                        │
│  ├── ExcelExportStrategy<T> (existing - single sheet)                       │
│  ├── SimpleJsonExportStrategy<T> (NEW - default fallback for flat entities)  │
│  └── MultiSheetExcelExportStrategy (NEW - composite sheets)                 │
│                                                                             │
│  [Relational Graph Export]                                                  │
│  RelationalExportTemplate<R>                                                │
│  └── dataQueryGraph() -> RootGraph (parent + children + dependencies)       │
│                                                                             │
│  [Multi-Table Import Coordination & Strategy Modes]                         │
│  enum class ImportStrategyMode {                                            │
│      TRUNCATE_AND_LOAD,  // Dành cho lookup/ref data độc lập                │
│      DELETE_AND_INSERT,  // Xóa scope con & insert lại theo sequence        │
│      UPSERT_MERGE,       // So khớp Unique Key/Code -> Update / Insert      │
│      PATCH_VALUES        // Chỉ cập nhật các trường thay đổi                │
│  }                                                                          │
│                                                                             │
│  interface TableImportHandler<E> {                                          │
│      val tableName: String                                                  │
│      val order: Int // Topological execution order                          │
│      val strategyMode: ImportStrategyMode                                   │
│      fun execute(records: List<E>, context: ImportContext)                  │
│  }                                                                          │
│                                                                             │
│  class DefaultSimpleImportHandler<T>(repo) : TableImportHandler<T>         │
│  // Default simple implementation: Giảm 90% boilerplate cho bảng thông thường │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Chi tiết giải pháp Import:
1. **`DELETE_AND_INSERT` (Bảo toàn Sequence & Hierarchy)**:
   - Dùng cho Menu cây phân cấp (`menu -> menu_item -> permission`): Khi import, hệ thống xóa các `menu_item` của Menu tương ứng, sau đó insert lại theo đúng thứ tự mảng JSON để giữ nguyên ID liên tục, `sort_order`, và cấu trúc cây không bị lệch sequence.
2. **`UPSERT_MERGE` (Cập nhật không phá hủy)**:
   - Dùng cho `system_configs`, `i18n_messages`: Đối chiếu theo Natural Key (`config_key`, `msg_key`). Record nào đã tồn tại trong DB thì update value & metadata, record mới thì insert.
3. **`TRUNCATE_AND_LOAD` (Fast Overwrite)**:
   - Dùng khi import snapshot full môi trường hoặc bảng độc lập: Truncate bảng đích và batch insert trực tiếp, tối ưu tốc độ tối đa.
4. **`DefaultSimpleImportHandler<T>`**:
   - Implementation mặc định tích hợp sẵn với Spring Data JPA `JpaRepository<T, ID>`, tự động upsert/save theo Entity ID mà domain không cần viết thêm handler phức tạp.

---

### 2. Debate & Trade-off: Multi-sheet Excel Export

**Vấn đề**: Mở rộng trực tiếp `ExcelExportStrategy` hay tách riêng `MultiSheetExcelExportStrategy`?

| Tiêu chí | Phương án A: Gộp vào `ExcelExportStrategy` | Phương án B: Tách riêng `MultiSheetExcelExportStrategy` (SELECTED) |
|---|---|---|
| **Single Responsibility (SRP)** | ❌ Vi phạm. 1 class vừa lo streaming flat single sheet, vừa quản lý tab, multiple sheet definitions. | ✅ Tuân thủ. Mỗi strategy có 1 nhiệm vụ rõ ràng. |
| **Open-Closed Principle (OCP)** | ❌ Cần sửa đổi code đã chạy ổn định và sửa đổi interface contract `ExportStrategy<T>`. | ✅ Tuân thủ. Mở rộng tính năng mới mà không đụng chạm đến code cũ đang production. |
| **Type Safety & Contract** | ❌ Chữ ký `export(Stream<T>)` bị gượng ép khi `T` đại diện cho nhiều bảng khác schema. | ✅ Chuẩn hóa với `MultiSheetExportTemplate` định nghĩa rõ `List<SheetExportDefinition<*>>`. |
| **Backward Compatibility** | ⚠️ Rủi ro phát sinh bug hồi quy (regression) cho các service đang dùng `ExcelExportStrategy`. | ✅ 100% Backward Compatible. Code hiện tại không bị ảnh hưởng. |
| **Code Duplication** | ✅ Không trùng code. | ✅ Tái sử dụng chung `ExcelCellWriter` và `ExportSanitizer` cho styling, date/number formatting. |

**Quyết định**: **TÁCH RIÊNG `MultiSheetExcelExportStrategy`**.
- Giữ nguyên `ExcelExportStrategy<T>` cho single table/domain thông thường.
- Bổ sung `MultiSheetExcelExportStrategy` xử lý multi-sheet workbook, nhận `MultiSheetExportTemplate`.
- `ExportService` tự động kiểm tra: nếu template là `MultiSheetExportTemplate` thì ủy quyền cho `MultiSheetExcelExportStrategy`.

---

### 3. Non-Blocking Event Tracking, Batch Persistence & Polyglot Storage (PostgreSQL Primary Default + MongoDB Optional)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│               EVENT-DRIVEN AUDIT & PLUGGABLE STORAGE ARCHITECTURE           │
│                                                                             │
│  [Main Thread - CRUD Execution]                                             │
│  Admin CRUD Request ──▶ ConfigDomainService (Menu / Config / i18n)           │
│                                │                                            │
│                                ├── 1. Ghi DB Nghiệp vụ (PostgreSQL)         │
│                                └── 2. Publish Domain Event:                 │
│                                       ConfigDomainChangedEvent              │
│                                                  │                          │
│  ════════════════════════════════════════════════╪════════════════════════  │
│  [Transactional Outbox - Zero Loss Guarantee]    ▼                          │
│  ★ DEFAULT PRIMARY: PostgreSQL EVENT_PUBLICATION table                      │
│    - Cùng Local ACID Transaction với dữ liệu chính                          │
│    - Tránh Dual-Write Problem, cam kết Zero-Loss khi JVM ngủm/crash         │
│  ☆ OPTIONAL EXTENSION: MongoDB Event Store (khi chạy domain trên NoSQL)     │
│                                                  │                          │
│  ════════════════════════════════════════════════╪════════════════════════  │
│  [Async Background Processing]                   ▼ (After Commit)           │
│  @ApplicationModuleListener (Async / Virtual Thread)                        │
│                                │                                            │
│                                ▼                                            │
│                    BatchAuditCollector (Buffer)                             │
│                    ├── In-memory RingBuffer / LinkedQueue                   │
│                    ├── Trigger: Batch Size (100) OR Time Window (500ms)     │
│                    └── SmartLifecycle Flush on JVM Shutdown                 │
│                                │                                            │
│  ═════════════════════════════════════════════════════════════════════════  │
│  [Pluggable Storage SPI - ConfigAuditStorageProvider]                       │
│                                │                                            │
│               ┌────────────────┴────────────────┐                           │
│               ▼                                 ▼                           │
│   ★ DEFAULT PRIMARY (Code sẵn 100%):   ☆ OPTIONAL PLUGGABLE EXTENSION:     │
│   PostgreSqlJsonbAuditStorage          MongoDbAuditStorage                  │
│   - sys_config_snapshot table          - config_audit_snapshots collection  │
│   - payload: JSONB column              - Schemaless Document Storage        │
│   - GIN Index for fast queries         - High-throughput Sharding           │
│   - Không cần MongoDB vẫn chạy mượt    - Bật qua: app.config.audit.storage  │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Cơ chế chống mất dữ liệu khi hệ thống bị sập ("bị ngủm"):
1. **Primary Default: PostgreSQL Transactional Outbox (Zero-Loss Guarantee)**:
   - **Code sẵn dùng mặc định 100% trên PostgreSQL**: Không phụ thuộc MongoDB.
   - Khi transaction nghiệp vụ commit vào PostgreSQL, record event đồng thời được ghi vào bảng `EVENT_PUBLICATION` trong CÙNG một transaction ACID. Điều này giải quyết triệt để bài toán **Dual-Write Problem** mà không cần 2-Phase Commit hay Distributed Tracing phức tạp.
   - Nếu JVM bị tắt đột ngột trước/trong khi lưu audit: Khi server khởi động lại, Spring Modulith tự động scan các event chưa hoàn thành (`completionDate IS NULL`) và replay lại. **Cam kết 100% không mất mát lịch sử!**
2. **MongoDB Outbox & Storage (Optional Pluggable Alternative)**:
   - MongoDB là database cực kỳ thích hợp cho Audit History và Outbox khi hệ thống phát triển quy mô lớn (High throughput, schemaless documents).
   - Thiết kế dạng Pluggable: Hệ thống code sẵn cấu trúc SPI để khi bật MongoDB (`app.config.audit.storage-type=mongodb` hoặc cấu hình Mongo Outbox), hệ thống sẽ định tuyến dữ liệu audit/outbox sang MongoDB mà **không làm thay đổi code nghiệp vụ chính**. Nếu không có MongoDB, hệ thống vẫn vận hành trơn tru hoàn toàn trên PostgreSQL.
3. **Batch Buffer & Graceful Shutdown**:
   - `BatchAuditCollector` gom nhóm các snapshot thành các batch (micro-batching 100 records hoặc 500ms) để giảm thiểu I/O disk cho PostgreSQL.
   - Đăng ký Spring `SmartLifecycle` / `DisposableBean`: Khi nhận tín hiệu SIGTERM / shutdown, tiến trình sẽ chặn tắt cho đến khi buffer được flush sạch vào Database.
4. **Storage SPI Đa Nền Tảng (Polyglot DB)**:
   - `PostgreSqlJsonbAuditStorageProvider` (**DEFAULT PRIMARY - READY OUT-OF-THE-BOX**): Lưu trữ ngay trong PostgreSQL hiện tại, payload là JSONB, tối ưu truy vấn bằng PostgreSQL GIN index, 0 phát sinh hạ tầng.
   - `MongoDbAuditStorageProvider` (**OPTIONAL EXTENSION**): Triển khai sẵn interface cho MongoDB, kích hoạt linh hoạt khi dự án yêu cầu mở rộng lưu trữ Document đa dạng.

---

## Resolved Questions Summary

- [RESOLVED] **`JsonExportStrategy` & Complex Relational Import/Export**: Bổ sung vào `base-core` để toàn bộ microservice tái sử dụng. Cung cấp `SimpleJsonExportStrategy` (default) cho bảng đơn giản và `RelationalExportTemplate` + `ImportStrategyMode` (`TRUNCATE_AND_LOAD`, `DELETE_AND_INSERT`, `UPSERT_MERGE`) cho quan hệ phức tạp.
- [RESOLVED] **Multi-sheet Excel Export**: TÁCH RIÊNG `MultiSheetExcelExportStrategy` trong `base-file-starter`, sử dụng chung engine Apache POI và helper core với `ExcelExportStrategy` đơn sheet. Giữ code đơn giản, không phá vỡ backward compatibility.
- [RESOLVED] **Change Tracking, Batch Buffer & Polyglot Database**:
  * **Primary Default (Code sẵn 100%)**: PostgreSQL đảm nhận cả Transactional Outbox (`EVENT_PUBLICATION`) và Audit Snapshot (`sys_config_snapshot` JSONB), bảo đảm Zero-loss và không phụ thuộc hạ tầng ngoài.
  * **Optional Pluggable**: Thiết kế SPI sẵn sàng mở rộng MongoDB cho cả Audit Storage lẫn Outbox khi có cấu hình bật lên.
- [RESOLVED] **Rollback conflict detection**: Khi entity đã thay đổi sau snapshot → warn + force option.
- [RESOLVED] **Import auto-snapshot**: Luôn tạo snapshot trước khi tiến hành import.

---

## Open Questions for URD Analysis
- Performance benchmark: Bao nhiêu config records tối đa per domain trong 1 lần export/snapshot?
- Concurrent admin editing: Đã chốt dùng Optimistic Locking (`@Version` field trên entity).
- Export scheduling: Cần bổ sung auto-export định kỳ (backup cron job) trong giai đoạn tiếp theo hay làm manual on-demand trước?

---

## Ready for OpenSpec
Toàn bộ các câu hỏi thiết kế kiến trúc then chốt đã được giải quyết triệt để. Sẵn sàng khởi chạy:
```bash
/wf_openspec config-management-system
```

