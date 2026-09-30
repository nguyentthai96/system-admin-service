---
type: research_brief
name: advanced-config-import-export-and-audit
version: "1.0"
language: vi
date: 2026-09-30
status: in_progress
---

# Research Brief: Nền tảng Export/Import & Audit Versioning Nâng cao (Advanced Config Import/Export & Audit Framework)

> Tài liệu khởi đầu cho quá trình research kiến trúc và thiết kế kỹ thuật chuyên sâu giải quyết 3 bài toán kiến trúc trọng yếu:
> 1. `JsonExportStrategy` & Cơ chế Import phức tạp trên quan hệ bảng có khóa ngoại (Base-core vs System-admin-service).
> 2. Multi-sheet Excel Export đa bảng / đa miền cấu hình (Mở rộng `ExcelExportStrategy` vs Tách biệt Strategy).
> 3. `VersionedConfigDomain` Auto-tracking phi đồng bộ, bộ đệm vi mẻ (micro-batching), chống mất mát dữ liệu (zero-loss durability) và khả năng mở rộng lưu trữ đa cơ sở dữ liệu (PostgreSQL + MongoDB).

---

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Nền tảng Export/Import & Audit Versioning Nâng cao (Advanced Config Import/Export & Audit Framework) |
| **Ngày tạo** | 2026-09-30 |
| **Input source** | MODE_IDEA / ARCHITECTURE_DEBATE (User Request với 3 chủ đề thiết kế kiến trúc mở) |
| **Input content** | Phân tích chuyên sâu 3 open points: (1) `JsonExportStrategy` đưa vào base-core hay custom, xử lý graph quan hệ bảng phức tạp, import đa cơ chế; (2) Multi-sheet Excel export gộp hay tách `ExcelExportStrategy`; (3) Auto-tracking versioning bằng Event Listener/AOP + Batch buffer ngầm + Hỗ trợ lưu trữ secondary database (MongoDB). |
| **Người yêu cầu** | Architect / Tech Lead (@nguyentthai96) |

---

## 2. Mô tả bài toán & Bối cảnh kỹ thuật

### 2.1 Bối cảnh (Context)
Trong hệ thống quản trị vi dịch vụ doanh nghiệp (Microservices Architecture), cấu hình hệ thống không chỉ là các bản ghi đơn lẻ (`key=value`) mà bao gồm các cấu trúc dữ liệu đồ thị phức tạp (Relational Graphs & Hierarchical Trees) như:
- **Menu Management**: Cây danh mục cha - con đa tầng, liên kết với màn hình UI, nút bấm hành động (Action Buttons), và quyền hạn API (API Endpoints/Permissions).
- **Organization & Department**: Cây tổ chức phòng ban, chức vụ, ma trận chức danh và liên kết nhân sự.
- **Dynamic Multilingual Messages (i18n)**: Message key, nhóm thông điệp, bản địa hóa song ngữ/đa ngữ.
- **Common & Domain Configuration**: Cấu hình tham số nghiệp vụ, Feature Flags, Metadata.

Hiện tại:
1. Module `base-file-starter` trong `base-core` mới chỉ hỗ trợ xuất/nhập tệp tin phẳng đơn giản (`CsvExportStrategy`, `ExcelExportStrategy` cho 1 sheet duy nhất, `ImportService` chỉ đọc CSV phẳng). Thiếu vắng hoàn toàn khả năng xử lý đồ thị liên kết có khóa ngoại (Foreign Keys), thiếu kiểm soát thứ tự phụ thuộc (Topological Dependency Sorting), và không có cơ chế xuất JSON phân tầng có mã băm bảo toàn dữ liệu (SHA-256 Checksum).
2. Việc xuất báo cáo hoặc backup toàn bộ cấu hình hệ thống ra một tệp Excel nhiều Sheet (Multi-sheet Excel) hiện phải thực hiện thủ công, chưa có abstraction tiêu chuẩn khiến các dịch vụ viết mã lặp lại, dễ cạn kiệt bộ nhớ JVM (OOM do Apache POI DOM) và style leak.
3. Cơ chế theo dõi lịch sử cấu hình (Audit Trail & Versioning) nếu thực hiện thủ công hoặc đồng bộ trong luồng CRUD chính sẽ làm suy giảm nghiêm trọng độ trễ API (latency), tạo điểm thắt cổ chai cơ sở dữ liệu (I/O bottleneck), và có nguy cơ thất thoát dữ liệu lịch sử khi có lượng truy cập đột biến hoặc máy chủ gặp sự cố (server crash). Hơn nữa, việc phình to bảng lịch sử trong RDBMS PostgreSQL chính gây áp lực sao lưu và dọn dẹp phân vùng.

