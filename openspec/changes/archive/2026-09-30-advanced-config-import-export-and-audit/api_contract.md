# API Contract: advanced-config-import-export-and-audit

<!-- contract-version: 1.0 -->
<!-- backend-status: implemented -->
<!-- frontend-status: implemented -->
<!-- openapi-spec: N/A -->
<!-- generated-by: wf_api_contract -->
<!-- generated-at: 2026-09-30T13:35:00+07:00 -->

> **Pipeline Bridge**: This contract is the shared source of truth between Backend Track (`wf_openspec_apply`) and Frontend Track (`wf_fe_spec` → `wf_fe_apply`).
> Both tracks MUST validate their implementation against this contract.

---

## Response Wrapper (Standard)

> All API responses MUST be wrapped in this standard envelope.
> Frontend service layer MUST unwrap `data` field before returning to hooks/components.

```typescript
interface ApiResponse<T> {
  code: string;        // "00" = success, other = error
  message: string;     // Human-readable message
  data: T;             // Response payload
  timestamp: string;   // ISO 8601 UTC
}

interface ApiErrorResponse {
  code: string;        // Error code (e.g., "SYS_001")
  message: string;     // Error message
  errors?: Record<string, string[]>; // Field-level validation errors
  timestamp: string;
}
```

---

## Endpoints

### POST `/api/v1/configs/milestones`

- **Auth**: `required` (Header: `X-User-Id`)
- **FR**: FR-009
- **Description**: Create a configuration milestone grouping point-in-time domain snapshots.
- **Request**:
  ```typescript
  interface CreateMilestoneRequest {
    name: string;              // @NotBlank, visibility: public
    description?: string;      // visibility: public
    domainNames?: string[];    // Domain filter — null = all domains, visibility: public
  }
  ```
- **Response (200)**:
  ```typescript
  // Wrapped: ApiResponse<MilestoneResponse>
  interface MilestoneResponse {
    id: string;                           // visibility: public
    name: string;                         // visibility: public
    description?: string;                 // visibility: public
    createdBy?: string;                   // visibility: public
    createdAt: string;                    // ISO 8601 Instant, visibility: public
    status: string;                       // "ACTIVE" | "ARCHIVED", visibility: public
    snapshots: SnapshotSummaryResponse[]; // visibility: public
  }
  ```
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|-------------|-------------|-----------------|
  | `SYS_001` | 500 | System error creating milestone | `toast` |

---

### GET `/api/v1/configs/milestones`

- **Auth**: `required`
- **FR**: FR-009
- **Description**: List all configuration milestones with their snapshot summaries.
- **Request**: *None*
- **Response (200)**:
  ```typescript
  // Wrapped: ApiResponse<MilestoneResponse[]>
  // (Reuses MilestoneResponse from POST /milestones)
  ```
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|-------------|-------------|-----------------|
  | `SYS_001` | 500 | System error | `toast` |

---

### GET `/api/v1/configs/milestones/{id}`

- **Auth**: `required`
- **FR**: FR-009
- **Description**: Get a specific milestone with its snapshot summaries.
- **Request**:
  ```typescript
  // Path params only
  // id: string — milestone UUID
  ```
- **Response (200)**:
  ```typescript
  // Wrapped: ApiResponse<MilestoneResponse>
  ```
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|-------------|-------------|-----------------|
  | `SYS_004` | 404 | Milestone not found | `toast` |

---

### GET `/api/v1/configs/snapshots/{id}`

- **Auth**: `required`
- **FR**: FR-009
- **Description**: Get a specific snapshot's metadata.
- **Request**:
  ```typescript
  // Path params only
  // id: string — snapshot UUID
  ```
- **Response (200)**:
  ```typescript
  // Wrapped: ApiResponse<SnapshotSummaryResponse>
  interface SnapshotSummaryResponse {
    id: string;                // visibility: public
    domainName: string;        // visibility: public
    checksumSha256: string;    // visibility: public
    recordCount: number;       // visibility: public
    createdAt: string;         // ISO 8601 Instant, visibility: public
  }
  ```
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|-------------|-------------|-----------------|
  | `SYS_019` | 404 | Snapshot not found | `toast` |

---

### GET `/api/v1/configs/domains/{domainName}/diff`

