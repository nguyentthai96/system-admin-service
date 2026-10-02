# Pre-OpenSpec: config-management-system

> **Type**: NEWBUILD
> **Flow**: Command
> **Source**: URD (Business Analysis, Technical Spec & Brainstorm Notes)
> **Classification Evidence**: VersionedConfigDomain → system-admin-service/shared/versioning → VersionedConfigDomain.kt + base-core/base-file-starter
> **Archive**: N/A
> **Quality Score**: 94/100

## 📋 Feature Summary

Hệ thống Quản lý Cấu hình Tập trung và Đa Miền (Configuration Management System) cung cấp giải pháp toàn diện cho việc quản trị, sao lưu, di chuyển (export/import) và kiểm soát phiên bản cấu hình hệ thống (Menu phân cấp, Cấu hình tham số động, Thông điệp đa ngôn ngữ i18n, Phòng ban/Tenant configs). 

Hệ thống được thiết kế với kiến trúc trừu tượng cấp cao (`VersionedConfigDomain<E>`), tận dụng và mở rộng hạ tầng `base-file-starter` (hỗ trợ JSON quan hệ phức tạp và Excel Multi-sheet), bảo đảm an toàn dữ liệu với cơ chế **Zero-Loss Transactional Outbox** (Spring Modulith Event Publication Registry) kết hợp In-Memory Batch Buffer trên **Primary PostgreSQL**, đồng thời cung cấp Storage SPI sẵn sàng mở rộng sang **MongoDB** khi có nhu cầu.

| Metric | Giá trị |
|--------|---------|
| Số FR | 14 (URD/Business: 10, Enriched: 4) |
| Issues | 2 (🔴: 0, 🟡: 2, 🟢: 0) |
| Open Questions | 0 (Đã giải quyết toàn bộ ở brainstorm) |
| **Quality Score** | **94/100** |

---

## 1. Actors

- **System Administrator (Admin)**: Người quản trị vận hành giao diện admin, thực hiện thêm/sửa/xóa cấu hình, export/import dữ liệu, tạo snapshot mốc milestone và kích hoạt rollback cấu hình.
- **DevOps / Release Engineer**: Người phụ trách triển khai hệ thống, thực hiện migration dữ liệu giữa các môi trường (Dev -> Staging -> Production) bằng file cấu hình JSON/Excel.
- **Internal Microservices (Consumers)**: Các dịch vụ trong hệ thống (như `auth-service`, `notification-service`, `gateway`) gọi API tra cứu cấu hình, menu và i18n messages với cache hiệu năng cao.
- **Background Audit Worker**: Tiến trình ngầm gom batch các sự kiện thay đổi cấu hình từ Transactional Outbox để lưu trữ vào bảng snapshot/history mà không làm gián đoạn luồng nghiệp vụ.

---

## 2. Functional Requirements

### FR-001: Quản lý Cấu hình Đa Miền Tập trung [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cung cấp giao diện và API CRUD tập trung cho các miền cấu hình: Menu Items (phân cấp cây), System Configs (key-value động), i18n Messages (đa ngôn ngữ), và Domain/Tenant Settings khi admin thực hiện thao tác.
- **Validation**:
  * Kiểm tra hợp lệ kiểu dữ liệu cấu hình (String, Number, Boolean, JSON) qua `SysAdminErrorCode.INVALID_CONFIG_TYPE`.
  * Không cho phép tạo menu cha con vòng lặp qua `SysAdminErrorCode.CIRCULAR_REFERENCE`.

### FR-002: Export Dữ liệu Đơn bảng (CSV, Single-sheet Excel, Simple JSON) [URD]
- **Actor**: System Administrator, DevOps
- **Action**: Hệ thống phải cho phép export toàn bộ hoặc một phần danh sách cấu hình của từng domain ra các file định dạng CSV, Excel (.xlsx đơn sheet), hoặc JSON streaming khi có yêu cầu.
- **Validation**:
  * Tái sử dụng `ExportService` và `ExportStrategy` của `base-file-starter`.
  * Sử dụng streaming để không tải toàn bộ dữ liệu vào RAM, chống OOM.
  * Tự động sanitize công thức nguy hiểm chống CSV/Formula Injection.