### 2.2 Mục tiêu (Objectives)
- [ ] **Objective 1 (Abstract Base-Core Architecture)**: Thiết kế và bổ sung vào `base-core` (`base-file-starter`) một khung trừu tượng hỗ trợ export/import đồ thị bảng quan hệ phức tạp, giải quyết thứ tự khóa ngoại tự động (Topological Sort), hỗ trợ 4 chế độ nạp dữ liệu (`TRUNCATE_AND_LOAD`, `DELETE_AND_INSERT`, `UPSERT_MERGE`, `PATCH_VALUES`) kèm `DefaultSimpleImportHandler` giúp các domain đơn giản giảm 90% boilerplate code.
- [ ] **Objective 2 (Scalable Multi-sheet Excel Engine)**: Đưa ra quyết định kiến trúc chuẩn xác giữa việc mở rộng `ExcelExportStrategy` hay tách riêng `MultiSheetExcelExportStrategy`. Đảm bảo O(1) Memory footprint với cơ chế Apache POI `SXSSFWorkbook` sliding window, tái sử dụng Style Pool tránh cạn kiệt heap/style limit và ngăn chặn lỗ hổng Formula Injection.
- [ ] **Objective 3 (Resilient Non-blocking Audit & Dual Storage)**: Thiết kế giải pháp auto-tracking phi đồng bộ không gây nghẽn luồng CRUD chính thông qua Spring Modulith Transactional Outbox (`@ApplicationModuleListener`), kết hợp bộ đệm vi mẻ (In-memory Micro-batch Collector) với vòng đời `SmartLifecycle` đảm bảo không thất thoát dữ liệu (Zero-loss Durability). Xây dựng Storage SPI cho phép cấu hình linh hoạt chuyển đổi hoặc ghi đồng thời sang cơ sở dữ liệu thứ cấp (MongoDB) để tối ưu lưu trữ JSONB phi cấu trúc.

### 2.3 Phạm vi ban đầu (Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Thiết kế `SimpleJsonExportStrategy<T>` và `RelationalJsonExportStrategy` cho `base-file-starter` | Triển khai mã nguồn thực tế (Code implementation được giữ cho giai đoạn sau) |
| Thiết kế khung Import đa chế độ (`TableImportHandler`, `ImportStrategyMode`, Topological Dependency Sorter) | Thay đổi giao diện người dùng frontend (React/Vite) |
| Đánh giá Trade-off kiến trúc Multi-sheet Excel (`ExcelExportStrategy` vs `MultiSheetExcelExportStrategy`) | Cấu hình hạ tầng phần cứng vật lý |
| Thiết kế cơ chế Event-driven Audit: Spring Modulith Outbox + `BatchAuditCollector` | Xây dựng Kafka Broker Cluster mới (sử dụng hạ tầng hiện có) |
| Thiết kế Storage SPI hỗ trợ Dual Database (PostgreSQL RDBMS + MongoDB NoSQL) | Thay đổi các bảng nghiệp vụ thanh toán lõi (Core banking transactions) |

---

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `JsonExportStrategy`
- `MultiSheetExcelExportStrategy`
- `VersionedConfigDomain`
- `Transactional Outbox Pattern`
- `Spring Modulith Event Publication`
- `Topological Sort Foreign Key Database Import`

### 3.2 Secondary Keywords
- `Apache POI SXSSFWorkbook sliding window memory`
- `Jackson Streaming JsonGenerator`
- `Micro-batching LinkedBlockingQueue SmartLifecycle`
- `Dual Database Audit Trail PostgreSQL MongoDB`
- `Hibernate Envers vs Custom Outbox Audit`
- `TableImportHandler ImportStrategyMode`

