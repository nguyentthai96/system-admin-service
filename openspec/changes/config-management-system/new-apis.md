# New APIs — Configuration Management System

## Milestone & Snapshot Endpoints

| Method | Endpoint | Description | Request Payload | Response Payload |
|---|---|---|---|---|
| `POST` | `/api/v1/configs/milestones` | Tạo milestone gom snapshot đa miền cấu hình (FR-009) | `CreateMilestoneRequest` (`name`, `description`, `domainNames`) | `MilestoneResponse` |
| `GET` | `/api/v1/configs/milestones` | Danh sách lịch sử milestones | None | `List<MilestoneResponse>` |
| `GET` | `/api/v1/configs/milestones/{id}` | Chi tiết milestone và các snapshot thành phần | None | `MilestoneResponse` |
| `GET` | `/api/v1/configs/snapshots/{id}` | Chi tiết metadata snapshot miền | None | `SnapshotSummaryResponse` |

## Diff & Rollback Endpoints

| Method | Endpoint | Description | Query/Body | Response Payload |
|---|---|---|---|---|
| `GET` | `/api/v1/configs/domains/{domainName}/diff` | So sánh khác biệt giữa trạng thái hiện tại và snapshot (FR-010) | Query: `snapshotId` | `ConfigDiffResponse` (added, removed, modified fields) |
| `POST` | `/api/v1/configs/snapshots/{snapshotId}/rollback` | Rollback trạng thái miền về snapshot với cảnh báo xung đột (FR-010, FR-014) | Body: `RollbackRequest` (`forceOverwrite: boolean`) | `RollbackResponse` (recordsRestored, conflictsOverridden) |

## Export & Import Endpoints

| Method | Endpoint | Description | Params / Body | Content-Type |
|---|---|---|---|---|
| `GET` | `/api/v1/configs/domains/{domainName}/export` | Export cấu hình miền ra file JSON / Excel đơn bảng (FR-002, FR-003) | Query: `format` (`json`, `excel`, `csv`) | `application/json` hoặc `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `GET` | `/api/v1/configs/export/all` | Export toàn bộ cấu hình tất cả các miền ra 1 file Excel Multi-Sheet (FR-004) | None | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `POST` | `/api/v1/configs/import` | Import cấu hình theo cơ chế linh hoạt 3 chế độ (FR-005, FR-006, FR-007) | Multipart: `file`, Query: `mode` (`UPSERT_MERGE`, `DELETE_AND_INSERT`, `TRUNCATE_AND_LOAD`) | `ImportSummaryResponse` |