### FR-003: Export Cấu trúc Quan hệ Phức tạp ra JSON [URD]
- **Actor**: System Administrator, DevOps
- **Action**: Hệ thống phải export được các cấu trúc dữ liệu nhiều bảng có khóa ngoại liên kết (như Menu -> Menu Items -> Permissions/Actions) thành một Payload JSON có cấu trúc cây lồng nhau (Hierarchical Graph) kèm theo metadata (schemaVersion, domain, timestamp, checksum) khi admin yêu cầu export.
- **Validation**:
  * File JSON phải tuân thủ Schema phiên bản đã định nghĩa.
  * Checksum SHA-256 được tính toán và nhúng vào metadata để kiểm tra tính toàn vẹn khi import.

### FR-004: Export Cấu hình Toàn Miền ra Multi-Sheet Excel [URD]
- **Actor**: System Administrator, DevOps
- **Action**: Hệ thống phải hỗ trợ export toàn bộ các bảng trong một miền hoặc toàn bộ các miền cấu hình hệ thống vào DUY NHẤT một file Excel gồm nhiều tab sheets (ví dụ Sheet 1: Menus, Sheet 2: MenuItems, Sheet 3: Roles, Sheet 4: i18n) khi admin lựa chọn Export All.
- **Validation**:
  * Sử dụng `MultiSheetExcelExportStrategy` độc lập, stream từng sheet bằng Apache POI SXSSFWorkbook sliding window.
  * Mỗi sheet hiển thị đúng tên tab và danh sách cột tương ứng với domain đó.

### FR-005: Import Cấu hình với Chế độ Xóa và Tạo lại (DELETE_AND_INSERT) [URD]
- **Actor**: System Administrator, DevOps
- **Action**: Hệ thống phải hỗ trợ chế độ import xóa các bản ghi con trong phạm vi chỉ định và insert lại theo đúng thứ tự mảng dữ liệu khi import các cấu trúc phân cấp (Menu tree) để bảo toàn thứ tự sequence và quan hệ cha-con.
- **Validation**:
  * Thực thi trong 1 transaction an toàn; nếu 1 bản ghi lỗi phải rollback toàn bộ.
  * Tự động gán lại `sort_order` và đường dẫn phân cấp theo đúng vị trí mảng.

### FR-006: Import Cấu hình với Chế độ Cập nhật Hợp nhất (UPSERT_MERGE) [URD]
- **Actor**: System Administrator, DevOps
- **Action**: Hệ thống phải hỗ trợ chế độ import đối chiếu theo Natural/Business Key (`config_key`, `msg_key`) khi import `system_configs` và `i18n_messages`. Nếu bản ghi đã tồn tại thì cập nhật giá trị mới (giữ nguyên ID cũ), nếu chưa có thì insert mới.
- **Validation**:
  * Không làm thay đổi ID hoặc các metadata audit cũ của bản ghi đang có nếu không có thay đổi giá trị.
  * Báo cáo chi tiết số lượng: `insertedCount`, `updatedCount`, `skippedCount`, `failedCount`.

### FR-007: Import Cấu hình với Chế độ Làm sạch Bảng (TRUNCATE_AND_LOAD) [URD]
- **Actor**: DevOps
- **Action**: Hệ thống phải cho phép xóa toàn bộ dữ liệu bảng đích (Truncate) và batch load trực tiếp toàn bộ dữ liệu mới từ file khi triển khai khởi tạo môi trường mới hoặc reset cấu hình tham chiếu.
- **Validation**:
  * Chỉ cho phép thực hiện bởi tài khoản có quyền System Admin cấp cao nhất.
  * Yêu cầu xác nhận mật khẩu hoặc tham số `confirm=true`.

### FR-008: Theo dõi Lịch sử 5 Lần Sửa Đổi Gần Nhất (Audit Trail) [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải tự động lưu vết lịch sử thay đổi của từng bản ghi cấu hình (ai sửa, sửa lúc nào, giá trị trước và sau, lý do thay đổi), mặc định hiển thị 5 phiên bản gần nhất và hỗ trợ phân trang xem thêm.
- **Validation**:
  * Thời gian lưu trữ cấu hình (Retention): tối thiểu 6 tháng.
  * Thông tin người sửa lấy tự động từ Security Context (JWT).