### 3.3 Domain-Specific Terms
- **Topological Sorting**: Thuật toán sắp xếp đồ thị có hướng không chu trình (DAG) để xác định thứ tự chèn/xóa bảng dữ liệu sao cho các bảng cha được tạo trước bảng con và xóa sau bảng con.
- **SXSSFWorkbook**: API bảng tính mở rộng của Apache POI tạo tệp Excel XLSX lớn bằng cơ chế sliding window (ghi cuốn chiếu ra đĩa) duy trì dung lượng RAM cố định O(1).
- **Transactional Outbox**: Mẫu thiết kế đảm bảo tính nhất quán tuyệt đối giữa ghi dữ liệu chính và phát tán sự kiện/audit vào bảng `event_publication` trong cùng một giao dịch DB cục bộ.
- **Micro-batching**: Gom nhóm các sự kiện riêng lẻ trong bộ nhớ đệm (VD: 100 items hoặc 500ms) để thực hiện `batchUpdate` một lần xuống DB nhằm giảm số lượng I/O roundtrips.
- **Zero-loss Durability**: Cơ chế đảm bảo không đánh mất bất kỳ bản ghi lịch sử/audit nào ngay cả khi ứng dụng gặp crash đột ngột hoặc dừng dịch vụ (Graceful Shutdown flush + Outbox replay).

