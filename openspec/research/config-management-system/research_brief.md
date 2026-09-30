# Research Brief: Configuration Management System

## 1. Feature Overview

**Feature Name:** Configuration Management System — Export/Import, Audit Trail, Snapshot/Rollback
**Input Mode:** MODE_IDEA (mô tả tự do từ user)
**Date:** 2026-09-30

### Mô tả
Hệ thống quản lý cấu hình đa domain (Menu, Common Config, i18n Messages, Department) với khả năng:
- **Export/Import**: Xuất/nhập dữ liệu cấu hình dạng Excel, CSV, JSON để backup và migrate giữa các môi trường
- **Audit Trail**: Lưu lịch sử thay đổi gần nhất, trace 5 lần thay đổi gần
- **Snapshot/Rollback**: Nhóm các thay đổi thành mốc (changeset/milestone), đặt tên, và cho phép rollback/apply theo mốc
- **Multi-domain**: Áp dụng cơ chế chung trên nhiều bảng cấu hình riêng biệt nhưng tối ưu hiệu suất đọc

```
[wf_feature_research](recipe;file:///home/nguyentthai96/Desktop/bigbang/boilerplate/frontends/admindashboard/.agents/workflows/wf_feature_research.md) đối với các config như config menu management, config cấu hình biến động, cấu hình message i18n động từ database cấu hình thêm sửa xóa, 
--> tôi muốn đối với các cấu hình này cần có cơ chế export ra file excel, csv, json... để làm export/import backup và migrate dễ dàng, đồng bộ giữa các môi trường giai đoạn,
backup lưu lịch sử thay đổi lần sửa edit gần nhất (audit),
và sẽ có lần sửa update cũng có thể tạo thành các lịch sử group giai đoạn update tạo theo cụm thay đổi, để dễ dàng tách nó ra thành một giai đoạn sửa tính như một mốc thay đổi có tạo name và maping các thay đổi để tiến hành switching apply theo mốc thay đổi và rollback dễ dàng, theo các config cấu hình cụ thể.
Vì thế tôi sẽ cần thiết kế phương án lưu trữ cấu hình có thể cho nhiều trường hợp các trường hợp riêng biệt lưu trữ trên các bảng riêng biệt tối ưu cho việc load dữ liệu sử dụng hiệu suất cao, nhưng cần thiết kế cơ chế dễ dàng tạo các mốc thay đổi dữ liệu và rollback áp dụng thay đổi mốc gồm nhiều thay đổi trên các record khác nhau cũng có thể trace xem thay đổi 5 lần gần, và cũng cho phép export/import và audit
Tôi cần đưa ra thiết kế chuẩn để có thể tổ chức trên nhiều domain cấu hình, ví dụ cấu hình menu (thì sẽ có menu hiển thị, menu con, program api/endpoint, các nút bấm trên menu ui), cấu hình config common (chứa các config code, config value, config group, mô tả config...), cấu hình message i18n đa ngôn ngữ (message code, message group, nội dung các ngôn ngữ tiếng việt, tiếng anh, tiếng hàn... mô tả message), cấu hình department phòng ban (danh sách các phòng ban, đâu là bộ phận con, đâu là sếp trưởng phó bộ phận, kết nối với danh sách nhân sự) cũng sẽ cần có thể export/export và mô hình lịch sử thay đổi edit có thể phức tạp hơn trong số trường hợp đặc biệt 

- menu
- common-config
- i18n-message
- department
- position
Export/Import - Export configs to Excel, CSV, JSON for backup and migration between environments
Audit History - Track changes, store edit history
Change Groups/Snapshots - Group changes into named milestones/snapshots for easy rollback and switching
Multiple Config Domains - Menu management, common config, i18n messages, department structure
High-performance storage - Separate tables optimized for read performance
Rollback mechanism - Apply/revert changes by milestone I should follow the workflow steps now: reading the feature-research skill, scanning the existing codebase, and then generating the research artifacts. Let me start with the skill file and project scan.
````

## 2. Keywords

| # | Keyword | Scope |
|---|---------|-------|
| 1 | Configuration versioning | Database pattern |
| 2 | Changeset/Snapshot rollback | Core mechanism |
| 3 | Export/Import Excel CSV JSON | Data portability |
| 4 | Audit trail configuration | Change tracking |
| 5 | Multi-domain config management | Architecture |
| 6 | Event sourcing configuration | Pattern |
| 7 | i18n message database management | Specific domain |
| 8 | Menu management versioning | Specific domain |
| 9 | Hibernate Envers vs custom audit | Technology choice |
| 10 | Apache POI SXSSFWorkbook streaming | Export library |

## 3. Search Queries

- `"configuration management export import versioning rollback best practices enterprise"`
- `"database configuration versioning snapshot rollback Spring Boot JPA audit trail"`
- `"changeset batch group rollback similar Flyway Liquibase pattern application config"`
- `"event sourcing configuration management changeset snapshot rollback multi-table database"`
- `"Apache POI Spring Boot export Excel CSV JSON configuration data"`
- `"i18n message management database dynamic multilingual admin CRUD Spring Boot"`
- `"Hibernate Envers vs custom audit table configuration versioning comparison"`

## 4. Current System Analysis

### 4.1 Related Features

| Feature | Module | File/Location | Relevance | Notes |
|---------|--------|---------------|-----------|-------|
| Domain Config | `sysadmin.tenant` | `DomainConfigEntity.kt`, `DomainConfigService.kt` | ⭐ HIGH | Đã có versioning + rollback cơ bản (snapshot JSONB) |
| Domain Config History | `sysadmin.tenant` | `DomainConfigHistoryEntity.kt` | ⭐ HIGH | Đã có bảng history, version tracking |
| Menu Management | `sysadminservice.menu` | V1__create_menu_tables.sql | ⭐ HIGH | Menu tree structure, permissions — CHƯA CÓ versioning/export |
| Feature Flags | `sysadminservice.config` | `FeatureFlagEntity.kt` | 🔵 MEDIUM | Config domain khác — CHƯA CÓ versioning |
| Organization | `sysadminservice.organization` | `DepartmentEntity.kt`, `PositionEntity.kt` | ⭐ HIGH | Tree hierarchy — CHƯA CÓ versioning/export |
| Audit Logging | `sysadminservice.audit` | `AuditAspect.kt`, `AuditService.kt` | ⭐ HIGH | AOP-based audit, sensitive masking — CÓ THỂ REUSE |
| Navigation Config | Frontend | `navigationConfig.ts` | 🔵 MEDIUM | Static menu config — sẽ chuyển sang dynamic |

### 4.2 Existing Code Patterns

| Pattern | Implementation | Notes |
|---------|----------------|-------|
| Architecture | Clean Architecture (Hexagonal) | `adapter/in/web`, `adapter/out/persistence`, `application` layers |
| Data Access | Spring Data JPA | `SnowflakePersistentAuditableEntity` base class |
| API Style | REST (@RestController) | Standard Spring MVC |
| Entity Base | `SnowflakePersistentAuditableEntity`, `TreeEntity` | Snowflake ID + auditable fields |
| Caching | Redis (StringRedisTemplate) | TTL 30 min, key prefix pattern |
| Audit | AOP (@Aspect) + custom audit service | Sensitive field masking, JSONB diff |
| History | Snapshot-based (JSONB column) | Current pattern trong DomainConfigService |
| DB Migration | Flyway | `V{n}__description.sql` pattern |
| Build | Gradle (Kotlin DSL) | `ntt.spring-app-conventions` plugin |

### 4.3 Tech Stack Constraints

| Aspect | Value |
|--------|-------|
| Language | Kotlin (JVM) |
| Framework | Spring Boot 4.x |
| Database | PostgreSQL (with JSONB support) |
| Cache | Redis |
| Build Tool | Gradle (Kotlin DSL) |
| Migration | Flyway |
| ID Strategy | Snowflake ID |
| Key Dependencies | base-web-starter, base-security-starter, base-data-starter, spring-modulith |

### 4.4 Integration Points

| Integration | Type | Notes |
|-------------|------|-------|
| `menu_items` table | Data source | Export/import target |
| `domain_configs` table | Data source | Đã có history — EXTEND |
| `departments`, `positions` tables | Data source | Export/import target |
| `feature_flags` table | Data source | Export/import target |
| `audit_logs` table | Audit sink | Immutable (no UPDATE/DELETE rules) |
| `AuditAspect` | Reusable component | AOP audit — có thể extend cho config changes |
| Redis cache | Cache invalidation | Cần invalidate khi rollback/apply snapshot |
| Kafka | Event publishing | Có thể publish config change events |
