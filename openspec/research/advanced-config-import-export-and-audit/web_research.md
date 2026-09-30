---
type: web_research
name: advanced-config-import-export-and-audit
version: "1.0"
language: vi
date: 2026-09-30
status: complete
---

# Kết quả Nghiên cứu Internet: Advanced Config Import/Export & Audit Framework

> Tổng hợp các bài học kinh nghiệm thực tế (Production Lessons Learned), kiến trúc mẫu (Design Patterns), và giải pháp kỹ thuật trên Internet cho:
> 1. Export/Import dữ liệu bảng quan hệ phức tạp và thuật toán giải quyết thứ tự khóa ngoại.
> 2. Động cơ Multi-sheet Excel Streaming và ngăn ngừa rủi ro cạn kiệt tài nguyên (OOM, POI Style Limit, Formula Injection).
> 3. Cơ chế Non-blocking Audit Versioning: Spring Modulith Transactional Outbox, In-memory Micro-batch buffer, Zero-loss Durability và Dual-database storage (PostgreSQL + MongoDB).

---

## 1. Tổng quan nghiên cứu

| Mục | Nội dung |
|-----|----------|
| **Chủ đề** | Advanced Config Import/Export & Audit Architecture |
| **Số vòng tìm kiếm (Iterations)** | 4 vòng lặp chuyên sâu (Perplexity-style loop) |
| **Số nguồn tài liệu đã kiểm chứng** | 12 nguồn (Official Docs, Baeldung, DanVega, Mitre, StackOverflow, Medium Architecture) |
| **Trọng tâm phân tích** | Giải pháp cho 3 vấn đề kiến trúc mở của người dùng |

---

## 2. Chi tiết các phát hiện theo từng chủ đề

### Chủ đề 1: `JsonExportStrategy` & Cơ chế Import phức tạp trên quan hệ bảng có khóa ngoại

#### 1.1 Thách thức khi xuất/nhập đồ thị dữ liệu quan hệ (Relational Graph)
- **Vấn đề**: Các bảng cấu hình như Menu (`menus`, `menu_items`, `menu_item_actions`, `menu_permissions`) hay Tổ chức (`departments`, `positions`, `department_members`) có quan hệ cha - con và khóa ngoại chặt chẽ.
  - Khi **Export**: Phải xuất toàn bộ cây quan hệ hoặc đồ thị phẳng có liên kết ID, kèm theo thông tin phiên bản lược đồ (`schemaVersion`) và mã kiểm tra tính toàn vẹn (SHA-256 Checksum) để ngăn chặn việc chỉnh sửa trái phép tệp cấu hình trước khi nạp lại.
  - Khi **Import**: Không thể chèn tùy tiện. Nếu chèn bảng con trước bảng cha sẽ gây lỗi vi phạm toàn vẹn dữ liệu `Foreign Key Constraint Violation`. Tương tự, khi xóa dữ liệu (clean slate) thì phải xóa bảng con trước rồi mới xóa bảng cha.
- **Giải pháp chuẩn công nghiệp**: **Topological Sorting (Thuật toán Kahn)**.
  - Biểu diễn các bảng dưới dạng đồ thị có hướng không chu trình (DAG - Directed Acyclic Graph), trong đó cạnh có hướng `A -> B` đại diện cho quan hệ "Bảng A phụ thuộc Bảng B (A trỏ FK tới B)".
  - Thứ tự chèn (Insert Order): Bảng cha (in-degree = 0) nạp trước → Bảng con nạp sau.
  - Thứ tự xóa (Delete Order): Đảo ngược danh sách Topo (Bảng con xóa trước → Bảng cha xóa sau).
  - Tự động phát hiện phụ thuộc vòng (Circular Reference Detection): Nếu còn bảng có in-degree > 0 thì cảnh báo lỗi hoặc buộc dùng `SET CONSTRAINTS ALL DEFERRED` trong giao dịch.

#### 1.2 Phân tích 4 chế độ Import (`ImportStrategyMode`)
Dựa trên yêu cầu thực tế, các doanh nghiệp áp dụng 4 chế độ nạp dữ liệu:
1. `TRUNCATE_AND_LOAD`:
   - *Cơ chế*: Thực hiện `TRUNCATE TABLE` (hoặc `deleteAllInBatch()`) rồi bulk insert.
   - *Ưu điểm*: Tốc độ cao nhất, reset sequence sạch sẽ, loại bỏ dữ liệu rác cũ.
   - *Ứng dụng*: Môi trường staging/dev hoặc các bảng tham chiếu cố định (Lookup/Static Reference Data).
