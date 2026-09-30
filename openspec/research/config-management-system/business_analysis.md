# Business Analysis: Configuration Management System

**Date:** 2026-09-30
**Feature:** Export/Import, Audit Trail, Snapshot/Rollback cho cấu hình đa domain

---

## 1. Business Context

### 1.1 Vấn đề hiện tại

- Cấu hình (menu, config, i18n, department) khi thay đổi KHÔNG có lịch sử → không biết ai thay đổi gì, khi nào
- Khi migrate giữa các môi trường (dev → staging → prod), phải thao tác thủ công → sai sót, mất thời gian
- Không có cơ chế rollback → khi cấu hình sai, phải sửa lại thủ công từng record
- Không thể nhóm nhiều thay đổi thành 1 "release" → khó quản lý khi có nhiều thay đổi đồng thời
- i18n messages hiện tại hardcode trong file → mỗi lần thay đổi phải build + deploy lại

### 1.2 Giá trị mong đợi

| Giá trị | Đo lường |
|---------|----------|
| Giảm thời gian migrate config giữa môi trường | Từ ~2h thủ công → <5 phút (export + import) |
| Giảm rủi ro config sai | Rollback trong <30 giây vs fix thủ công ~30 phút |
| Tăng accountability | 100% thay đổi config có audit trail |
| Tăng tốc i18n update | Không cần deploy để thay đổi message |
| Tạo mốc release cho config | Named snapshots + compare |

---

## 2. Use Cases

### UC-001: Export cấu hình

**Actor:** System Admin
**Precondition:** Đã đăng nhập, có quyền admin
**Semantic:** Admin cần xuất cấu hình hiện tại ra file để backup hoặc migrate sang môi trường khác

**Basic Flow:**
1. Admin chọn domain cấu hình cần export (Menu / Config / i18n / Department / All)
2. Admin chọn format (Excel / CSV / JSON)
3. Hệ thống sinh file chứa toàn bộ dữ liệu cấu hình + metadata (export date, version, checksum)
4. Admin download file

**Exception Flow:**
- E1: Dataset quá lớn (>50K records) → hệ thống sinh file async, thông báo khi hoàn tất
- E2: Admin không có quyền trên domain → HTTP 403

**Business Rules:**
- BR-EXP-01: File Excel phải có multi-sheet (1 sheet per sub-domain)
- BR-EXP-02: Encrypted config values PHẢI được mask trong export (`***ENCRYPTED***`)
- BR-EXP-03: Export file phải có Metadata sheet (ngày, domain, record counts, checksum)

---

### UC-002: Import cấu hình

**Actor:** System Admin
**Precondition:** Có file export hợp lệ
**Semantic:** Admin cần import cấu hình từ file (backup restore hoặc migrate từ môi trường khác)

**Basic Flow:**
1. Admin upload file (Excel / CSV / JSON)
2. Hệ thống validate file:
   - Schema check (column names, data types)
   - Reference check (FK, parent IDs tồn tại)
   - Uniqueness check (không duplicate codes)
   - Business rules check
3. Hệ thống hiển thị preview: danh sách thay đổi (new / updated / deleted / unchanged)
4. Admin xác nhận
5. Hệ thống tự động tạo snapshot "Pre-Import Backup" trước khi apply
6. Hệ thống apply import (batch upsert)
7. Hệ thống invalidate caches
8. Hiển thị import summary

**Exception Flow:**
- E1: File format không hợp lệ → HTTP 400 + error details
- E2: Validation failed → trả lại danh sách lỗi (row, column, error message)
- E3: Import failed mid-way → transaction rollback + restore from auto-snapshot
- E4: Encrypted fields trong import file → skip hoặc re-encrypt

**Business Rules:**
- BR-IMP-01: LUÔN tạo auto-snapshot trước khi import
- BR-IMP-02: Import mode: MERGE (upsert) — không xóa records không có trong file
- BR-IMP-03: Hỗ trợ option REPLACE_ALL — xóa tất cả rồi import mới (cần confirm 2 lần)
- BR-IMP-04: Max file size: 10MB

---

### UC-003: Xem lịch sử thay đổi (Audit Trail)

**Actor:** System Admin
**Precondition:** Config domain có thay đổi

**Basic Flow:**
1. Admin mở config management (Menu / Config / i18n / Department)
2. Admin chọn 1 record → click "View History"
3. Hệ thống hiển thị N lần thay đổi gần nhất (default: 5)
4. Mỗi entry hiển thị: version, changed_by, changed_at, change_reason, field diff
5. Admin click vào 1 entry → xem chi tiết diff (old vs new, highlighted)

**Exception Flow:**
- E1: Record chưa có thay đổi → hiển thị "No changes recorded"
- E2: Cần xem nhiều hơn 5 → pagination

