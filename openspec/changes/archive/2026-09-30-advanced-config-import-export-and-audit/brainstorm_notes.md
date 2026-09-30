---
type: brainstorm_notes
change: advanced-config-import-export-and-audit
date: 2026-09-30
selected_direction: "Two-Phase Enterprise Framework: Base-Core Abstractions with Dynamic JPA Metamodel + System-Admin Pluggable Dual-Storage Outbox"
pre_flow: "Hybrid (Streaming Query + Transactional Command + Outbox Event)"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Advanced Config Import/Export & Audit Framework

## 1. Thông tin Chung

| Mục | Nội dung |
|-----|----------|
| **Tên Change** | `advanced-config-import-export-and-audit` |
| **Ngày thực hiện** | 2026-09-30 |
| **Input Source** | Mode 3 (Handoff từ `/wf_feature_research` kết hợp Brainstorm định hướng chuyên sâu) |
| **Dự án liên quan** | `components/base-core` (`base-file-starter`, `base-audit-starter`) & `services/system-admin-service` |
| **Định hướng lựa chọn** | Triển khai 2 Phase: (Phase 1) Lõi trừu tượng và động cơ streaming thông minh tại `base-core`; (Phase 2) Tích hợp nghiệp vụ cấu hình, Outbox kiểm toán và kho lưu trữ kép tại `system-admin-service`. |

---

## 2. Bối cảnh & Mục tiêu Định hình

Hệ thống quản lý cấu hình vi dịch vụ cần một khung xuất/nhập tệp và kiểm toán phiên bản nâng cao nhằm:
1. **Xuất/Nhập Đồ thị Quan hệ Đa bảng (Relational Graphs)**: Khắc phục điểm yếu chỉ hỗ trợ dữ liệu phẳng của `base-file-starter`, hỗ trợ các cấu trúc dữ liệu phân tầng có khóa ngoại (Menu, Department Tree) với chữ ký băm bảo toàn dữ liệu (SHA-256 Checksum) và 4 chế độ nạp dữ liệu an toàn (`TRUNCATE_AND_LOAD`, `DELETE_AND_INSERT`, `UPSERT_MERGE`, `PATCH_VALUES`).
2. **Xuất Excel Đa Sheet Không Cần Cấu hình Cột Thủ công**: Tách riêng `MultiSheetExcelExportStrategy` tiếp nhận tệp workbook đa sheet, sử dụng cơ chế **Dynamic JPA Metamodel** tự động trích xuất thuộc tính thực thể, tiêu đề cột và kiểu dữ liệu mà không bắt buộc lập trình viên phải khai báo `ColumnDefinition` thủ công; đồng thời kiểm soát bộ nhớ $O(1)$ RAM qua `SXSSFWorkbook(100)` và Style Pool dùng chung.
3. **Kiểm toán Phi đồng bộ Zero-loss & Kho Lưu trữ Kép (Dual-Storage)**: Tự động ghi vết cấu hình qua Spring Modulith Transactional Outbox và bộ đệm vi mẻ trong bộ nhớ RAM (`BatchAuditCollector` với `SmartLifecycle` Phase 10,000); thiết kế `ConfigAuditStorageProvider` SPI cho phép chuyển đổi độc lập (Pluggable Exclusive Switching) giữa PostgreSQL JSONB và MongoDB NoSQL.

---

## 3. Các Câu hỏi Thảo luận & Quyết định Đã Thống nhất (Questions & Decisions)

### Q1: Lộ trình và phạm vi phân bổ công việc (Phasing & Scope)
- **Câu hỏi**: Nên tổ chức phạm vi công việc giữa `base-core` và `system-admin-service` như thế nào?
- **Quyết định**: **Triển khai toàn diện theo 2 Phase**:
  - **Phase 1**: Xây dựng toàn bộ các abstractions, SPI, động cơ streaming, thuật toán sắp xếp Topo Kahn và Dynamic JPA Metamodel trong `components/base-core` (`base-file-starter`).
  - **Phase 2**: Tích hợp các domain cấu hình cụ thể (Menu, Org/Department, Common Config, Feature Flags), cấu hình Outbox Event Listener, bộ đệm vi mẻ `BatchAuditCollector`, và bộ điều hướng Dual-Storage (PostgreSQL/MongoDB) trong `services/system-admin-service`.