- **Auth**: `required`
- **FR**: FR-010
- **Description**: Compare current live state of a domain against a historical snapshot.
- **Request**:
  ```typescript
  // Path params: domainName: string
  // Query params:
  interface GetDiffParams {
    snapshotId: string;        // visibility: public
  }
  ```
- **Response (200)**:
  ```typescript
  // Wrapped: ApiResponse<ConfigDiffResponse>
  interface ConfigDiffResponse {
    domainName: string;                  // visibility: public
    snapshotId: string;                  // visibility: public
    snapshotCreatedAt: string;           // ISO 8601 Instant, visibility: public
    addedCount: number;                  // visibility: public
    removedCount: number;                // visibility: public
    modifiedCount: number;               // visibility: public
    entities: EntityDiffResponse[];      // visibility: public
  }

  interface EntityDiffResponse {
    naturalKey: string;                  // visibility: public
    diffType: string;                    // "ADDED" | "REMOVED" | "MODIFIED", visibility: public
    fieldDiffs: FieldDiffResponse[];     // visibility: public
  }

  interface FieldDiffResponse {
    fieldName: string;                   // visibility: public
    oldValue: any;                       // visibility: public
    newValue: any;                       // visibility: public
  }
  ```
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|-------------|-------------|-----------------|
  | `SYS_004` | 404 | Domain not registered | `toast` |
  | `SYS_019` | 404 | Snapshot not found | `toast` |

---

### POST `/api/v1/configs/snapshots/{snapshotId}/rollback`

- **Auth**: `required` (Header: `X-User-Id`)
- **FR**: FR-010, FR-014
- **Description**: Rollback a domain to a historical snapshot state with conflict detection.
- **Request**:
  ```typescript
  // Path params: snapshotId: string
  interface RollbackRequest {
    forceOverwrite?: boolean;  // default: false, visibility: public
  }
  ```
- **Response (200)**:
  ```typescript
  // Wrapped: ApiResponse<RollbackResponse>
  interface RollbackResponse {
    domainName: string;                  // visibility: public
    snapshotId: string;                  // visibility: public
    milestoneId?: string;                // visibility: public
    recordsRestored: number;             // visibility: public
    conflictsOverridden: string[];       // visibility: public
    executedAt: string;                  // ISO 8601 Instant, visibility: public
  }
  ```
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|-------------|-------------|-----------------|
  | `SYS_019` | 404 | Snapshot not found | `toast` |
  | `SYS_020` | 409 | Rollback conflict — newer modifications exist | `modal` |

---

### GET `/api/v1/configs/domains/{domainName}/export`

- **Auth**: `required`
- **FR**: FR-002, FR-003
- **Description**: Export a single domain's configuration data in JSON format.
- **Request**:
  ```typescript
  // Path params: domainName: string
  // Query params:
  interface ExportDomainParams {
    format?: string;           // "json" (default), visibility: public
  }
  ```
- **Response (200)**:
  ```typescript
  // Direct file download — NOT wrapped in ApiResponse
  // Content-Type: application/json
  // Content-Disposition: attachment; filename="<domainName>_export.json"
  // Body: JSON array of domain entity records
  ```
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|-------------|-------------|-----------------|
  | `SYS_004` | 404 | Domain not registered | `toast` |

---

### GET `/api/v1/configs/export/all`

- **Auth**: `required`
- **FR**: FR-004
- **Description**: Export ALL registered configuration domains into a single multi-sheet Excel file.
- **Request**: *None*
- **Response (200)**:
  ```typescript
  // Direct file download — NOT wrapped in ApiResponse
  // Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  // Content-Disposition: attachment; filename="all_configurations_backup.xlsx"
  // Body: Excel workbook with one sheet per domain
  ```
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|-------------|-------------|-----------------|
  | `SYS_001` | 500 | No domains configured for multi-sheet export | `toast` |

---

### POST `/api/v1/configs/import`

- **Auth**: `required` (Header: `X-User-Id`)
- **FR**: FR-005, FR-006, FR-007
- **Description**: Import configuration data from a JSON file with policy-driven strategy and optional SHA-256 checksum verification.
- **Request**:
  ```typescript
  // Content-Type: multipart/form-data
  interface ImportRequest {
    file: File;                // JSON file, required, visibility: public
    mode?: string;             // "TRUNCATE_AND_LOAD" | "DELETE_AND_INSERT" | "UPSERT_MERGE" | "PATCH_VALUES"
                               // default: "UPSERT_MERGE", visibility: public
    checksum?: string;         // SHA-256 hex digest for integrity verification, visibility: public
  }
  ```
