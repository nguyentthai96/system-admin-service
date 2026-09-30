---
type: research_handoff
feature: config-management-system
date: 2026-09-30
recommendation: build
research_dir: openspec/research/config-management-system/
status: complete
---

# Research Handoff: Configuration Management System

## Recommendation

**BUILD custom** — vì yêu cầu cross-domain snapshot/rollback, named milestones, và export/import integration không có tool/library nào cung cấp sẵn. Tận dụng tech stack hiện có (Spring Boot + JPA + PostgreSQL JSONB + Redis) và patterns đã có (AuditAspect, SnowflakeEntity, DomainConfigService).

## Key Findings

- **Open Source**: Không có project nào đáp ứng đủ requirements (Configu = CLI tool, Envers = per-entity only, Spring Cloud Config = env config only)
- **Web Research**: Custom centralized approach là industry-recommended cho multi-domain admin panel config management
- **Gap Coverage**: 85% — gaps nhỏ (partition strategy, async export, conflict detection) có thể xử lý phase 2
- **Existing codebase**: Đã có `DomainConfigService` với versioning pattern → **EXTEND, không tạo mới**

## Use Cases Identified

| UC | Name | Domains |
|----|------|---------|
| UC-001 | Export cấu hình (Excel/CSV/JSON) | All |
| UC-002 | Import cấu hình (validate + preview + apply) | All |
| UC-003 | Xem lịch sử thay đổi (5 lần gần nhất) | All |
| UC-004 | Tạo Snapshot (named milestone) | All |
| UC-005 | Rollback Snapshot | All |
| UC-006 | CRUD i18n Messages | i18n |
| UC-007 | CRUD System Configs | System Config |

## Architecture Highlights

```
Config Domains (separate tables, optimized for reads)
  ├── menu_items (existing)
  ├── system_configs (NEW)
  ├── i18n_messages (NEW)
  └── departments (existing)

Shared Versioning Infrastructure (centralized)
  ├── config_change_history (ALL changes, ALL domains)
  ├── config_snapshots (named milestones)
  └── config_snapshot_details (N:M mapping)

Export/Import Engine (pluggable, strategy pattern)
  ├── ConfigDomainAdapter<E> interface
  ├── ExcelWriter (SXSSFWorkbook streaming)
  ├── CsvWriter (OpenCSV)
  └── JsonWriter (Jackson)
```

## Implementation Estimate

| Phase | Effort |
|-------|--------|
| DB Migrations | 2h |
| System Config CRUD | 4h |
| i18n Message CRUD | 4h |
| ConfigChangeService | 6h |
| ConfigSnapshotService | 6h |
| Domain Adapters (4) | 8h |
| ExportImportService | 8h |
| Controllers + Cache | 6h |
| Integration Tests | 6h |
| **Total** | **~50h** |

## Ready for

- `/wf_brainstorm_openspec config-management-system --from-research` — deep thinking với research context
- `/wf_pre_openspec openspec/research/config-management-system/business_analysis.md` — formal URD analysis
- `/wf_openspec config-management-system` — generate implementation artifacts

## Research Artifacts

| File | Content |
|------|---------|
| [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| [comparison_analysis.md](./comparison_analysis.md) | Approach comparison + technology decisions |
| [business_analysis.md](./business_analysis.md) | Use case decomposition + business rules |
| [technical_spec.md](./technical_spec.md) | Database schema, API endpoints, architecture, implementation plan |
