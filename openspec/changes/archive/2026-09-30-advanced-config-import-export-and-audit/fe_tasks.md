# Frontend Tasks: advanced-config-import-export-and-audit

<!-- generated-by: wf_fe_spec -->
<!-- contract-version: 1.0 -->
<!-- complexity: MEDIUM -->
<!-- estimated-tasks: 7 -->
<!-- input-mode: C (Memory-Enriched) -->
<!-- backend-status: implemented -->

---

## Task 1: TypeScript Types (from Contract)

- **File**: `src/types/config-versioning.types.ts` | Action: **[NEW]**
- **Contract Source**: `api_contract.md` → §Endpoints + §Shared Types + §State Contracts
- **Visibility Rule**: Only `visibility: public` fields → TypeScript interfaces
- **Pattern**: Follow [`admin-config.types.ts`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/frontends/admindashboard/src/types/admin-config.types.ts) convention

### Types to Generate

```typescript
// From POST /api/v1/configs/milestones → Request
interface CreateMilestoneRequest {
  name: string;
  description?: string;
  domainNames?: string[];
}

// From POST /api/v1/configs/snapshots/{snapshotId}/rollback → Request
interface RollbackRequest {
  forceOverwrite?: boolean;
}

// From POST /api/v1/configs/milestones → Response
interface MilestoneResponse {
  id: string;
  name: string;
  description?: string;
  createdBy?: string;
  createdAt: string;
  status: MilestoneStatus;
  snapshots: SnapshotSummaryResponse[];
}

// From GET /api/v1/configs/snapshots/{id} → Response
interface SnapshotSummaryResponse {
  id: string;
  domainName: string;
  checksumSha256: string;
  recordCount: number;
  createdAt: string;
}

// From GET /api/v1/configs/domains/{domainName}/diff → Response
interface ConfigDiffResponse {
  domainName: string;
  snapshotId: string;
  snapshotCreatedAt: string;
  addedCount: number;
  removedCount: number;
  modifiedCount: number;
  entities: EntityDiffResponse[];
}

interface EntityDiffResponse {
  naturalKey: string;
  diffType: DiffType;
  fieldDiffs: FieldDiffResponse[];
}

interface FieldDiffResponse {
  fieldName: string;
  oldValue: any;
  newValue: any;
}

// From POST /api/v1/configs/snapshots/{snapshotId}/rollback → Response
interface RollbackResponse {
  domainName: string;
  snapshotId: string;
  milestoneId?: string;
  recordsRestored: number;
  conflictsOverridden: string[];
  executedAt: string;
}

// From POST /api/v1/configs/import → Response
interface ImportSummaryResponse {
  totalRecordsProcessed: number;
  tablesProcessed: string[];
  mode: string;
  success: boolean;
  message: string;
}

// Enums
type ImportStrategyMode = 'TRUNCATE_AND_LOAD' | 'DELETE_AND_INSERT' | 'UPSERT_MERGE' | 'PATCH_VALUES';
type DiffType = 'ADDED' | 'REMOVED' | 'MODIFIED';
type MilestoneStatus = 'ACTIVE' | 'ARCHIVED';
```

### Validation Criteria
- [x] All `visibility: public` fields from contract included
- [x] Zero `visibility: internal` fields present
- [x] Matches `admin-config.types.ts` naming convention (PascalCase interfaces)
- [x] Export all types with `export` keyword

---

## Task 2: API Service Layer (Dual-Mode)

- **File**: `src/services/config-versioning.service.ts` | Action: **[NEW]**
- **Contract Source**: `api_contract.md` → §Endpoints (all 9)
- **Depends**: Task 1 (types)
- **Pattern**: Follow [`admin-config.service.ts`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/frontends/admindashboard/src/services/admin-config.service.ts) dual-mode pattern
- **Skills Applied**:
  - `vercel-react-best-practices` → `async-parallel` (parallel milestone+snapshot fetching)
  - `vercel-react-best-practices` → `client-swr-dedup` (TanStack Query dedup via hook)

### Methods to Generate

| # | Method | HTTP | Path | Notes |
|---|--------|------|------|-------|
| 1 | `createMilestone(data)` | POST | `/api/v1/configs/milestones` | JSON body |
| 2 | `listMilestones()` | GET | `/api/v1/configs/milestones` | — |
| 3 | `getMilestone(id)` | GET | `/api/v1/configs/milestones/{id}` | — |
| 4 | `getSnapshot(id)` | GET | `/api/v1/configs/snapshots/{id}` | — |
| 5 | `getDomainDiff(domainName, snapshotId)` | GET | `/api/v1/configs/domains/{domainName}/diff` | Query param |
| 6 | `rollbackSnapshot(snapshotId, data?)` | POST | `/api/v1/configs/snapshots/{snapshotId}/rollback` | Optional JSON body |
| 7 | `exportDomain(domainName, format?)` | GET | `/api/v1/configs/domains/{domainName}/export` | **File download** — use `window.open()` or Blob |
| 8 | `exportAllDomains()` | GET | `/api/v1/configs/export/all` | **File download** — Excel Blob |
| 9 | `importConfig(file, mode, checksum?)` | POST | `/api/v1/configs/import` | **multipart/form-data** |