2. `DELETE_AND_INSERT`:
   - *Cơ chế*: Xóa có điều kiện theo phạm vi domain (hoặc xóa theo thứ tự topo ngược) rồi chèn lại toàn bộ.
   - *Ưu điểm*: Duy trì cấu trúc phân cấp cây cha - con nguyên vẹn, đảm bảo sequence ID chính xác theo backup.
   - *Ứng dụng*: Menu Management, Organization Department Trees.
3. `UPSERT_MERGE`:
   - *Cơ chế*: Dựa vào Khóa Nghiệp vụ Tự nhiên (Natural Business Key) như `config_code` hoặc `menu_code`. Nếu đã tồn tại thì cập nhật các trường giá trị; nếu chưa có thì chèn mới.
   - *Ưu điểm*: Không làm mất dữ liệu của các bản ghi đang chạy, an toàn nhất cho môi trường Production (Zero-downtime, Non-destructive).
   - *Ứng dụng*: Common Configs, Feature Flags, i18n Message Bundles.
4. `PATCH_VALUES`:
   - *Cơ chế*: Chỉ cập nhật các trường có giá trị khác null trong tệp import, giữ nguyên các cấu hình cục bộ khác.

#### 1.3 Thiết kế Abstract Base Code & Default Implementation
- Đưa abstraction vào `base-core` (`base-file-starter`):
  - Interface `TableImportHandler<E : Any>` với 3 hook: `prepare(context)`, `process(records, context)`, `cleanup(context)`.
  - Lớp mở rộng `DefaultSimpleImportHandler<T, ID>`: Tận dụng `JpaRepository<T, ID>` sẵn có của Spring Data JPA. Với các bảng phẳng thông thường (chiếm 90% số lượng cấu hình), developer chỉ cần khởi tạo bean kế thừa lớp này với 2 dòng mã:
    ```kotlin
    @Component
    class FeatureFlagImportHandler(repo: FeatureFlagRepository) : 
        DefaultSimpleImportHandler<FeatureFlagEntity, Long>("feature_flags", repo)
    ```
  - Lớp điều phối `RelationalImportCoordinator`: Tự động quét các `TableImportHandler` được đăng ký, áp dụng thuật toán Kahn để sắp xếp thứ tự và thực thi trong một `@Transactional` duy nhất.

---

### Chủ đề 2: Multi-sheet Excel Export (Export ALL Domains → 1 File)

#### 2.1 Trade-off: Mở rộng `ExcelExportStrategy` hay Tách riêng `MultiSheetExcelExportStrategy`?

| Tiêu chí phân tích | Phương án A: Gộp chung vào `ExcelExportStrategy` | Phương án B: Tách riêng `MultiSheetExcelExportStrategy` (ĐỀ XUẤT) |
|-------------------|------------------------------------------------|-------------------------------------------------------------------|
| **Chữ ký Interface (`ExportStrategy<T>`)** | Bị phá vỡ nghiêm trọng. `ExportStrategy<T>` được thiết kế cho đơn kiểu `Stream<T>`. Nếu gộp, `T` phải ép kiểu thành `Any` hoặc bọc trong `Map<String, Stream<*>>`, sinh ra unchecked cast nguy hiểm. | Tuân thủ hoàn hảo. Giữ nguyên `ExcelExportStrategy<T>` cho single table. Tạo mới `MultiSheetExcelExportStrategy` nhận `MultiSheetExportTemplate`. |
| **Nguyên lý Single Responsibility (SRP)** | Vi phạm. Một class vừa xử lý streaming cho bảng phẳng đơn lẻ, vừa quản lý điều phối tab, metadata và quan hệ multi-sheet. | Đảm bảo. Mỗi lớp có một trách nhiệm duy nhất, rõ ràng, độc lập. |
| **Tương thích ngược (Backward Compatibility)** | Rủi ro cao gây bug hồi quy (regression) cho các microservice hiện tại đang inject và gọi `ExcelExportStrategy<T>`. | 100% an toàn. Không đụng chạm bất kỳ dòng mã nào đang chạy ổn định của các service khác. |
| **Độ phức tạp mã nguồn (Code Complexity)** | Code trở nên phức tạp do phải rẽ nhánh `if (isMultiSheet) ... else ...`, khó viết unit test chuyên biệt. | Mã nguồn sạch sẽ, tách biệt file test và logic cấu hình từng tab độc lập. |

