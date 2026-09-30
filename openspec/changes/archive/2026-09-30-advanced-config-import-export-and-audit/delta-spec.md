# Delta Spec: advanced-config-import-export-and-audit

> Changes relative to `config-management-system` baseline.

## Summary of Delta

| Category | Added | Modified | Removed |
|----------|:-----:|:--------:|:-------:|
| Classes (base-file-starter) | 4 NEW | 1 MODIFY | 0 |
| Classes MOVED to base-file-starter | 8 | 0 | 8 (from sysadmin) |
| Classes (system-admin-service) | 4 NEW | 1 MODIFY | 0 |
| API Endpoints | 0 | 2 ENHANCE | 0 |
| Database Tables | 0 | 0 | 0 |
| Capabilities | 4 NEW | 2 MODIFIED | 0 |

## New Capabilities

### 1. dynamic-jpa-metamodel-export
- **FR Coverage**: FR-003
- **Components**: `DynamicJpaMetamodelSheetExtractor`, `SheetExportDefinition<T>`, `@ExportColumn`
- **Spec**: [specs/dynamic-jpa-metamodel-export/spec.md](specs/dynamic-jpa-metamodel-export/spec.md)

### 2. four-mode-policy-import
- **FR Coverage**: FR-005, FR-006
- **Components**: `ImportStrategyMode` (4 modes), `DefaultSimpleImportHandler<T, ID>`, `PATCH_VALUES` mode
- **Spec**: [specs/four-mode-policy-import/spec.md](specs/four-mode-policy-import/spec.md)

### 3. two-pass-tree-import
- **FR Coverage**: FR-015
- **Components**: `TwoPassTreeImportHandler<T, ID>`, `SET CONSTRAINTS ALL DEFERRED`
- **Spec**: [specs/four-mode-policy-import/spec.md](specs/four-mode-policy-import/spec.md)

### 4. formula-injection-protection
- **FR Coverage**: FR-013
- **Components**: `ExportSanitizer` integration into `MultiSheetExcelExportStrategy`
- **Spec**: [specs/base-file-starter-promotion/spec.md](specs/base-file-starter-promotion/spec.md)

## Modified Capabilities

### 5. relational-file-exchange (ENHANCED)
- **Delta**: Promoted from `system-admin-service/shared/file/` → `base-file-starter`. Added `PATCH_VALUES` mode. No API breaking changes.
- **Spec**: [specs/base-file-starter-promotion/spec.md](specs/base-file-starter-promotion/spec.md)

### 6. event-driven-audit (ENHANCED)
- **Delta**: Added auto-republish on restart (`spring.modulith.republish-outstanding-events-on-restart=true`). MongoDB fallback resilience.
- **FR Coverage**: FR-014

## FR Traceability

| FR-ID | Capability | Status |
|-------|-----------|--------|
| FR-001 | base-file-starter-promotion | ✅ Covered |
| FR-002 | base-file-starter-promotion | ✅ Covered |
| FR-003 | dynamic-jpa-metamodel-export | ✅ Covered |
| FR-004 | base-file-starter-promotion | ✅ Covered |
| FR-005 | four-mode-policy-import | ✅ Covered |
| FR-006 | four-mode-policy-import | ✅ Covered |
| FR-007 | event-driven-audit (existing) | ✅ Already implemented |
| FR-008 | event-driven-audit (existing) | ✅ Already implemented |
| FR-009 | config-versioning-snapshot (existing) | ✅ Already implemented |
| FR-010 | config-versioning-snapshot (existing) | ✅ Already implemented |
| FR-011 | config-versioning-snapshot (existing) | ✅ Already implemented |
| FR-012 | event-driven-audit (enhanced) | ✅ Enhanced |
| FR-013 | formula-injection-protection | ✅ NEW |
| FR-014 | event-driven-audit (enhanced) | ✅ NEW |
| FR-015 | two-pass-tree-import | ✅ NEW |

**Coverage**: 15/15 FRs (100%)
