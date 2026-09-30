# Service Structure

_Generated: 2026-09-30_

## system-admin-service

### Detected Packages

- `com.ntt.sysadmin.menu`: Quản lý menu hệ thống, phân quyền menu, cây phân cấp đa cấp (`adapter/in/web`, `adapter/out/persistence`, `application`).
- `com.ntt.sysadmin.tenant`: Quản lý cấu hình tenant/domain (`domain_configs`, `domain_config_history`, Redis caching).
- `com.ntt.sysadminservice.config`: Cấu hình hệ thống động, feature flags (`FeatureFlagEntity`, `FeatureFlagService`, `DomainConfigService`).
- `com.ntt.sysadminservice.organization`: Quản lý phòng ban và chức danh (`DepartmentEntity`, `PositionEntity`, `UserPositionEntity`).
- `com.ntt.sysadminservice.audit`: Quản lý audit log và AOP aspect (`AuditLogEntity`, `AuditAspect`, `AuditService`, `AuditController`).
- `com.ntt.sysadminservice.workflow`: Động cơ quy trình phê duyệt (`WorkflowEntities`, `WorkflowEngine`, `WorkflowService`).
- `com.ntt.sysadminservice.shared.exception`: Xử lý ngoại lệ toàn cục (`SysAdminErrorCode`, `SysAdminExceptions`, `SysAdminControllerAdvice`).
- `com.ntt.sysadminservice.shared.persistence`: Hỗ trợ lưu trữ cây phân cấp (`TreeEntity`, `TreeBuilder`).
- `com.ntt.sysadminservice.shared.security`: Xác thực JWT và session (`ResourceJwtAuthFilter`, `DefaultSessionManagement`, `SecurityConfig`).

### Target Packages (To be created)

- `com.ntt.sysadmin.versioning`: Chứa framework versioning cấp cao (`VersionedConfigDomain<E>`, `ConfigChangeTracker`, `ConfigSnapshotManager`).
- `com.ntt.sysadmin.versioning.storage`: Chứa Pluggable Storage SPI (`ConfigAuditStorageProvider`, `PostgreSqlJsonbAuditStorageProvider`, `MongoDbAuditStorageProvider`).
- `com.ntt.sysadmin.versioning.buffer`: Chứa micro-batching buffer (`BatchAuditCollector`).
- `com.ntt.sysadmin.i18n`: Quản lý tin nhắn đa ngôn ngữ động (`I18nMessageEntity`, `I18nMessageService`, `I18nMessageController`).

### Not Found

- GRPCServices: NOT DETECTED (giao tiếp microservice chủ yếu qua REST API và Kafka).
- Batch Job Configs: NOT DETECTED trong system-admin-service (tái sử dụng từ `base-file-starter`).

### Naming Convention

- Entity: `<Domain>Entity` (ví dụ: `MenuEntity`, `DomainConfigEntity`, `AuditLogEntity`)
- Repository: `<Domain>Repository` (Spring Data JPA)
- Service: `<Domain>Service`
- Controller: `<Domain>Controller`
- DTOs: `<Action><Domain>Request` / `<Domain>Response` (ví dụ: `UpdateDomainConfigRequest`, `DomainConfigResponse`)
- Error Code: `SysAdminErrorCode.<NAME>` (implementing `ErrorCodeBase`)
- Table Names: snake_case (ví dụ: `domain_configs`, `domain_config_history`, `menu_items`)