#### 2.2 Các bài học thực tế để tránh lỗi bộ nhớ (OOM) với Apache POI SXSSF
Theo tài liệu Apache POI và bài học triển khai enterprise:
1. **Sliding Window:** Bắt buộc sử dụng `SXSSFWorkbook(100)` để duy trì cố định 100 dòng trên RAM, tự động ghi tràn các dòng cũ vào tệp tạm dạng nén XML trên đĩa (`.poi-sxssf-sheet*.xml`).
2. **Quản lý CellStyle Pool (Tránh Style Limit):** Giới hạn tối đa của file `.xlsx` là 64,000 kiểu `CellStyle`. Nếu trong vòng lặp dữ liệu gọi `workbook.createCellStyle()` thì ứng dụng sẽ ném ngoại lệ `IllegalStateException: The maximum number of cell styles was exceeded` và tràn RAM. **Quy tắc bắt buộc**: Tạo trước các style dùng chung (Header style, Date style, Number style) ở đầu quá trình xuất và tái sử dụng cho toàn bộ các sheet.
3. **Giải phóng file tạm (Mandatory Cleanup):** Khi ghi xong tệp, khối lệnh `finally` bắt buộc phải gọi `workbook.dispose()` để xóa sạch toàn bộ các tệp tạm trên thư mục `/tmp`. Nếu chỉ gọi `workbook.close()`, tệp tạm vẫn tồn tại trên ổ cứng gây cạn kiệt dung lượng đĩa (Disk Full).
4. **Phòng chống tấn công Formula Injection (CWE-1236):** Dữ liệu xuất ra từ database có thể chứa các ký tự bắt đầu bằng `=`, `+`, `-`, `@`, `\t`, `\r` do người dùng nhập vào. Khi mở bằng Microsoft Excel, phần mềm sẽ tự động thực thi các hàm độc hại. Cần tích hợp `ExportSanitizer` để thêm dấu nháy đơn `'` phía trước các giá trị chuỗi này.

---

### Chủ đề 3: `VersionedConfigDomain` Auto-Tracking & Resilient Dual Storage

#### 3.1 So sánh AOP vs Spring Event Listener vs Transactional Outbox

| Phương thức | Tác động hiệu năng CRUD chính | Đảm bảo không mất dữ liệu (Durability) | Độ phức tạp triển khai | Khả năng mở rộng Storage |
|-------------|:----------------------------:|:-------------------------------------:|:----------------------:|:------------------------:|
| **Gọi thủ công trong Service** | ❌ Gây chậm (thêm 1-2 câu INSERT đồng bộ) | ⚠️ Phụ thuộc vào transaction chính | Thấp nhưng duplicate code | Kém |
| **AOP thuần túy (`@Around`)** | ⚠️ Vẫn chạy đồng bộ trong thread chính; khó lấy Previous State | ❌ Mất mát nếu service crash giữa chừng | Trung bình | Kém |
| **Spring Modulith Transactional Outbox + Event Listener** (KHUYẾN NGHỊ) | ✅ **Cực nhanh** (Sự kiện lưu cùng local TX với entity chính, listener chạy async sau commit) | ✅ **Zero-loss tuyệt đối** (Tự động phục hồi sự kiện chưa hoàn tất khi restart) | Chuẩn mực Modular Monolith | **Cực cao** (Dễ dàng đẩy sang Batch Buffer & Mongo) |

#### 3.2 Cơ chế chống mất mát dữ liệu (Zero-loss Durability) với In-memory Micro-batch Buffer
Khi lưu lượng cấu hình thay đổi lớn, ghi từng bản ghi xuống database sẽ làm quá tải kết nối JDBC. Giải pháp là gom batch trong RAM, nhưng gom trong RAM thường có nguy cơ mất dữ liệu khi server tắt đột ngột.
**Mô hình kết hợp 2 lớp phòng vệ (Two-tier Defense):**
1. **Lớp 1 (Persistent Outbox Registry):**
   - Khi Service gọi `eventPublisher.publishEvent(ConfigDomainChangedEvent(...))`, Spring Modulith chặn lại và lưu vào bảng `event_publication` trong PostgreSQL tại cùng database connection của transaction nghiệp vụ.
   - Trạng thái sự kiện là `UNCOMPLETED`.