### FR-009: Quản lý Mốc Cấu hình Milestone và Nhóm Thay đổi (Grouped Changes) [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cho phép tạo các Snapshot cấu hình theo mốc có đặt tên (Milestone Tag, ví dụ `v1.2-release-configs`, `tet-holiday-menus`), gom nhóm nhiều thay đổi cross-domain vào một mốc duy nhất khi admin xác nhận tạo mốc.
- **Validation**:
  * Tên Milestone là duy nhất, không trùng lặp.
  * Lưu trữ trạng thái hoàn chỉnh (Full State Snapshot) của các domain liên quan tại thời điểm tạo.

### FR-010: Khôi phục và Chuyển đổi Cấu hình theo Mốc (Rollback & Switching) [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cho phép rollback một domain cấu hình hoặc toàn bộ các domain thuộc một Milestone về trạng thái snapshot đã lưu khi admin kích hoạt chức năng Rollback.
- **Validation**:
  * Hiển thị bảng so sánh sai khác (Diff Viewer: Before vs After) trước khi admin bấm xác nhận.
  * Ghi nhận 1 bản ghi Audit đặc biệt đánh dấu hành động "Rollback to milestone X by Admin Y".

### FR-011: Bắt Sự kiện Thay đổi Bất đồng bộ Non-Blocking [ENRICHED]
- **Actor**: Background Worker
- **Action**: Hệ thống phải publish sự kiện miền (`ConfigDomainChangedEvent`) sau khi transaction nghiệp vụ CRUD chính đã commit thành công (`@ApplicationModuleListener`), không làm tăng độ trễ (latency) của API chính.
- **Validation**:
  * Tách biệt luồng xử lý trên Virtual Thread / Thread Pool riêng.
  * Nếu quá trình audit/snapshot gặp sự cố, transaction nghiệp vụ chính không bị ảnh hưởng.

### FR-012: Đảm bảo Zero-Loss qua Transactional Outbox trên PostgreSQL Primary [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải ghi nhận sự kiện vào bảng `EVENT_PUBLICATION` trong cùng một Local ACID Transaction trên database PostgreSQL mặc định. Khi ứng dụng bị sập, crash hoặc restart đột ngột ("bị ngủm"), hệ thống phải tự động quét và hoàn tất các sự kiện còn dang dở khi khởi động lại.
- **Validation**:
  * Sử dụng Spring Modulith JPA Event Publication Registry.
  * Tự động đánh dấu `completionDate` khi sự kiện xử lý thành công.

### FR-013: Gom Batch Ghi ngầm và Graceful Shutdown [ENRICHED]
- **Actor**: Background Audit Worker
- **Action**: Hệ thống phải gom các sự kiện audit vào `BatchAuditCollector` theo ngưỡng kích thước (100 bản ghi) hoặc khoảng thời gian (500ms) để bulk insert vào database. Khi hệ thống nhận tín hiệu tắt máy (SIGTERM), hook `SmartLifecycle` phải xả hết buffer trước khi tắt hoàn toàn.
- **Validation**:
  * Giảm tối thiểu 80% tần suất I/O ghi đĩa so với ghi lẻ tẻ từng dòng.
  * Không làm thất thoát bất kỳ snapshot nào trong buffer khi tiến trình kết thúc bình thường.

### FR-014: Cảnh báo Xung đột khi Rollback (Conflict Detection) [ENRICHED]
- **Actor**: System Administrator
- **Action**: Hệ thống phải phát hiện nếu có bản ghi cấu hình nào đã bị chỉnh sửa sau thời điểm tạo Snapshot khi admin chuẩn bị rollback; hiển thị danh sách xung đột và yêu cầu chọn chế độ "Bỏ qua bản ghi xung đột" hoặc "Ghi đè bắt buộc (Force Overwrite)".
- **Validation**:
  * So sánh trường `updated_at` hoặc entity `@Version` giữa trạng thái hiện tại và trạng thái trong snapshot.

---

