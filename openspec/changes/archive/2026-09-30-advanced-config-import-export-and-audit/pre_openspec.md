# Pre-OpenSpec: advanced-config-import-export-and-audit

> **Type**: EXTEND
> **Flow**: Hybrid (Streaming Query + Transactional Command + Outbox Event)
> **Source**: URD (`openspec/research/advanced-config-import-export-and-audit/business_analysis.md`)
> **Classification Evidence**: `ExportStrategy` → `base-file-starter` → `components/base-core/src/main/kotlin/com/ntt/basecore/domain/file/ExportStrategy.kt` & `ConfigManagementController` → `system-admin-service` → `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/ConfigManagementController.kt`
> **Archive**: N/A
> **Quality Score**: 99/100

## 📋 Feature Summary

Xây dựng nền tảng nâng cao cho việc Xuất/Nhập dữ liệu cấu hình đồ thị quan hệ phức tạp có khóa ngoại liên kết (Relational Graph Export/Import) và Cơ chế Kiểm toán phiên bản phi đồng bộ (Non-blocking Resilient Audit Trail) với khả năng mở rộng lưu trữ đa cơ sở dữ liệu.
Tính năng bao gồm:
1. Xuất JSON phân tầng có mã kiểm tra toàn vẹn SHA-256 (`RelationalJsonExportStrategy`) và xuất JSON phẳng streaming (`SimpleJsonExportStrategy`).
2. Xuất toàn bộ miền cấu hình vào 1 tệp Excel nhiều Sheet (`MultiSheetExcelExportStrategy`) với cơ chế **Dynamic JPA Metamodel** tự động trích xuất schema thực thể, không cần định nghĩa cột thủ công, kiểm soát O(1) RAM qua SXSSFWorkbook sliding window và chống lỗ hổng Formula Injection (CWE-1236).
3. Nạp dữ liệu cấu hình đa bảng an toàn qua 4 chế độ (`TRUNCATE_AND_LOAD`, `DELETE_AND_INSERT`, `UPSERT_MERGE`, `PATCH_VALUES`) kết hợp thuật toán sắp xếp Topo (Kahn's DAG Sorter) và cơ chế Two-Pass Import giải quyết triệt để ràng buộc khóa ngoại tự tham chiếu (Self-referencing FK).
4. Tự động ghi vết cấu hình qua Spring Modulith Transactional Outbox không làm chậm luồng CRUD chính, gom vi mẻ trong RAM qua `BatchAuditCollector` (`SmartLifecycle` Phase 10,000) đảm bảo không mất dữ liệu (Zero-loss), và hỗ trợ kho lưu trữ kép linh hoạt (PostgreSQL JSONB / MongoDB Document).

| Metric | Giá trị |
|--------|---------|
| Số FR | 15 (URD: 12, Enriched: 3) |
| Issues | 1 (🔴: 0, 🟡: 1, 🟢: 0) |
| Open Questions | 0 |
| **Quality Score** | **99/100** |

---

## 1. Actors

- **System Administrator (Quản trị viên hệ thống)**: Người dùng chính thực hiện xuất dữ liệu sao lưu, nạp cấu hình di chuyển giữa các môi trường, đóng gói mốc cấu hình (Milestone), so sánh khác biệt (Diff) và hoàn tác (Rollback).
- **DevOps Engineer (Kỹ sư vận hành)**: Thực hiện tự động hóa sao lưu và đồng bộ cấu hình giữa các môi trường STAGING và PRODUCTION qua REST API hoặc lệnh CLI.
- **Security Auditor (Kiểm toán viên an ninh)**: Tra cứu lịch sử thay đổi cấu hình, xác thực mã băm toàn vẹn SHA-256, kiểm tra tính tuân thủ quy trình vận hành.
- **System Worker (Tiến trình hệ thống ngầm)**: Tự động gom mẻ sự kiện kiểm toán trong RAM và xả định kỳ xuống cơ sở dữ liệu (Micro-batch Collector).

---

## 2. Functional Requirements

### FR-001: Xuất JSON phẳng dạng streaming [URD]
- **Actor**: System Administrator, DevOps Engineer
- **Action**: Hệ thống phải tuần tự hóa và truyền tải luồng dữ liệu (streaming) của các thực thể cấu hình phẳng trực tiếp ra `OutputStream` của HTTP response dưới dạng mảng JSON (`[ ... ]`) mà không tải toàn bộ danh sách bản ghi vào bộ nhớ RAM.
- **Validation**:
  - Phải sử dụng Jackson `JsonGenerator`.
  - Phải đặt `Content-Type: application/json` và HTTP Header `Content-Disposition: attachment; filename="<domain>_export.json"`.

### FR-002: Xuất đồ thị quan hệ JSON có mã băm SHA-256 [URD]
- **Actor**: System Administrator, DevOps Engineer
- **Action**: Hệ thống phải đóng gói toàn bộ cây phân cấp hoặc đồ thị liên kết khóa ngoại của miền cấu hình (như Menu cha - con, Quyền hạn, Nút bấm) vào đối tượng `RelationalExportPayload`, tự động tính toán mã băm SHA-256 trên mảng byte JSON và đính kèm metadata (`schemaVersion = "1.0"`, `exportedAt`, `domainName`).
- **Validation**:
  - Mã SHA-256 tính toán phải khớp chính xác với nội dung trường `data`.
  - Tệp xuất ra phải định dạng UTF-8 chuẩn.

### FR-003: Xuất Excel nhiều Sheet tự động qua Dynamic JPA Metamodel [URD]
- **Actor**: System Administrator, DevOps Engineer
- **Action**: Hệ thống phải tự động quét siêu dữ liệu thực thể JPA (`Metamodel`) để tự động tạo danh sách sheet và tiêu đề cột cho từng bảng cấu hình mà không yêu cầu lập trình viên khai báo `ColumnDefinition` thủ công; ghi dữ liệu ra tệp `.xlsx` duy nhất chứa nhiều sheet.
- **Validation**:
  - Bộ nhớ RAM phải duy trì cố định $O(1)$ thông qua Apache POI `SXSSFWorkbook(100)`.
  - Phải sử dụng Style Pool tập trung dùng chung cho toàn bộ workbook để không vượt quá ngưỡng 64,000 styles của Excel.
  - Phải gọi `workbook.dispose()` trong khối `finally` để dọn sạch file tạm trên đĩa.

### FR-004: Thuật toán sắp xếp thứ tự phụ thuộc khóa ngoại [URD]
- **Actor**: System Worker (Import Subsystem)
- **Action**: Hệ thống phải sử dụng thuật toán sắp xếp Topo (Kahn's DAG Sorter) để tự động phân tích ma trận phụ thuộc khóa ngoại giữa các bảng cần nạp, xác định thứ tự chèn bảng cha trước bảng con và thứ tự xóa bảng con trước bảng cha.
- **Validation**:
  - Tự động phát hiện và cảnh báo ngoại lệ nếu tồn tại quan hệ phụ thuộc vòng (Circular Dependency).

### FR-005: Bốn chế độ nạp cấu hình linh hoạt [URD]
- **Actor**: System Administrator, DevOps Engineer
- **Action**: Hệ thống phải hỗ trợ nạp tệp cấu hình theo 4 chế độ: `TRUNCATE_AND_LOAD` (xóa trắng bảng và nạp nhanh), `DELETE_AND_INSERT` (xóa theo phạm vi domain và nạp lại để giữ nguyên sequence), `UPSERT_MERGE` (khớp theo Natural Business Key, cập nhật nếu có, chèn nếu chưa), và `PATCH_VALUES` (chỉ cập nhật các trường non-null).
- **Validation**:
  - Toàn bộ quá trình nạp trên nhiều bảng phải được bọc trong một giao dịch cơ sở dữ liệu `@Transactional` nguyên tố (ACID). Lỗi tại bất kỳ bảng nào phải tự động rollback toàn bộ.

### FR-006: Khung thực thi mặc định giảm mã thừa [URD]
- **Actor**: Developer / System
- **Action**: Hệ thống phải cung cấp lớp thực thi mẫu `DefaultSimpleImportHandler<T, ID>` kế thừa Spring Data JPA `JpaRepository` để xử lý tự động cho các bảng cấu hình phẳng mà không cần viết thêm mã DAO/Service.
- **Validation**:
  - Hỗ trợ đầy đủ các chế độ nạp qua `deleteAllInBatch()` và `saveAll()`.

### FR-007: Ghi nhận sự kiện cấu hình qua Transactional Outbox [URD]
- **Actor**: System Worker
- **Action**: Khi có bất kỳ thay đổi nào trên cấu hình (Create, Update, Delete, Rollback), hệ thống phải tự động phát `ConfigDomainChangedEvent` và ghi nhận vào bảng Outbox `event_publication` của PostgreSQL trong cùng kết nối cơ sở dữ liệu với giao dịch chính.
- **Validation**:
  - Request API của người dùng phải được phản hồi thành công ngay sau khi commit mà không bị chặn bởi việc xử lý lịch sử.

### FR-008: Bộ đệm vi mẻ RAM chống thất thoát dữ liệu [URD]
- **Actor**: System Worker
- **Action**: Hệ thống phải tiếp nhận sự kiện sau commit từ `@ApplicationModuleListener`, đưa vào hàng đợi `LinkedBlockingQueue` (sức chứa 20,000 sự kiện), và gom vi mẻ (ngưỡng 100 sự kiện hoặc sau 500ms) để thực thi ghi hàng loạt xuống cơ sở dữ liệu.
- **Validation**:
  - Cài đặt `SmartLifecycle` với `phase = 10,000` để bảo đảm xả sạch toàn bộ hàng đợi RAM trước khi Spring đóng kết nối DataSource khi tắt ứng dụng.

### FR-009: Đóng gói mốc cấu hình đa miền Milestone [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cho phép tạo một mốc phát hành (`Milestone`) đóng gói ảnh chụp trạng thái JSONB (`Snapshot`) của một hoặc nhiều miền cấu hình tại cùng một thời điểm, có tên gợi nhớ và mã định danh UUID.
- **Validation**:
  - Mỗi snapshot trong mốc phải có mã băm SHA-256 và số lượng bản ghi tương ứng.

### FR-010: So sánh khác biệt chi tiết từng trường Config Diff [URD]
- **Actor**: System Administrator, Auditor
- **Action**: Hệ thống phải cho phép so sánh trạng thái đang chạy thực tế của một miền cấu hình với một bản snapshot trong quá khứ, trả về danh sách chi tiết các thực thể: Đã thêm mới (Added), Đã xóa (Removed), và Đã sửa đổi (Modified kèm giá trị cũ và mới của từng trường).
- **Validation**:
  - So sánh dựa trên Natural Business Key của thực thể.

### FR-011: Hoàn tác cấu hình an toàn kèm kiểm tra xung đột [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải hỗ trợ hoàn tác dữ liệu cấu hình về trạng thái của một snapshot đã chọn. Nếu dữ liệu hiện tại có sự thay đổi xung đột, hệ thống phải yêu cầu xác nhận cờ `forceOverwrite = true` trước khi ghi đè.
- **Validation**:
  - Quá trình rollback phải phát sự kiện kiểm toán với hành động `ROLLBACK`.

### FR-012: Điều hướng lưu trữ kiểm toán sang Database phụ MongoDB [URD]
- **Actor**: Security Auditor, System Worker
- **Action**: Hệ thống phải cung cấp giao diện `ConfigAuditStorageProvider` SPI cho phép định tuyến việc lưu trữ dữ liệu lịch sử và snapshot sang cơ sở dữ liệu MongoDB thông qua cấu hình `app.config.audit.storage-type: mongodb` mà không làm thay đổi mã nghiệp vụ.
- **Validation**:
  - Khi không cấu hình MongoDB, hệ thống mặc định lưu trên PostgreSQL JSONB có chỉ mục GIN.

### FR-013: Vô hiệu hóa mã độc Formula Injection theo chuẩn CWE-1236 [ENRICHED]
- **Actor**: System Worker (Export Subsystem)
- **Action**: Khi xuất tệp Excel hoặc CSV, hệ thống phải tự động kiểm tra ký tự đầu tiên của mọi ô dữ liệu chuỗi; nếu bắt đầu bằng `=`, `+`, `-`, `@`, `\t`, `\r`, phải tự động thêm dấu nháy đơn `'` ở đầu để vô hiệu hóa việc thực thi lệnh độc hại khi mở bằng phần mềm bảng tính.
- **Validation**:
  - Tích hợp lớp `ExportSanitizer` xử lý trước khi ghi ô Excel.

### FR-014: Tự động phục hồi sự kiện Outbox dở dang khi khởi động lại [ENRICHED]
- **Actor**: System Worker (Spring Modulith Engine)
- **Action**: Khi ứng dụng khởi động lại sau sự cố mất điện hoặc crash đột ngột (`kill -9`), hệ thống phải tự động quét các sự kiện có trạng thái `UNCOMPLETED` trong bảng `event_publication` và tái phân phối lại cho các listener xử lý tiếp.
- **Validation**:
  - Kích hoạt thông qua thuộc tính `spring.modulith.republish-outstanding-events-on-restart=true`.

### FR-015: Hoãn kiểm tra ràng buộc khóa ngoại (Deferred FK Constraints) trong phiên nạp [ENRICHED]
- **Actor**: System Worker (Import Subsystem)
- **Action**: Trong suốt quá trình thực thi nạp dữ liệu đa bảng hoặc cây tự tham chiếu, hệ thống phải thiết lập `SET CONSTRAINTS ALL DEFERRED` trong phiên giao dịch PostgreSQL cục bộ và thực hiện Two-Pass import để tránh vi phạm khóa ngoại tức thời giữa các bản ghi lồng nhau.
- **Validation**:
  - Đảm bảo kiểm tra toàn vẹn dữ liệu được thực thi đầy đủ khi kết thúc transaction commit.

---

## 3. Non-functional Requirements

- **NFR-001 (Performance Impact)**: Thao tác CRUD cấu hình chính của người dùng không bị tăng thời gian phản hồi quá 5ms do việc ghi nhận kiểm toán Outbox.
- **NFR-002 (Memory Safety)**: Quá trình xuất tệp Excel đa sheet hoặc tệp JSON lớn (> 100,000 dòng) duy trì mức tiêu thụ bộ nhớ RAM cố định $O(1)$, không làm tăng heap JVM quá 128MB.
- **NFR-003 (Integrity & Non-repudiation)**: 100% tệp sao lưu JSON có mã băm SHA-256 để phát hiện ngay lập tức bất kỳ sự chỉnh sửa trái phép nào.
- **NFR-004 (Durability / Zero-loss)**: Đảm bảo 100% không mất mát sự kiện kiểm toán cấu hình ngay cả khi máy chủ bị tắt đột ngột nhờ bảng Outbox bền vững.
- **NFR-005 (Transaction Atomicity)**: Quá trình nạp dữ liệu đa bảng tuân thủ nguyên tắc All-or-Nothing — lỗi tại một bảng sẽ tự động hoàn tác toàn bộ các bảng khác trong phiên nạp.
- **NFR-006 (Extensibility)**: Cho phép bổ sung domain cấu hình mới hoặc thay đổi kho lưu trữ (PostgreSQL / MongoDB) mà không phải sửa đổi mã nguồn các domain hiện hữu (Tuân thủ OCP).

---

## 4. Deduplicated & Consolidated

Không phát hiện yêu cầu nghiệp vụ trùng lặp. Toàn bộ các yêu cầu từ tài liệu phân tích nghiệp vụ (`business_analysis.md`) đã được tổng hợp, chuẩn hóa và đối chiếu nhất quán với kết quả thảo luận kiến trúc trong `brainstorm_notes.md`.

---

## 5. Enriched Domain Requirements

Đã bổ sung 3 yêu cầu làm giàu nghiệp vụ (Enriched FRs) tuân thủ giới hạn $\min(5, \lceil 12 \times 0.20 \rceil) = 3$:
1. **FR-013**: Vô hiệu hóa Formula Injection (CWE-1236) bảo vệ người dùng cuối khi mở file Excel backup.
2. **FR-014**: Cơ chế phục hồi sự kiện Outbox dở dang khi khởi động lại ứng dụng đảm bảo tính bền vững Zero-loss.
3. **FR-015**: Hoãn kiểm tra ràng buộc khóa ngoại (Deferred FK Constraints) và nạp 2 lượt (Two-Pass Import) xử lý an toàn cho cây danh mục tự tham chiếu.

### External Integrations

| Hệ thống | Mục đích | Ghi chú |
|----------|----------|---------|
| **PostgreSQL Database** | Lưu trữ bảng nghiệp vụ, bảng Outbox `event_publication`, và bảng lịch sử `domain_config_history` | Sử dụng Spring Data JPA và `JdbcTemplate` |
| **MongoDB Database** | Kho lưu trữ thứ cấp cho các sự kiện kiểm toán khối lượng lớn và snapshot mốc cấu hình | Tùy chọn, kết nối qua `spring-data-mongodb` |
| **Redis Cache** | Lưu bộ đệm cấu hình ứng dụng | Tự động xóa cache (Invalidation) khi áp dụng mốc hoặc Rollback |

---

## 6. Assumptions

- ⚠️ **AS-001**: Cơ sở dữ liệu PostgreSQL cho phép sử dụng tính năng `DEFERRED CONSTRAINTS` trên các khóa ngoại của bảng cấu hình.
- ⚠️ **AS-002**: Khi cấu hình `app.config.audit.storage-type: mongodb`, cụm MongoDB đã được khởi tạo sẵn sàng và có thể kết nối thông qua connection string tiêu chuẩn của Spring Boot.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|:----:|-----------|
| Rõ ràng (Clarity) | 25/25 | Không có điểm trừ |
| Đầy đủ (Completeness) | 24/25 | FR-012: Trừ 1 điểm do cần bổ sung cấu hình chi tiết thông số kết nối MongoDB trong file môi trường |
| Nhất quán (Consistency) | 25/25 | Không có điểm trừ |
| Kiểm thử được (Testability) | 25/25 | Không có điểm trừ |
| **Tổng điểm** | **99/100** | **Xếp loại: XUẤT SẮC** |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|:--------:|----|-------------------|----------------|
| 1 | Đầy đủ | -1 | FR-012 | "Điều hướng lưu trữ sang MongoDB" cần làm rõ cấu hình fallback nếu MongoDB tạm thời mất kết nối | Thiết kế cơ chế cảnh báo hoặc tự động đệm lại vào Outbox khi ghi MongoDB gặp lỗi |

---

## 8. Issues & Risks

- 🟡 **WARN-001 (Circular Dependency Risk)** — FR-004: Nếu dữ liệu import chứa quan hệ phụ thuộc vòng giữa các bảng khác nhau mà không thể hoãn kiểm tra khóa ngoại, phiên import sẽ bị dừng lại. *Đề xuất*: Bắt buộc người dùng chuẩn hóa dữ liệu hoặc sử dụng chế độ `TRUNCATE_AND_LOAD`.

---

## 9. Open Questions

1. [RESOLVED] Cấu trúc package trong `base-file-starter`: Đặt tại `com.ntt.basecore.autoconfigure.file.export` và `com.ntt.basecore.autoconfigure.file.import` — giữ cấu trúc phẳng, không tạo sub-package `relational`.
2. [RESOLVED] Cơ chế bảo mật quyền nạp cấu hình: SHA-256 checksum là đủ — không cần chữ ký số RSA.

---

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Quản trị Cấu hình Hệ thống, Xuất/Nhập Dữ liệu Đa Bảng và Kiểm toán Phiên bản Bất biến (System Configuration Management, Relational File Portability & Audit Versioning).

### 10.2 Flow Type
Hybrid (Streaming Query cho Export, Transactional Command cho Import & Rollback, Outbox Event cho Audit Trail).

### 10.3 Candidate Services
- `components/base-core` (`starters/base-file-starter`, `starters/base-audit-starter`): Cung cấp các abstraction, engine xuất Excel đa sheet, thuật toán Kahn, nạp quan hệ và SPI lưu trữ.
- `services/system-admin-service`: Triển khai các domain cấu hình cụ thể, bộ điều phối Outbox, bộ đệm vi mẻ RAM và controller REST API.

### Detection Evidence
- Keyword: `ExportStrategy` → Module: `base-file-starter` → File: `components/base-core/src/main/kotlin/com/ntt/basecore/domain/file/ExportStrategy.kt`
- Keyword: `ExcelExportStrategy` → Module: `base-file-starter` → File: `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/ExcelExportStrategy.kt`
- Keyword: `ConfigManagementController` → Module: `system-admin-service` → File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/adapter/in/web/ConfigManagementController.kt`
- Keyword: `BatchAuditCollector` → Module: `system-admin-service` → File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/buffer/BatchAuditCollector.kt`

### 10.4 External Integrations
- PostgreSQL (RDBMS chính lưu trữ thực thể cấu hình và bảng Outbox `event_publication`).
- MongoDB (Kho lưu trữ tài liệu phụ trợ cho audit logs và snapshots).
- Redis (Bộ nhớ đệm phục vụ invalidation khi cấu hình thay đổi).

### 10.5 Required Modules
- `com.ntt:base-file-starter`
- `com.ntt:base-data-starter`
- `com.ntt:base-web-starter`
- `org.springframework.modulith:spring-modulith-starter-jpa`
- `org.apache.poi:poi-ooxml:5.3.0`
- `com.fasterxml.jackson.module:jackson-module-kotlin`

---

## 11. Transaction Flow Detail

| Bước | Tác nhân | Hành động | Hệ thống xử lý |
|------|----------|-----------|----------------|
| 1 | Quản trị viên | Gửi yêu cầu nạp file cấu hình qua API `POST /api/v1/configs/import` | Controller tiếp nhận file multipart, kiểm tra định dạng và tính toàn vẹn SHA-256 |
| 2 | Hệ thống | Sắp xếp thứ tự bảng phụ thuộc | `TopologicalDependencySorter` tính toán DAG; nếu `DELETE_AND_INSERT`, sắp xếp thứ tự bảng con xóa trước bảng cha; thứ tự chèn bảng cha trước bảng con |
| 3 | Hệ thống | Khởi tạo giao dịch ACID | Bật `SET CONSTRAINTS ALL DEFERRED` trong phiên giao dịch PostgreSQL |
| 4 | Hệ thống | Thực thi nạp dữ liệu | Lần lượt gọi `TableImportHandler.process()` cho từng bảng theo đúng thứ tự Topo đã sắp xếp |
| 5 | Hệ thống | Phát sự kiện kiểm toán | Ghi nhận sự kiện thay đổi vào bảng Outbox `event_publication` trong cùng giao dịch |
| 6 | Hệ thống | Commit giao dịch | Commit dữ liệu vào database chính, xóa cache Redis liên quan, trả về kết quả thành công cho client |
| 7 | Hệ thống ngầm | Gom vi mẻ kiểm toán | `@ApplicationModuleListener` nhận sự kiện phi đồng bộ, đưa vào `BatchAuditCollector`, gom 100 sự kiện hoặc 500ms xả xuống `ConfigAuditStorageProvider` |

---

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| **FR-001** | BA §4 (UC-001) | TechSpec §4.1 | `SimpleJsonExportStrategy` | Mapped |
| **FR-002** | BA §4 (UC-001) | TechSpec §4.1 | `RelationalJsonExportStrategy` | Mapped |
| **FR-003** | BA §4 (UC-002) | TechSpec §4.2 | `MultiSheetExcelExportStrategy`, `DynamicJpaMetamodelSheetExtractor` | Mapped |
| **FR-004** | BA §4 (UC-003) | TechSpec §4.3 | `TopologicalDependencySorter` | Mapped |
| **FR-005** | BA §4 (UC-003) | TechSpec §4.3 | `RelationalImportCoordinator`, `TableImportHandler` | Mapped |
| **FR-006** | BA §4 (UC-003) | TechSpec §4.3 | `DefaultSimpleImportHandler` | Mapped |
| **FR-007** | BA §4 (UC-004) | TechSpec §4.4 | Spring Modulith Outbox, `ConfigDomainChangedEvent` | Mapped |
| **FR-008** | BA §4 (UC-004) | TechSpec §4.4 | `BatchAuditCollector` | Mapped |
| **FR-009** | BA §4 (UC-005) | TechSpec §2.2 | `ConfigSnapshotManager`, `ConfigMilestoneEntity` | Mapped |
| **FR-010** | BA §4 (UC-005) | TechSpec §5 | `ConfigSnapshotManager` | Mapped |
| **FR-011** | BA §4 (UC-005) | TechSpec §5 | `ConfigSnapshotManager` | Mapped |
| **FR-012** | BA §4 (UC-006) | TechSpec §4.5 | `ConfigAuditStorageProvider`, `MongoAuditStorageProvider` | Mapped |
| **FR-013** | BA §4.5 (BR-004) | TechSpec §4.2 | `ExportSanitizer` | Mapped |
| **FR-014** | BA §4.2 (BR-009) | TechSpec §3.3 | Spring Modulith Auto-republish | Mapped |
| **FR-015** | BA §4.3 (BR-006) | TechSpec §4.3 | `TwoPassTreeImportHandler` | Mapped |

---

## 13. Agent Notes (Tổng hợp Bổ sung)

### Observations
- Đây là một giải pháp kiến trúc mang tính nền tảng (Platform Architecture) có sức ảnh hưởng toàn diện đến khả năng di chuyển cấu hình và tính sẵn sàng của hệ sinh thái vi dịch vụ.
- Việc kết hợp giữa Dynamic JPA Metamodel và Apache POI SXSSFWorkbook sliding window giải quyết triệt để 2 vấn đề kinh điển của Java: mã lặp khai báo cột và lỗi cạn kiệt bộ nhớ heap OOM khi xuất dữ liệu lớn.

### Related Features / Precedents
- `DomainConfigService`: Hiện tại đã có cơ chế lưu lịch sử cơ bản trên bảng `domain_config_history`, sẽ được nâng cấp lên mô hình Outbox phi đồng bộ hoàn toàn.
- `base-file-starter`: Đã có `ExcelExportStrategy` (1 sheet) và `CsvExportStrategy`. Các thành phần mới sẽ mở rộng tự nhiên mà không phá vỡ bất kỳ dòng mã nào đang hoạt động.

### Integration Notes
- PostgreSQL 16 hỗ trợ `jsonb` và chỉ mục GIN, đáp ứng hoàn hảo cho việc lưu trữ snapshot và tìm kiếm linh hoạt ở chế độ mặc định.
- MongoDB được tích hợp qua Spring Data MongoDB theo chuẩn `ConfigAuditStorageProvider` SPI, bảo đảm tính cắm rút 100%.

### Suggested Approach
- Thực hiện tuần tự 2 Phase đã thống nhất trong `brainstorm_notes.md`:
  - **Phase 1**: Xây dựng toàn bộ các abstract base classes, SPI và động cơ xuất/nhập trong `components/base-core/starters/base-file-starter`.
  - **Phase 2**: Tích hợp các domain cấu hình cụ thể và bộ điều hướng Dual-Storage trong `services/system-admin-service`.
