# Integration Map

_Generated: 2026-09-30_

## PostgreSQL Primary Database

- Client: `JdbcTemplate` & Spring Data `JpaRepository`
- Protocol: PostgreSQL JDBC Connection Pool (HikariCP)
- Components:
  - `ConfigAuditStorageProvider` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/storage/ConfigAuditStorageProvider.kt`
  - `ConfigMilestoneRepository` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/storage/repository/ConfigMilestoneRepository.kt`
  - `ConfigSnapshotRepository` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/storage/repository/ConfigSnapshotRepository.kt`
- Target Tables:
  - `event_publication` (Spring Modulith Outbox Table)
  - `config_milestones` (Milestone metadata)
  - `config_snapshots` (JSONB domain snapshots)
  - `domain_config_history` (Audit log 5 lần gần nhất)

## MongoDB Secondary Audit Database (Extensible)

- Client: `MongoTemplate` (`org.springframework.data.mongodb.core.MongoTemplate`)
- Protocol: MongoDB Wire Protocol
- Component: `MongoAuditStorageProvider` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/storage/MongoAuditStorageProvider.kt`
- Collections:
  - `config_audit_events` (Sự kiện audit chi tiết dạng document JSON)
  - `config_milestones` (Snapshot mốc cấu hình dung lượng lớn)

## Spring Modulith Transactional Outbox

- Client: `ApplicationEventPublisher` & `@ApplicationModuleListener`
- Protocol: In-process Transaction Synchronized Event Bus
- Component: `ConfigDomainEventListener` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/listener/ConfigDomainEventListener.kt`
- Mechanism: Ghi nhận sự kiện vào PostgreSQL trước commit, phát lại tự động khi restart nếu chưa hoàn tất

## Apache POI OOXML Streaming Engine

- Component: `SXSSFWorkbook` — `org.apache.poi.xssf.streaming.SXSSFWorkbook`
- Protocol: Direct OutputStream Serialization with Local Disk Temporary Files
- Strategies:
  - `ExcelExportStrategy` — `components/base-core/starters/base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/export/ExcelExportStrategy.kt`
  - `MultiSheetExcelExportStrategy` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/MultiSheetExcelExportStrategy.kt`

## Jackson Streaming JSON Engine

- Component: `ObjectMapper` & `JsonGenerator` (`com.fasterxml.jackson.databind.ObjectMapper`)
- Protocol: Direct HTTP Chunked Transfer Encoding Stream
- Strategy: `SimpleJsonExportStrategy` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/shared/file/export/SimpleJsonExportStrategy.kt`

## NOT DETECTED

- External Payment Gateways / NAPAS / Third-party APIs (Feature thuộc phạm vi System Admin & Framework Core, không tích hợp thanh toán tài chính bên ngoài)