### Implementation Notes
- Methods 7 & 8: File download — use `api.get(...).blob()` → `URL.createObjectURL()` → trigger download
- Method 9: Use `FormData` for multipart upload: `api.post('configs/import', { body: formData })`
- Response unwrap: `ApiResponse<T>` → return `T` directly
- Error map: Contract error codes → typed error messages
- `useMock` toggle: `import.meta.env.VITE_USE_MOCK_API === 'true'`

### Validation Criteria
- [x] All 9 endpoints mapped to service methods
- [x] Dual-mode (real + mock) pattern matches `admin-config.service.ts`
- [x] File download methods trigger browser download (Blob + anchor click)
- [x] Import method uses `FormData` for multipart upload
- [x] Mock data provides realistic sample milestones, snapshots, diff

---

## Task 3: Custom Hook

- **File**: `src/hooks/useConfigVersioning.ts` | Action: **[NEW]**
- **Depends**: Task 2 (service layer)
- **Contract Source**: `api_contract.md` → §State Contracts → `ConfigManagementState`
- **Pattern**: Follow [`useAdminConfig.ts`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/frontends/admindashboard/src/hooks/useAdminConfig.ts) TanStack Query pattern
- **Skills Applied**:
  - `vercel-react-best-practices` → `rerender-*` (memo, avoid unnecessary re-renders)
  - `vercel-react-best-practices` → `client-swr-dedup` (query key factory)

### Hook API Design

```typescript
export function useConfigVersioning() {
  // Queries
  const milestonesQuery;          // listMilestones() — staleTime: 5min
  const selectedMilestoneQuery;   // getMilestone(id) — enabled: !!selectedId
  const diffQuery;                // getDomainDiff() — enabled: !!snapshotId

  // Mutations
  const createMilestone;          // → invalidate milestones
  const rollbackSnapshot;         // → invalidate milestones + show result modal
  const importConfig;             // → show ImportSummaryResponse toast
  const exportDomain;             // → trigger file download
  const exportAllDomains;         // → trigger Excel download

  // State
  const [selectedMilestoneId, setSelectedMilestoneId];
  const [importMode, setImportMode];

  return { ... };
}
```

### Query Key Factory

```typescript
const CONFIG_VERSIONING_KEYS = {
  milestones: ['config-milestones'] as const,
  milestone: (id: string) => ['config-milestone', id] as const,
  snapshot: (id: string) => ['config-snapshot', id] as const,
  diff: (domain: string, snapshotId: string) => ['config-diff', domain, snapshotId] as const,
};
```

### Validation Criteria
- [x] All 9 service methods exposed via hook
- [x] TanStack Query key factory matches convention
- [x] Mutations invalidate correct query keys
- [x] File download mutations don't use TanStack Query (use direct async calls)
- [x] `staleTime` and `enabled` guards follow `useAdminConfig.ts` pattern

---

## Task 4: Page Component

- **File**: `src/app/(control-panel)/admin/settings/config-versioning/AdminConfigVersioningPage.tsx` | Action: **[NEW]**
- **Depends**: Task 3 (hook)
- **Contract Source**: All endpoints — unified versioning management page
- **Skills Applied**:
  - `vercel-react-best-practices` → `bundle-dynamic-imports` (lazy loading)
  - `vercel-react-best-practices` → `rendering-*` rules

### Page Layout (MUI)

```
┌────────────────────────────────────────────────────┐
│ Page Header: "Configuration Versioning"             │
│ [Create Milestone] [Export All ↓] [Import ↑]       │
├──────────────────────┬─────────────────────────────┤
│ Milestone Timeline   │ Detail Panel                │
│ (list + search)      │ ┌─────────────────────────┐ │
│                      │ │ Milestone Info           │ │
│ ● v2.1 - 2026-09    │ │ Snapshots Table          │ │
│ ● v2.0 - 2026-08    │ │ [View Diff] [Rollback]   │ │
│ ● v1.0 - 2026-07    │ └─────────────────────────┘ │
│                      │ ┌─────────────────────────┐ │
│                      │ │ Diff Viewer (accordion)  │ │
│                      │ │ ▸ ADDED (3)             │ │
│                      │ │ ▸ REMOVED (1)           │ │
│                      │ │ ▸ MODIFIED (5)          │ │
│                      │ └─────────────────────────┘ │
└──────────────────────┴─────────────────────────────┘
```

### Sub-Components (inline or split)

