# Delta Specification — Configuration Management System

## Overview
Tài liệu tóm tắt sự khác biệt (delta) về kiến trúc và hành vi giữa hệ thống cấu hình cũ và hệ thống quản lý cấu hình tập trung mới (`config-management-system`).

---

## 1. Domain Configuration Governance

| Khía cạnh | Hiện trạng cũ | Hiện trạng mới (`config-management-system`) |
|---|---|---|
| **Cơ chế quản lý** | Rải rác theo từng domain riêng biệt (`system_configs`, `menu_items`, `domain_configs`). | Thống nhất qua SPI `VersionedConfigDomain<E>` và Registry tự động phát hiện Spring Bean (`ConfigDomainRegistry`). |
| **Audit Trail** | Ghi log đồng bộ trực tiếp vào database, dễ gây nghẽn và latency tăng cao. | Bất đồng bộ qua Spring Modulith Transactional Outbox (`EVENT_PUBLICATION`) và micro-batch buffer (`BatchAuditCollector`). |
| **Khả năng khôi phục** | Không có cơ chế rollback mốc snapshot, chỉ xem lịch sử đơn lẻ. | Hỗ trợ Milestone Snapshot toàn hệ thống, diff chi tiết cấp trường, và rollback nhận diện xung đột (conflict-aware rollback). |

---

## 2. File Export & Import Capabilities

| Tính năng | Hiện trạng cũ | Hiện trạng mới |
|---|---|---|
| **JSON Export** | Không có strategy streaming chuyên dụng. | `SimpleJsonExportStrategy` và `RelationalJsonExportStrategy` (kèm SHA-256 checksum). |
| **Excel Export** | Chỉ xuất đơn bảng một sheet (`ExcelExportStrategy`). | Hỗ trợ cả đơn bảng và `MultiSheetExcelExportStrategy` (xuất tất cả các miền sang 1 workbook nhiều tab với SXSSFWorkbook bộ nhớ O(1)). |
| **Import Policies** | Chưa có coordinator đa bảng. | `RelationalImportCoordinator` với 3 chế độ: `DELETE_AND_INSERT`, `UPSERT_MERGE`, `TRUNCATE_AND_LOAD`. |

---

## 3. Durability & Resiliency

| Tiêu chuẩn | Cam kết đạt được |
|---|---|
| **Zero-Loss Guarantee** | Sử dụng bảng `EVENT_PUBLICATION` trong cùng local ACID transaction PostgreSQL; nếu server crash hoặc reboot, Spring Modulith tự động republish và hoàn tất. |
| **Graceful Shutdown** | `BatchAuditCollector` implements `SmartLifecycle` (phase 10,000) đảm bảo xả toàn bộ buffer vào database trước khi DataSource đóng. |
| **Polyglot Storage** | Mặc định 100% chạy trên PostgreSQL Primary (`JSONB` + GIN Index); hỗ trợ mở rộng MongoDB qua `ConfigAuditStorageProvider` SPI không cần sửa code ứng dụng. |