- **Response (200)**:
  ```typescript
  // Wrapped: ApiResponse<ImportSummaryResponse>
  interface ImportSummaryResponse {
    totalRecordsProcessed: number;       // visibility: public
    tablesProcessed: string[];           // visibility: public
    mode: string;                        // visibility: public
    success: boolean;                    // visibility: public
    message: string;                     // visibility: public
  }
  ```
- **Errors**:
  | Code | HTTP Status | Description | Frontend Action |
  |------|-------------|-------------|-----------------|
  | `SYS_021` | 400 | Import data validation failed (empty file, parse error) | `toast` |
  | `SYS_022` | 409 | Circular dependency among import tables | `modal` |
  | `SYS_023` | 400 | SHA-256 checksum mismatch | `modal` |

---

## Shared Types

### ImportStrategyMode (Enum)

```typescript
type ImportStrategyMode =
  | "TRUNCATE_AND_LOAD"   // Clean slate — truncate all + insert all
  | "DELETE_AND_INSERT"   // Scoped delete + re-insert (preserves sequence)
  | "UPSERT_MERGE"       // Match by key → update existing, insert new
  | "PATCH_VALUES";       // Match by key → update non-null fields only, no inserts
```

### DiffType (Enum)

```typescript
type DiffType = "ADDED" | "REMOVED" | "MODIFIED";
```

### MilestoneStatus (Enum)

```typescript
type MilestoneStatus = "ACTIVE" | "ARCHIVED";
```

---

## State Contracts

### ConfigManagement State (Frontend)

```typescript
interface ConfigManagementState {
  // Milestone management
  milestones: MilestoneResponse[];
  selectedMilestone: MilestoneResponse | null;
  milestonesLoading: boolean;

  // Diff comparison
  currentDiff: ConfigDiffResponse | null;
  diffLoading: boolean;

  // Export status
  exportInProgress: boolean;
  exportFormat: "json" | "xlsx";

  // Import status
  importInProgress: boolean;
  importResult: ImportSummaryResponse | null;
  importMode: ImportStrategyMode;
}
```

---

## Pagination Contract

> Not applicable for this change. All endpoints return full lists or file downloads.

---

## Error Code Summary (This Change)

| Code | HTTP Status | Description | Frontend Action | Endpoint(s) |
|------|-------------|-------------|-----------------|-------------|
| `SYS_001` | 500 | General system error | `toast` | All |
| `SYS_004` | 404 | Resource not found | `toast` | GET milestones/{id}, GET domains/{domainName}/export, GET domains/{domainName}/diff |
| `SYS_019` | 404 | Snapshot not found | `toast` | GET snapshots/{id}, GET domains/{domainName}/diff, POST rollback |
| `SYS_020` | 409 | Rollback conflict | `modal` | POST rollback |
| `SYS_021` | 400 | Import validation failed | `toast` | POST import |
| `SYS_022` | 409 | Circular dependency detected | `modal` | POST import |
| `SYS_023` | 400 | Checksum mismatch | `modal` | POST import |

---

## Contract Rules

1. **Bi-directional sync**: If frontend needs additional fields → update this contract → notify backend
2. **Version bump**: Any breaking change MUST increment `contract-version`
3. **Status tracking**: Each track updates its status header after implementation
4. **Visibility enforcement**: Frontend generators MUST filter out `visibility: internal` fields
5. **Error handling**: Every error code MUST have a corresponding frontend action
6. **File downloads**: Export endpoints return direct file downloads, NOT wrapped in `ApiResponse<T>`
7. **Multipart upload**: Import endpoint uses `multipart/form-data`, NOT JSON body

---

## Validation Checklist

- [x] Every endpoint has Request + Response + Errors defined
- [x] All fields have visibility annotation
- [x] Response wrapper format matches backend implementation
- [x] Error codes are unique and documented
- [x] State contract matches endpoint response shape
- [ ] OpenAPI spec link is valid (N/A — not yet generated)