## 3. Non-functional Requirements

- **Performance**:
  * Thời gian phản hồi API CRUD thông thường: ≤ 100ms.
  * Thời gian export dữ liệu < 10,000 bản ghi: ≤ 1.5 giây.
  * Thời gian tạo snapshot: xử lý bất đồng bộ trong nền, thời gian trả về xác nhận cho admin ≤ 200ms.
- **Scalability & Polyglot Database**:
  * Chạy sẵn 100% out-of-the-box trên **PostgreSQL** (bảng `sys_config_snapshot` với cột `JSONB` + GIN Index, bảng `EVENT_PUBLICATION`).
  * Cung cấp SPI `ConfigAuditStorageProvider` sẵn sàng switch sang **MongoDB** qua cấu hình `app.config.audit.storage-type=mongodb` khi muốn lưu trữ schemaless mở rộng.
- **Reliability & Data Integrity**:
  * Cam kết **Zero-Loss**: Không bao giờ mất vết audit/snapshot ngay cả khi JVM sập bất tử.
  * Idempotency: Import cùng 1 file 2 lần ở chế độ `UPSERT_MERGE` phải cho kết quả trạng thái hệ thống đồng nhất.
- **Security**:
  * Phân quyền nghiêm ngặt: Chỉ các vai trò có quyền `SYSTEM_ADMIN` mới được gọi các API Export/Import/Rollback.
  * Chống CSV/Formula Injection bằng `ExportSanitizer` của `base-file-starter`.

---

## 4. Deduplicated & Consolidated

- Yêu cầu export file của từng domain đơn lẻ được hợp nhất thành kiến trúc `ExportTemplate<T>` kết hợp `ExportService` của base-core.
- Không phát hiện xung đột nghiệp vụ giữa các yêu cầu.

---

## 5. Enriched Domain Requirements

### Enriched FRs
- **FR-011**: Bắt sự kiện bất đồng bộ qua `@ApplicationModuleListener` — Đảm bảo hiệu năng cao cho nghiệp vụ CRUD chính.
- **FR-012**: Transactional Outbox qua bảng `EVENT_PUBLICATION` trên PostgreSQL — Cam kết không mất lịch sử khi server "bị ngủm".
- **FR-013**: In-Memory Batch Buffer + Graceful Shutdown — Giảm tải I/O ghi đĩa và chống mất dữ liệu khi shutdown.
- **FR-014**: Cảnh báo xung đột khi Rollback — Tránh vô tình ghi đè các cấu hình khẩn cấp được update sau snapshot.

### External Integrations
| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| PostgreSQL (Primary DB) | Lưu trữ nghiệp vụ, Transactional Outbox và Audit JSONB | Sẵn dùng 100%, không cần thêm hạ tầng |
| MongoDB (Optional SPI) | Lưu trữ Document Audit Snapshots và Outbox mở rộng | Bật qua property cấu hình khi cần |
| Redis Cache | Caching cấu hình hệ thống, menu permission, i18n bundle | TTL 30 phút, auto-evict khi update |

---

## 6. Assumptions

- Hệ thống microservice hiện tại đã có PostgreSQL và Spring Modulith Starter.
- Admin thực hiện các thao tác Rollback/Import trong cửa sổ bảo trì hoặc có ý thức về tác động diện rộng đối với người dùng cuối.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 24/25 | FR-007: Cần định nghĩa chi tiết danh sách bảng cho phép truncate |
| Đầy đủ (Completeness) | 24/25 | FR-010: Cần thêm chi tiết giao diện Diff Viewer ở phía Frontend |
| Nhất quán (Consistency) | 25/25 | Không có mâu thuẫn |
| Kiểm thử được (Testability) | 21/25 | FR-012: Kiểm thử crash server cần môi trường tích hợp đặc thù |
| **Tổng** | **94/100** | **ĐẠT CHUẨN XUẤT SẮC** |

### Chi tiết trừ điểm
| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|---------------|
| 1 | Clarity | -1 | FR-007 | Chưa liệt kê cụ thể bảng nào được truncate | Bổ sung whitelist bảng được phép truncate |
| 2 | Completeness | -1 | FR-010 | Chưa mô tả chi tiết payload diff UI | Sẽ đặc tả chi tiết trong frontend spec |
| 3 | Testability | -4 | FR-012 | Test kịch bản server ngủm đột ngột cần Testcontainers | Bổ sung Integration Test giả lập crash |