### Q2: Giải quyết ràng buộc khóa ngoại tự tham chiếu (Self-referencing FK) khi Import
- **Câu hỏi**: Đối với các bảng có cấu trúc cây tự tham chiếu (như Menu cha-con, Phòng ban cha-con), cơ chế Import giải quyết ràng buộc khóa ngoại (Self-referencing FK) theo hướng nào?
- **Quyết định**: **Kết hợp Two-Pass Import & Deferred Constraints**:
  - Bật `SET CONSTRAINTS ALL DEFERRED` trong transaction PostgreSQL cục bộ để cho phép hoãn kiểm tra ràng buộc FK đến cuối transaction commit.
  - Áp dụng cơ chế nạp 2 lượt (Two-Pass Execution): Lượt 1 chèn toàn bộ các bản ghi với `parent_id = null`; Lượt 2 cập nhật lại `parent_id` liên kết cây gia phả. Đảm bảo an toàn 100% không bao giờ gặp lỗi `Foreign Key Constraint Violation`.
  - Kết hợp thuật toán Kahn sắp xếp thứ tự bảng cha trước bảng con khi chèn, và bảng con trước bảng cha khi xóa.

### Q3: Cơ chế Kho lưu trữ kép Dual-Storage (PostgreSQL + MongoDB)
- **Câu hỏi**: Về cơ chế Dual-Storage (PostgreSQL + MongoDB) cho module Audit & Snapshot, chiến lược lưu trữ được tổ chức thế nào?
- **Quyết định**: **Pluggable Exclusive Switching**:
  - Cung cấp giao diện `ConfigAuditStorageProvider` SPI.
  - Cho phép cấu hình chọn 1 trong 2 qua profile/property: `app.config.audit.storage-type: postgresql` (mặc định, lưu bảng `domain_config_history` với cột JSONB và chỉ mục GIN) hoặc `mongodb` (lưu collection `config_audit_events` và `config_snapshots`).
  - Mặc định sử dụng PostgreSQL JSONB để không bắt buộc cài đặt thêm MongoDB đối với các hệ thống quy mô nhỏ hoặc môi trường local development; khi cần scale lớn chỉ cần đổi thuộc tính sang `mongodb`.

### Q4: Cơ chế Tổ chức và Thiết lập Sheet trong Multi-Sheet Excel Export
- **Câu hỏi**: Cơ chế tổ chức và chọn lọc các sheet trong Multi-Sheet Excel Export hoạt động như thế nào?
- **Quyết định**: **Dynamic JPA Metamodel Introspection**:
  - Tự động quét metadata JPA (`jakarta.persistence.metamodel.Metamodel`) từ `EntityManager` để tự động suy diễn danh sách thuộc tính thực thể, tiêu đề cột và kiểu dữ liệu.
  - Lập trình viên không cần phải viết `ColumnDefinition` thủ công cho từng bảng — loại bỏ 100% mã thừa (boilerplate).
  - Vẫn cho phép ghi đè (override) tiêu đề hoặc thứ tự cột thông qua annotation hoặc cấu hình tùy biến khi cần thiết.

---