| Component | Purpose | Contract Source |
|-----------|---------|----------------|
| `MilestoneTimeline` | Left panel — clickable milestone list | `MilestoneResponse[]` |
| `MilestoneDetailPanel` | Right panel — snapshots + actions | `MilestoneResponse` + `SnapshotSummaryResponse[]` |
| `DiffViewer` | Accordion showing entity-level diffs | `ConfigDiffResponse` |
| `ImportDialog` | Modal — file upload + mode select + checksum | `POST /import` request spec |
| `RollbackConfirmDialog` | Modal — force override warning | `RollbackRequest` + `RollbackResponse` |

### Validation Criteria
- [x] Page renders milestone timeline + detail panel
- [x] Import dialog supports 4 modes (TRUNCATE_AND_LOAD, DELETE_AND_INSERT, UPSERT_MERGE, PATCH_VALUES)
- [x] Export buttons trigger file downloads
- [x] Diff viewer shows ADDED/REMOVED/MODIFIED with field-level diffs
- [x] Rollback shows confirmation dialog with conflict warnings
- [x] Loading/error states handled for all queries
- [x] MUI components used consistently (Paper, Grid, Typography, Dialog, Accordion)

---

## Task 5: Route Registration

- **File**: `src/app/(control-panel)/admin/route.tsx` | Action: **[MODIFY]**
- **Depends**: Task 4 (page component)
- **Pattern**: Follow existing route pattern in [`route.tsx`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/frontends/admindashboard/src/app/%28control-panel%29/admin/route.tsx)

### Changes

```diff
+const AdminConfigVersioningPage = lazy(() => import('./settings/config-versioning/AdminConfigVersioningPage'));

 const route: CoreRouteItemType = {
   children: [
     // ... existing routes ...
+    {
+      path: 'admin/settings/versioning',
+      element: <AdminConfigVersioningPage />,
+      auth: authRoles.admin,
+    },
   ],
 };
```

### Validation Criteria
- [x] Lazy import added
- [x] Route path follows existing convention (`admin/settings/versioning`)
- [x] Auth set to `authRoles.admin`
- [x] Existing routes untouched

---

## Task 6: Unit Tests

- **File**: `src/services/__tests__/config-versioning.service.test.ts` | Action: **[NEW]**
- **File**: `src/hooks/__tests__/useConfigVersioning.test.ts` | Action: **[NEW]**
- **Depends**: Task 2, Task 3
- **Framework**: Vitest + JSDOM
- **Skills Applied**: `vercel-react-best-practices` → test patterns

### Test Cases

#### Service Tests
- [x] `createMilestone` sends correct POST body
- [x] `listMilestones` returns array of MilestoneResponse
- [x] `getDomainDiff` passes snapshotId as query param
- [x] `importConfig` sends FormData with file + mode
- [x] `exportAllDomains` returns Blob
- [x] Mock mode returns mock data when `VITE_USE_MOCK_API=true`

#### Hook Tests
- [x] `useConfigVersioning` returns milestones query data
- [x] `createMilestone` mutation invalidates milestones query
- [x] `importConfig` mutation handles checksum mismatch error (SYS_023)
- [x] `rollbackSnapshot` shows conflict error for SYS_020

---

## Task 7: UI Review Audit (Post-Implementation)

- **Type**: REVIEW (not code generation)
- **Depends**: Tasks 1-6 complete
- **Skill**: `ui-review` → full checklist
- **Output**: `fe_review.md` with verdict

### Review Checklist
- [x] MUI theme consistency
- [x] Responsive layout (mobile-friendly)
- [x] Loading skeletons vs spinners
- [x] Error boundary handling
- [x] Accessibility (ARIA labels on dialogs)
- [x] File download UX (progress indicator)
- [x] Import dialog validation feedback

---

## Error Code → Frontend Action Mapping

| Code | HTTP | Frontend Action | Implementation |
|------|------|-----------------|----------------|
| `SYS_001` | 500 | `toast` | `enqueueSnackbar(message, { variant: 'error' })` |
| `SYS_004` | 404 | `toast` | `enqueueSnackbar('Not found', { variant: 'warning' })` |
| `SYS_019` | 404 | `toast` | `enqueueSnackbar('Snapshot not found', { variant: 'warning' })` |
| `SYS_020` | 409 | `modal` | Open `RollbackConfirmDialog` with `forceOverwrite` option |
| `SYS_021` | 400 | `toast` | `enqueueSnackbar('Import validation failed', { variant: 'error' })` |
| `SYS_022` | 409 | `modal` | Show circular dependency details in dialog |
| `SYS_023` | 400 | `modal` | Show checksum mismatch dialog with expected/actual values |

---

## Dependency Graph

```
Task 1 (Types)
  └─→ Task 2 (Service)
        └─→ Task 3 (Hook)
              └─→ Task 4 (Page + Components)
                    └─→ Task 5 (Route Registration)
                          └─→ Task 6 (Unit Tests)
                                └─→ Task 7 (UI Review)
```