---

## 8. Issues & Risks

- 🟡 **Rủi ro khóa ngoại khi Import**: Khi import ở chế độ `DELETE_AND_INSERT`, nếu các bảng khác đang có Foreign Key trỏ tới bản ghi bị xóa thì Postgres sẽ báo lỗi vi phạm ràng buộc FK.
  * *Đề xuất*: Sử dụng Cascade delete hoặc xóa theo đúng thứ tự Topological Sort (từ bảng con đến bảng cha, sau đó insert từ bảng cha đến bảng con).
- 🟡 **Rủi ro Out-of-Memory khi Export toàn bộ**: Khi export toàn bộ các domain ra Multi-sheet Excel, nếu dữ liệu quá lớn có thể gây áp lực bộ nhớ.
  * *Đề xuất*: Bắt buộc dùng `SXSSFWorkbook` với sliding window 100 rows và streaming database cursor.

---

## 9. Open Questions

- Không còn câu hỏi mở. Toàn bộ các thắc mắc về JSON Export, Multi-sheet Excel, và Polyglot Database đã được chốt và đồng thuận ở giai đoạn Brainstorm.

---

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- Configuration Management & Governance (Menu, Parameters, i18n, Tenant Configs, Audit, Versioning)

### 10.2 Flow Type
- Command (write-heavy: snapshot, rollback, import, activate milestone) + Query (export, diff, history)

### 10.3 Candidate Services
- `services/system-admin-service`: Service chính đảm nhiệm quản trị cấu hình, versioning framework và API.
- `components/base-core`:
  * `base-file-starter`: Bổ sung `SimpleJsonExportStrategy`, `RelationalJsonExportStrategy`, `MultiSheetExcelExportStrategy`.
  * `base-modulith-starter`: Tận dụng Event Publication Registry.
  * `base-data-starter`: Tận dụng `SnowflakePersistentAuditableEntity`.

### Detection Evidence
- Module: `com.ntt.sysadmin.tenant` → File: `DomainConfigService.kt`
- Module: `com.ntt.sysadmin.menu` → File: `MenuEntities.kt`, `MenuController.kt`
- Module: `com.ntt.sysadminservice.shared` → File: `SysAdminErrorCode.kt`, `TreeEntity.kt`
- Module: `com.ntt.basecore.autoconfigure.file` → File: `ExportService.kt`, `ExcelExportStrategy.kt`

### 10.4 External Integrations
- PostgreSQL (Database chính cho Nghiệp vụ + Transactional Outbox + JSONB Snapshot)
- MongoDB (Pluggable SPI cho Document Snapshot/Outbox khi mở rộng)
- Redis (Bộ nhớ đệm cấu hình và quyền hạn)

### 10.5 Required Modules
- `shared/versioning`: `VersionedConfigDomain<E>`, `ConfigChangeTracker`, `ConfigSnapshotManager`
- `config`: `SystemConfigEntity`, `SystemConfigService`, `SystemConfigController`
- `i18n`: `I18nMessageEntity`, `I18nMessageService`, `I18nMessageController`
- `menu`: Mở rộng versioning cho `MenuEntities`
- `tenant`: Mở rộng versioning cho `DomainConfigEntity`

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Admin | Gửi request cập nhật cấu hình (Menu / Config / i18n) | Web Controller xác thực JWT và role |
| 2 | System | Thực hiện kiểm tra tính hợp lệ và ghi DB chính | PostgreSQL trong Local Transaction |
| 3 | System | Ghi nhận sự kiện `ConfigDomainChangedEvent` vào outbox | Bảng `EVENT_PUBLICATION` trong cùng transaction |
| 4 | System | Commit transaction chính thành công, trả về HTTP 200 cho Admin | Client nhận kết quả ngay tức thì |
| 5 | Background | `@ApplicationModuleListener` nhận event sau commit | Bắt đầu xử lý bất đồng bộ |
| 6 | Background | Đẩy event vào `BatchAuditCollector` buffer | In-memory RingBuffer / LinkedQueue |
| 7 | Background | Đạt ngưỡng 100 events hoặc 500ms → Flush batch | Bulk insert vào `sys_config_snapshot` |
| 8 | Background | Đánh dấu hoàn thành event trong `EVENT_PUBLICATION` | Cập nhật `completion_date = NOW()` |