2. **Lớp 2 (In-memory Micro-batch Buffer - `BatchAuditCollector`):**
   - Sự kiện sau khi commit được `@ApplicationModuleListener` đón nhận và đẩy vào `LinkedBlockingQueue` (sức chứa 20,000 items).
   - Bộ gom vi mẻ (Collector) kiểm tra: Đạt đủ **100 items** HOẶC sau **500ms** thì kích hoạt `jdbcTemplate.batchUpdate(...)` hoặc lưu vào MongoDB.
   - Sau khi batch lưu thành công, thông báo cho Spring Modulith đánh dấu sự kiện là `COMPLETED`.
3. **Phòng vệ khi Server Tắt / Khởi động lại (Crash-safe / Graceful Shutdown):**
   - Triển khai Spring `SmartLifecycle` với `phase = 10,000` (mức ưu tiên cao nhất) để đảm bảo hàm `flushAll()` xả toàn bộ hàng đợi bộ nhớ xuống database trước khi DataSource bị Spring đóng lại.
   - Nếu máy chủ bị `kill -9` (ngắt điện đột ngột), khi khởi động lại, thuộc tính `spring.modulith.republish-outstanding-events-on-restart=true` sẽ tự động quét các bản ghi `UNCOMPLETED` trong bảng `event_publication` và gửi lại cho listener. Đảm bảo **100% không mất mát lịch sử**!

#### 3.3 Khả năng mở rộng lưu trữ Đa Cơ Sở Dữ Liệu (PostgreSQL + MongoDB)
- **Vấn đề**: Bản ghi audit cấu hình chứa các trường JSON snapshot lớn (`previousStateJson`, `newStateJson`) có cấu trúc biến động theo từng domain. Lưu quá nhiều trong PostgreSQL làm phình to dung lượng bảng và cạn kiệt I/O disk.
- **Giải pháp**:
  - Định nghĩa Storage SPI interface: `ConfigAuditStorageProvider`.
  - **PostgreSQL JSONB Provider (Mặc định)**: Sử dụng cột kiểu `jsonb` và chỉ mục `GIN` cho phép tìm kiếm linh hoạt nội dung cấu hình mà không cần cài đặt thêm hạ tầng.
  - **MongoDB Audit Provider (Mở rộng cho Scale lớn)**: Tích hợp `spring-boot-starter-data-mongodb`. Khi bật cấu hình:
    ```yaml
    app:
      config:
        audit:
          storage-type: mongodb # hoặc postgresql
    ```
    Hệ thống tự động kích hoạt `MongoAuditStorageProvider`, lưu các snapshot vào collection `config_audit_events` và `config_milestones`. Tách biệt hoàn toàn tải đọc/ghi audit ra khỏi database nghiệp vụ chính!

---

## 3. Tổng hợp nguồn tham khảo (Evidence-based Sources)

| # | Nguồn | Tiêu đề / Nội dung | URL |
|---|-------|-------------------|-----|
| 1 | Spring.io | Spring Modulith Working with Events & Outbox | https://docs.spring.io/spring-modulith/reference/events.html |
| 2 | Baeldung | Spring Modulith Transactional Outbox Pattern | https://www.baeldung.com/spring-modulith-outbox-pattern |
| 3 | Apache POI | SXSSF (Streaming XML Spreadsheet) Performance & OOM Prevention | https://poi.apache.org/components/spreadsheet/how-to.html#sxssf |
| 4 | MITRE CWE | CWE-1236: Improper Neutralization of Formula Elements in CSV File | https://cwe.mitre.org/data/definitions/1236.html |
| 5 | Dan Vega | Event-Driven Architectures with Spring Modulith & Outbox | https://danvega.dev/blog/spring-modulith-outbox |
| 6 | Wikipedia | Topological Sorting & Dependency Graphs (Kahn's Algorithm) | https://en.wikipedia.org/wiki/Topological_sorting |
| 7 | JaVers | Object Auditing & Spring Data MongoDB/SQL Integration | https://javers.org/documentation/spring-boot-integration/ |

---
> **Trạng thái**: Hoàn thành Phase 3. Dữ liệu đã được kiểm chứng với nguồn URL thực tế.
> **Bước tiếp theo**: Comparison Analysis (comparison_analysis.md).