### 3.4 Search Queries

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"Spring Modulith" "ApplicationModuleListener" outbox event publication registry postgresql` | Transactional Outbox & Audit | High |
| 2 | `"topological sort" foreign key database import export order java kotlin` | Relational Import/Export Ordering | High |
| 3 | `"SXSSFWorkbook" multiple sheets streaming export memory OOM "Apache POI"` | Multi-sheet Excel Best Practices | High |
| 4 | `"Spring Boot" audit trail secondary database mongodb postgresql hybrid` | Dual Storage Architecture | High |
| 5 | `"Jackson" streaming JsonGenerator large dataset memory optimization` | Streaming JSON Export | Medium |

---

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Location | Relevance | Notes |
|---------|---------------|----------|-----------|-------|
| `ExportStrategy<T>` | `base-core` | `com.ntt.basecore.domain.file` | ⭐ CRITICAL | Interface xuất tệp gốc; hiện chỉ có `CSV` và `EXCEL` |
| `ExcelExportStrategy<T>` | `base-file-starter` | `com.ntt.basecore.autoconfigure.file.export` | ⭐ CRITICAL | SXSSFWorkbook đơn sheet; cần đánh giá mở rộng hay tách |
| `ImportService` | `base-file-starter` | `com.ntt.basecore.autoconfigure.file.import` | ⭐ HIGH | Chỉ hỗ trợ CSV phẳng, chưa có JSON, chưa có FK ordering |
| `AuditAutoConfiguration` | `base-audit-starter` | `com.ntt.basecore.autoconfigure.audit` | ⭐ HIGH | Hiện chỉ bọc Hibernate Envers; cần mở rộng cho Outbox/Event |
| `ConfigManagementController` | `system-admin-service` | `com.ntt.sysadmin.versioning.adapter.in.web` | ⭐ CRITICAL | REST Controller quản lý cấu hình, export/import, milestone |
| `BatchAuditCollector` | `system-admin-service` | `com.ntt.sysadmin.versioning.buffer` | ⭐ CRITICAL | Bộ đệm micro-batching bộ nhớ RAM có SmartLifecycle |
| `ConfigAuditStorageProvider` | `system-admin-service` | `com.ntt.sysadmin.versioning.storage` | ⭐ CRITICAL | SPI lưu trữ lịch sử cấu hình (PostgreSQL JSONB) |
| `RelationalImportCoordinator` | `system-admin-service` | `com.ntt.sysadmin.shared.file.import` | ⭐ CRITICAL | Điều phối import đa chế độ; cần tổng quát hóa lên base-core |

### 4.2 Existing Code Patterns
- **Clean Architecture (Hexagonal)**: Tách biệt rõ giữa Ports & Adapters (`in/web`, `out/persistence`, `application`, `domain`).
- **Spring Modulith Module Event**: Sử dụng `@ApplicationModuleListener` để lắng nghe sự kiện sau commit giao dịch.
- **Streaming Export**: Sử dụng `SXSSFWorkbook` sliding window 100 dòng của Apache POI và Java Stream cursors.
- **Pluggable Storage SPI**: Sử dụng interface định nghĩa contract lưu trữ kèm `@ConditionalOnProperty` cho phép chuyển đổi provider.

### 4.3 Tech Stack Constraints
- **Language**: Kotlin 2.1+ / Java 21+ (Virtual Threads enabled).
- **Framework**: Spring Boot 3.4+ / Spring Framework 6.2+ / Spring Modulith 1.3+.
- **Database**: PostgreSQL 16+ (hỗ trợ kiểu dữ liệu JSONB và GIN indexing).
- **Secondary Database**: MongoDB 7.0+ (Tùy chọn cho module audit lưu trữ phi cấu trúc dung lượng lớn).
- **Build Tool**: Gradle Kotlin DSL với cấu trúc đa dự án (Multi-project / Composite build).
- **Key Dependencies**: `com.fasterxml.jackson.module:jackson-module-kotlin`, `org.apache.poi:poi-ooxml:5.3.0`, `org.springframework.modulith:spring-modulith-starter-jpa`.

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `ExportStrategy<T>` | Interface Contract | `components/base-core` | Thêm `JSON` vào enum `ExportFormat` |
| `base-file-starter` | Starter Module | `components/base-core/starters/base-file-starter` | Đích đến cho các export/import abstract components |
| `base-audit-starter` | Starter Module | `components/base-core/starters/base-audit-starter` | Đích đến cho Outbox Audit SPI & Batching Collector |
| `system-admin-service` | Business Service | `services/system-admin-service` | Consumer chính thực thi nghiệp vụ quản trị cấu hình |
| `event_publication` | DB Table | PostgreSQL System Schema | Bảng lưu vết outbox của Spring Modulith |
| `domain_config_history` | DB Table | PostgreSQL System Schema | Bảng lưu lịch sử audit hiện thời |

---

## 5. Research Questions & Hypotheses

### 5.1 Danh sách câu hỏi cần trả lời
- [ ] **Q1**: `JsonExportStrategy` nên thuộc `base-core` hay `system-admin-service`? Làm thế nào để hỗ trợ cả bảng phẳng thông thường và đồ thị quan hệ phức tạp có khóa ngoại liên kết?
- [ ] **Q2**: Cơ chế Import đa bảng phức tạp cần giải quyết bài toán phụ thuộc khóa ngoại (Foreign Key Constraints) và các chế độ cập nhật dữ liệu (`DELETE_AND_INSERT`, `UPSERT_MERGE`, `TRUNCATE_AND_LOAD`) như thế nào để vừa an toàn vừa giảm thiểu code lặp?
- [ ] **Q3**: Multi-sheet Excel export nên sửa đổi trực tiếp `ExcelExportStrategy` hay tạo riêng `MultiSheetExcelExportStrategy`? Các rủi ro về tương thích ngược, hiệu năng bộ nhớ và giới hạn POI là gì?
- [ ] **Q4**: Có nên dùng AOP hay Spring Event Listener cho `VersionedConfigDomain`? Cơ chế batching bộ nhớ đệm và Zero-loss durability hoạt động ra sao khi có sự cố hệ thống?
- [ ] **Q5**: Kiến trúc Dual Database (PostgreSQL + MongoDB) cho module audit nên được thiết kế như thế nào để đảm bảo tính cắm rút (pluggable), không gây rò rỉ công nghệ ra tầng nghiệp vụ?

### 5.2 Giả định cần kiểm chứng (Assumptions)
- [ ] **A1**: Việc mở rộng `ExcelExportStrategy` hiện tại sẽ phá vỡ tính bao đóng và an toàn kiểu (type safety) của `ExportStrategy<T>` do chữ ký hàm cố định nhận `Stream<T>`.
- [ ] **A2**: Spring Modulith Transactional Outbox đảm bảo 100% không mất mát sự kiện (Zero-loss) bằng cách lưu sự kiện vào PostgreSQL trước khi commit giao dịch chính.
- [ ] **A3**: Một Base Handler đơn giản (`DefaultSimpleImportHandler`) có thể tự động phục vụ 80-90% các bảng cấu hình thông thường mà không cần viết custom DAO/Repository logic.

---

## 6. Tiêu chí thành công (Success Criteria)

| Tiêu chí | Định nghĩa | Đo lường |
|----------|-----------|----------|
| **Research Coverage** | Bao phủ đầy đủ 3 chủ đề mở với dẫn chứng thực tế | Đầy đủ 8 artifacts theo chuẩn template |
| **Open Source & Web Analysis** | Thu thập và đánh giá các giải pháp open source hàng đầu | ≥ 3 dự án open source, ≥ 5 nguồn tham khảo |
| **Architectural Trade-off** | Phân tích toàn diện ưu/nhược điểm có tính toán định lượng | Bảng ma trận so sánh và phân tích rủi ro chi tiết |
| **Technical Spec Readiness** | Đặc tả kỹ thuật mức hệ thống, kiến trúc lớp, Mermaid diagram | Agent-ready, đầy đủ lược đồ, luồng xử lý và API spec |

---

> **Status**: Hoàn thành Phase 1. Sẵn sàng cho Phase 2 (Open Source Discovery) và Phase 3 (Internet Research).