---

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | Section 2.1 | Spec 3.1 | `SystemConfigService`, `MenuPermissionService`, `I18nService` | Pending |
| FR-002 | Section 2.2 | Spec 3.2 | `ExportService`, `ExcelExportStrategy`, `CsvExportStrategy` | Reused |
| FR-003 | Section 2.2 | Spec 3.2 | `RelationalJsonExportStrategy`, `RelationalExportTemplate` | New in Base |
| FR-004 | Section 2.2 | Spec 3.2 | `MultiSheetExcelExportStrategy`, `MultiSheetExportTemplate` | New in Base |
| FR-005 | Section 2.3 | Spec 3.3 | `RelationalImportCoordinator`, `TableImportHandler` | New in Base |
| FR-006 | Section 2.3 | Spec 3.3 | `DefaultSimpleImportHandler`, `TableImportHandler` | New in Base |
| FR-007 | Section 2.3 | Spec 3.3 | `TableImportHandler` (`TRUNCATE_AND_LOAD`) | New in Base |
| FR-008 | Section 2.4 | Spec 3.4 | `ConfigChangeTracker`, `DomainConfigHistoryEntity` | New in Service |
| FR-009 | Section 2.5 | Spec 3.5 | `ConfigSnapshotManager`, `MilestoneEntity` | New in Service |
| FR-010 | Section 2.5 | Spec 3.5 | `ConfigSnapshotManager`, `DiffViewerService` | New in Service |
| FR-011 | Section 2.6 | Spec 3.6 | `ConfigDomainEventListener`, `@ApplicationModuleListener` | New in Service |
| FR-012 | Section 2.6 | Spec 3.6 | `EventPublicationRepository`, `spring-modulith-starter-jpa` | Reused |
| FR-013 | Section 2.6 | Spec 3.6 | `BatchAuditCollector`, `SmartLifecycle` | New in Service |
| FR-014 | Section 2.5 | Spec 3.5 | `ConfigSnapshotManager` (Conflict Detector) | New in Service |

---

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Việc trừu tượng hóa `VersionedConfigDomain<E>` giúp loại bỏ hoàn toàn mã lặp (boilerplate code) giữa các miền cấu hình khác nhau. Mỗi khi thêm cấu hình mới trong tương lai (ví dụ cấu hình thanh toán, cấu hình đối tác, cấu hình bảo mật), lập trình viên chỉ mất khoảng 30 phút để implement interface mà không phải viết lại engine snapshot hay rollback.

### Related Features / Precedents
- `DomainConfigService` trong `com.ntt.sysadmin.tenant` đã có cơ chế lưu snapshot thô sơ cho riêng một bảng `domain_configs`. Kiến trúc mới sẽ chuẩn hóa và thay thế cơ chế này bằng framework tổng quát.

### Integration Notes
- PostgreSQL là primary storage cho cả Outbox và Audit JSONB. MongoDB là optional extension. Cả hai cùng tuân thủ SPI `ConfigAuditStorageProvider`.
- Tận dụng triệt để `base-file-starter` và `base-modulith-starter`.

### Suggested Approach
- **Phase 1**: Xây dựng extensions trong `base-file-starter` (`SimpleJsonExportStrategy`, `MultiSheetExcelExportStrategy`, `RelationalExportTemplate`).
- **Phase 2**: Xây dựng core framework trong `system-admin-service/shared/versioning/` (`VersionedConfigDomain`, `BatchAuditCollector`, `ConfigAuditStorageProvider`).
- **Phase 3**: Áp dụng cho các domain cấu hình ưu tiên: `system_configs` → `i18n_messages` → `menu_items` → `domain_configs`.
- **Phase 4**: Viết Controller, DTOs và tích hợp giao diện quản trị.