**Business Rules:**
- BR-AUD-01: Mặc định hiển thị 5 lần thay đổi gần nhất
- BR-AUD-02: Diff hiển thị field-level (không hiển thị fields không thay đổi)
- BR-AUD-03: Sensitive fields (password, secret) phải mask trong diff

---

### UC-004: Tạo Snapshot (Mốc thay đổi)

**Actor:** System Admin
**Semantic:** Admin muốn nhóm nhiều thay đổi đã thực hiện thành 1 mốc có tên để dễ quản lý và rollback

**Basic Flow:**
1. Admin click "Create Snapshot"
2. Nhập: name (required), description (optional), config_domain (optional — NULL = all domains)
3. Hệ thống tạo snapshot với status = DRAFT
4. Hệ thống tự động gán các thay đổi chưa thuộc snapshot nào (orphan changes) vào snapshot mới
5. Admin có thể review danh sách changes trong snapshot
6. Admin click "Commit" → snapshot freeze (immutable)

**Exception Flow:**
- E1: Tên snapshot đã tồn tại → HTTP 409 Conflict
- E2: Không có changes để gán → snapshot tạo trống (vẫn valid)

**Business Rules:**
- BR-SNP-01: Snapshot name UNIQUE per domain_id
- BR-SNP-02: Chỉ snapshot DRAFT mới cho phép thêm/xóa changes
- BR-SNP-03: Commit → tính change_count, lưu rollback_snapshot cho mỗi change detail

---

### UC-005: Rollback Snapshot

**Actor:** System Admin
**Semantic:** Admin muốn revert tất cả thay đổi của 1 snapshot (hoàn tác)

**Basic Flow:**
1. Admin chọn snapshot (status = APPLIED hoặc COMMITTED)
2. Admin click "Rollback"
3. Hệ thống hiển thị preview: danh sách records sẽ bị revert + trạng thái trước
4. Admin confirm rollback
5. Hệ thống restore entity states từ entity_snapshot (theo thứ tự ngược)
6. Cập nhật snapshot status = ROLLED_BACK
7. Invalidate affected caches
8. Ghi lại rollback action vào change history

**Exception Flow:**
- E1: Snapshot đã ROLLED_BACK → HTTP 409
- E2: Entity đã bị xóa physically → skip + warn
- E3: Entity đã có changes mới sau snapshot → warn conflict, yêu cầu confirm force rollback

**Business Rules:**
- BR-RB-01: Rollback là atomic — all or nothing
- BR-RB-02: Rollback ghi vào change_history (operation = 'ROLLBACK')
- BR-RB-03: Rollback invalidate ALL caches cho affected domains
- BR-RB-04: Hỗ trợ "partial rollback" — chọn individual changes trong snapshot

---

### UC-006: CRUD i18n Messages

**Actor:** System Admin
**Semantic:** Admin quản lý message đa ngôn ngữ từ database, không cần deploy lại application

**Basic Flow:**
1. Admin mở i18n Management
2. Hiển thị danh sách messages (filter by group, locale, search by key)
3. Admin thêm/sửa/xóa message
4. Mỗi thay đổi tự động ghi vào config_change_history
5. Frontend tự động nhận messages mới (via cache refresh)

**Business Rules:**
- BR-I18N-01: message_key format: `section.subsection.field` (dot notation)
- BR-I18N-02: Supported locales: vi, en, ko (extensible)
- BR-I18N-03: message_group: ERROR, LABEL, NOTIFICATION, VALIDATION, MENU
- BR-I18N-04: Fallback: nếu locale không có message → fallback sang 'en'
- BR-I18N-05: Export i18n: pivot by locale (1 row per key, 1 column per locale)

---

### UC-007: CRUD System Configs

**Actor:** System Admin
**Semantic:** Admin quản lý cấu hình hệ thống chung (key-value)

**Business Rules:**
- BR-CFG-01: config_code UNIQUE per domain_id
- BR-CFG-02: value_type: STRING, NUMBER, BOOLEAN, JSON
- BR-CFG-03: is_encrypted = true → value lưu encrypted, hiển thị masked trên UI
- BR-CFG-04: config_group: GENERAL, SECURITY, EMAIL, SMS, PAYMENT, NOTIFICATION

---

## 3. Traceability Matrix

| Use Case | Config Domain | Export | Import | Audit | Snapshot | Rollback |
|----------|--------------|:-----:|:------:|:-----:|:--------:|:--------:|
| UC-001 | All | ✅ | — | — | — | — |
| UC-002 | All | — | ✅ | — | ✅ (auto) | — |
| UC-003 | All | — | — | ✅ | — | — |
| UC-004 | All | — | — | ✅ | ✅ | — |
| UC-005 | All | — | — | ✅ | — | ✅ |
| UC-006 | i18n | ✅ | ✅ | ✅ | ✅ | ✅ |
| UC-007 | System Config | ✅ | ✅ | ✅ | ✅ | ✅ |
