# New APIs — advanced-config-import-export-and-audit

## Enhanced Endpoints

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `GET` | `/api/v1/configs/export/all` | Multi-sheet Excel export of ALL registered domains | - | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `POST` | `/api/v1/configs/import` | Policy-driven multi-table import with checksum verification | `MultipartFile file`, `String mode` (TRUNCATE_AND_LOAD / DELETE_AND_INSERT / UPSERT_MERGE / PATCH_VALUES), `String? checksum` | `ImportSummaryResponse` |

## Import Modes

| Mode | Delete Existing | Insert # New APIs — advanced-config-import-export-and-audit

## Enhanced Endpoints

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `GET` | `/api/v1/configs/export/all` | Multi-sheet Excel export of ALL registered domains | - | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `POST` | `/api/v1/configs/import` | Policy-driven multi-table import with checksum verification | `MultipartFile file`, `String mode` (TRUNCATE_AND_LOAD / DELETE_AND_INSERT / UPSERT_MERGE / PATCH_VALUES), `String? checksum` | `ImportSummaryResponse` |

## Import Modes

| Mode | Delete Existing | Insert New | Update Existing | Preserve Sequence |
|------|:-:|:-:|:-:|:-:|
| `TRUNCATE_AND_LOAD` | ✅ (Truncate all) | ✅ | N/A | ❌ |
| `DELETE_AND_INSERT` | ✅ (Scoped) | ✅ | N/A | ✅ |
| `UPSERT_MERGE` | ❌ | ✅ | ✅ (Full) | ✅ |
| `PATCH_VALUES` | ❌ | ❌ | ✅ (Non-null only) | ✅ |

## Error Codes Added

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `SYS_022` | 409 CONFLICT | Circular dependency detected among import tables |
| `SYS_023` | 400 BAD_REQUEST | Import file SHA-256 checksum mismatch |
New | Update Existing | Preserve Sequence |
|------|:-:|:-:|:-:|:-:|
| `TRUNCATE_AND_LOAD` | ✅ (Truncate all) | ✅ | N/A | ❌ |
| `DELETE_AND_INSERT` | ✅ (Scoped) | ✅ | N/A | ✅ |
| `UPSERT_MERGE` | ❌ | ✅ | ✅ (Full) | ✅ |
| `PATCH_VALUES` | ❌ | ❌ | ✅ (Non-null only) | ✅ |

## Error Codes Added

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `SYS_022` | 409 CONFLICT | Circular dependency detected among import tables |
| `SYS_023` | 400 BAD_REQUEST | Import file SHA-256 checksum mismatch |