## 4. Kiến trúc Mục tiêu Tổng thể (Target Architecture Synthesis)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          System Admin Service                               │
│                                                                             │
│  [REST API Controller: /api/v1/configs/*]                                   │
│  ├── /export/all (Multi-Sheet Excel via Dynamic JPA Metamodel)              │
│  ├── /domains/{domain}/export (Streaming JSON with SHA-256 Checksum)        │
│  ├── /import (Policy-driven: Truncate, Delete-Insert, Upsert, Patch)        │
│  └── /milestones, /diff, /rollback (Configuration Lifecycle Management)     │
│                                                                             │
│  [Domain Layer]                                                             │
│  ├── MenuDomain, OrgDomain, CommonConfigDomain, I18nMessageDomain           │
│  └── ConfigDomainRegistry                                                   │
│                                                                             │
│  [Event & Audit Layer]                                                      │
│  ├── Outbox Event Publisher: ConfigDomainChangedEvent                       │
│  ├── @ApplicationModuleListener (Async worker)                              │
│  ├── BatchAuditCollector (LinkedBlockingQueue 20k, SmartLifecycle 10k)      │
│  └── ConfigAuditStorageProvider (SPI)                                       │
│       ├── PostgreSqlJsonbAuditStorageProvider (Default: GIN index JSONB)    │
│       └── MongoAuditStorageProvider (Secondary Scale: Document Collections)│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Inherits & Leverages
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                    Base-Core: base-file-starter                             │
│                                                                             │
│  [Export Subsystem]                                                         │
│  ├── SimpleJsonExportStrategy<T> (Streaming JSON Array)                     │
│  ├── RelationalJsonExportStrategy (Hierarchical Graph + SHA-256 Payload)    │
│  ├── ExcelExportStrategy<T> (Existing Single Sheet - Unchanged)             │
│  ├── MultiSheetExcelExportStrategy (NEW: SXSSFWorkbook 100, Shared Styles)  │
│  ├── DynamicJpaMetamodelSheetExtractor (NEW: Auto-introspect Entity Schema) │
│  └── ExportSanitizer (CWE-1236 Formula Injection Neutralizer)               │
│                                                                             │
│  [Import Subsystem]                                                         │
│  ├── RelationalImportCoordinator (ACID Transactional Orchestrator)          │
│  ├── TopologicalDependencySorter (Kahn's DAG Algorithm in Kotlin)          │
│  ├── TableImportHandler<T> (Lifecycle SPI: prepare, process, cleanup)       │
│  ├── DefaultSimpleImportHandler<T, ID> (Spring Data JPA Generic Handler)    │
│  └── TwoPassTreeImportHandler<T, ID> (Deferred FK + 2-Pass Hierarchy)       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Danh mục Thành phần Kỹ thuật Cần Triển khai (Component Inventory)

### 5.1 Module `components/base-core/starters/base-file-starter`
1. `ExportFormat.JSON`: Thêm vào enum `ExportFormat` trong `com.ntt.basecore.domain.file`.
2. `SimpleJsonExportStrategy<T>`: Triển khai `ExportStrategy<T>` sử dụng Jackson `JsonGenerator` streaming trực tiếp ra `OutputStream`.
3. `RelationalJsonExportStrategy`: Triển khai xuất đồ thị quan hệ tiếp nhận `RelationalExportTemplate<R>` và sinh `RelationalExportPayload` kèm SHA-256 checksum.
4. `MultiSheetExcelExportStrategy`: Động cơ xuất Excel đa sheet độc lập với Apache POI `SXSSFWorkbook(100)`, Style Pool dùng chung và `ExportSanitizer`.
5. `DynamicJpaMetamodelSheetExtractor`: Component tự động phân tích JPA Metamodel để sinh `SheetExportDefinition` tự động.
6. `TopologicalDependencySorter`: Triển khai thuật toán Kahn bằng Kotlin thuần kiểm soát thứ tự bảng cha/con và phát hiện chu trình.
7. `TableImportHandler<T>` & `ImportStrategyMode`: Định nghĩa hợp đồng nạp dữ liệu và 4 chế độ cập nhật.
8. `DefaultSimpleImportHandler<T, ID>`: Lớp mẫu tự động nạp bảng phẳng qua Spring Data `JpaRepository`.
9. `TwoPassTreeImportHandler<T, ID>`: Lớp mẫu chuyên dụng cho bảng cây tự tham chiếu với cơ chế 2 lượt nạp an toàn.
10. `RelationalImportCoordinator`: Bộ điều phối nạp dữ liệu đa bảng trong một giao dịch `@Transactional` duy nhất.

### 5.2 Module `services/system-admin-service`
1. `ConfigDomainRegistry`: Đăng ký và quản lý các domain cấu hình trong hệ sinh thái.
2. `ConfigDomainChangedEvent`: Định nghĩa sự kiện domain cho mọi biến động cấu hình.
3. `ConfigDomainEventListener`: `@ApplicationModuleListener` đón nhận sự kiện Outbox sau khi transaction chính commit.
4. `BatchAuditCollector`: Bộ đệm vi mẻ bộ nhớ RAM (100 items / 500ms) kèm `SmartLifecycle` (Phase 10,000) chống thất thoát dữ liệu khi dừng ứng dụng.
5. `ConfigAuditStorageProvider`: Giao diện SPI lưu trữ audit và snapshot.
6. `PostgreSqlJsonbAuditStorageProvider`: Triển khai mặc định lưu trên PostgreSQL `domain_config_history` với cột JSONB và chỉ mục GIN.
7. `MongoAuditStorageProvider`: Triển khai mở rộng trên MongoDB thông qua `MongoTemplate` kích hoạt khi `app.config.audit.storage-type: mongodb`.
8. `ConfigManagementController`: Cung cấp các endpoint REST API:
   - `GET /api/v1/configs/domains/{domain}/export`
   - `GET /api/v1/configs/export/all`
   - `POST /api/v1/configs/import`
   - `POST /api/v1/configs/milestones`
   - `GET /api/v1/configs/domains/{domain}/diff`
   - `POST /api/v1/configs/snapshots/{id}/rollback`

---

## 6. Phân loại Thay đổi (Pre-classifications)

- **Feature Type**: `EXTEND` (Mở rộng tính năng cho `base-file-starter` và `system-admin-service`).
- **Flow Type**: `Hybrid` (Streaming Query cho Export, Transactional Command cho Import & Rollback, Outbox Event cho Audit Trail).
- **Phạm vi tác động (Blast Radius)**:
  - `components/base-core`: Bổ sung thêm các class mới trong `base-file-starter`, hoàn toàn không làm thay đổi các class đang chạy của `ExcelExportStrategy` hay `CsvExportStrategy` (100% Backward Compatible).
  - `services/system-admin-service`: Bổ sung module `versioning` và `shared/file`, không làm ảnh hưởng đến luồng xác thực đăng nhập hay tài khoản người dùng của các service khác.
- **Mức độ Rủi ro (Risk Level)**: **LOW - MEDIUM** (Do tuân thủ triệt để OCP và cơ chế Transactional Outbox tách biệt luồng xử lý).

---

## 7. Các Câu hỏi Mở cho Giai đoạn Thiết kế Kế tiếp (`/wf_openspec`)

- [RESOLVED] Vị trí triển khai: Đưa abstractions vào `base-file-starter`, tích hợp tại `system-admin-service`.
- [RESOLVED] Khóa ngoại tự tham chiếu: Áp dụng Two-Pass Import kết hợp `DEFERRED CONSTRAINTS`.
- [RESOLVED] Đa sheet Excel: Tách riêng `MultiSheetExcelExportStrategy` kết hợp Dynamic JPA Metamodel.
- [RESOLVED] Lưu trữ Audit: Pluggable Exclusive Switching (PostgreSQL JSONB mặc định, MongoDB tùy chọn).
- [RESOLVED] Cấu trúc package trong `base-file-starter`: Đặt các class import/export mới tại `com.ntt.basecore.autoconfigure.file.export` và `com.ntt.basecore.autoconfigure.file.import` — giữ cấu trúc phẳng, không tạo thêm sub-package `relational`. Các class relational (như `RelationalJsonExportStrategy`, `RelationalImportCoordinator`) sẽ nằm chung với các class export/import khác, phân biệt bởi naming convention.
- [RESOLVED] Cơ chế bảo mật quyền nạp cấu hình: SHA-256 checksum là đủ. Không cần bổ sung chữ ký số RSA — SHA-256 đảm bảo tính toàn vẹn dữ liệu (integrity verification), phù hợp cho phạm vi nạp cấu hình nội bộ giữa các môi trường.

---

> **Trạng thái**: Brainstorm hoàn tất và được đồng thuận 100%.
> **Sẵn sàng chuyển tiếp**: Sử dụng `/wf_openspec advanced-config-import-export-and-audit` để sinh bộ tài liệu Proposal, Delta Spec, Design và Tasks.
