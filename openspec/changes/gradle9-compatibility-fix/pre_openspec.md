# Pre-OpenSpec: gradle9-compatibility-fix

> **Type**: MAINTENANCE
> **Flow**: Command
> **Source**: URD (Research Documents — research_brief.md, technical_spec.md, comparison_analysis.md, validation_report.md, web_research.md)
> **Classification Evidence**: `HasConvention` → `build.gradle.kts` → `settings.gradle.kts` — Gradle 9 API removal breaks build
> **Archive**: N/A
> **Quality Score**: 88/100

## 📋 Feature Summary

`system-admin-service` build thất bại với Gradle 9.4.1 do `org.gradle.api.internal.HasConvention` bị xóa hoàn toàn trong Gradle 9.0. Các plugin cũ (Kotlin 1.9.20, Spring Boot 3.2.0, foojay 0.9.0) sử dụng Convention API gây `ClassNotFoundException`. Giải pháp: migrate sang convention plugin `ntt.spring-app-conventions` + version catalog `libs` để align với auth-service và account-service.

| Metric | Giá trị |
|--------|---------|
| Số FR | 9 (URD: 7, Enriched: 2) |
| Issues | 2 (🔴: 0, 🟡: 2) |
| Open Questions | 1 |
| **Quality Score** | **88/100** |

---

## 1. Actors

- **Developer**: Thực hiện migration build configuration
- **CI/CD System**: Build, test, deploy service sau khi fix
- **Build System (Gradle 9.4.1)**: Runtime build tool — cần compatible plugins

## 2. Functional Requirements

### FR-001: Loại bỏ Convention API dependency [URD]
- **Actor**: Build System
- **Action**: Hệ thống build phải sử dụng Extensions API thay vì Convention API khi chạy trên Gradle 9.4.1
- **Validation**: `./gradlew dependencies` không throw `ClassNotFoundException: HasConvention`

### FR-002: Nâng Kotlin plugin lên 2.x [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải sử dụng Kotlin Gradle Plugin ≥ 2.0.0 (khuyến nghị 2.4.10 qua version catalog)
- **Validation**: `kotlin("jvm")` version ≥ 2.0.0 — không dùng `HasConvention`

### FR-003: Nâng Spring Boot plugin lên 4.x [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải sử dụng Spring Boot plugin ≥ 4.0.0 (khuyến nghị 4.1.0 qua version catalog)
- **Validation**: Spring Boot plugin compatible với Gradle 9.x

### FR-004: Nâng foojay-resolver-convention lên 1.0.0 [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải update `foojay-resolver-convention` từ 0.9.0 lên 1.0.0 trong `settings.gradle.kts`
- **Validation**: Không sử dụng `FoojayToolchainsPlugin` và `JvmVendorSpec.IBM_SEMERU` đã bị xóa

### FR-005: Migrate sang convention plugin [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải thay thế toàn bộ hardcoded plugin declarations bằng `ntt.spring-app-conventions` để align với auth-service và account-service
- **Validation**: `build.gradle.kts` chỉ khai báo `id("ntt.spring-app-conventions")` — không hardcode versions

### FR-006: Xóa deprecated kotlinOptions DSL [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải xóa block `tasks.withType<KotlinCompile> { kotlinOptions { ... } }` vì convention plugin đã cấu hình `compilerOptions` tự động
- **Validation**: Không còn `kotlinOptions` block trong `build.gradle.kts`

### FR-007: Sử dụng version catalog [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải khai báo `versionCatalogs` trong `settings.gradle.kts` trỏ đến `com.ntt:version-catalog:0.0.1-SNAPSHOT` — single source of truth
- **Validation**: `libs` catalog available, `./gradlew dependencies` resolve đúng versions

### FR-008: Đảm bảo backward compatibility source code [ENRICHED]
- **Actor**: Developer
- **Action**: Hệ thống phải verify source code hiện tại compile thành công với Kotlin 2.4.10 + Spring Boot 4.1.0
- **Validation**: `./gradlew compileKotlin` pass (trừ base-core dependency chưa publish)

### FR-009: Giữ nguyên test configuration [ENRICHED]
- **Actor**: CI/CD System
- **Action**: Hệ thống phải giữ `tasks.withType<Test> { useJUnitPlatform() }` và test profile không bị ảnh hưởng
- **Validation**: `./gradlew test` pass (khi dependencies available)

## 3. Non-functional Requirements

- NFR-001: Build time không tăng đáng kể so với trước migration
- NFR-002: Align 100% cấu hình build với auth-service và account-service trong monorepo

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. FR-001 (HasConvention removal) là root cause — FR-002, FR-003, FR-004 là các sub-fixes cụ thể. FR-005 (convention plugin) là giải pháp bao trùm cho FR-002, FR-003, FR-006, FR-007.

## 5. Enriched Domain Requirements

2 enriched FRs đã thêm:

### Enriched FRs

- **FR-008** [ENRICHED]: Verify source code backward compatibility — cần đảm bảo Kotlin 2.x + Spring Boot 4.x không break code hiện tại
- **FR-009** [ENRICHED]: Giữ test configuration — đảm bảo test infrastructure không bị ảnh hưởng bởi plugin migration

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| mavenLocal | Resolve convention plugins + version catalog | `com.ntt:build-logic:0.0.1-SNAPSHOT` |
| Maven Central | Download Spring Boot, Kotlin, dependencies | Standard |
| base-core (mavenLocal) | base-web-starter, base-data-starter | Chưa publish — pre-existing issue |

## 6. Assumptions

- ⚠️ Assumption: Convention plugin `ntt.spring-app-conventions` đã được publish thành công vào mavenLocal (verified trong validation_report.md)
- ⚠️ Assumption: Version catalog `com.ntt:version-catalog:0.0.1-SNAPSHOT` available trong mavenLocal
- ⚠️ Assumption: Source code hiện tại (4 files Kotlin) đơn giản, không sử dụng API bị breaking change trong Spring Boot 3.2→4.1

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-008: "compile thành công" cần loại trừ rõ base-core dependency issue |
| Đầy đủ (Completeness) | 22/25 | FR-005: Thiếu chi tiết rollback plan nếu convention plugin fail |
| Nhất quán (Consistency) | 23/25 | FR-003: Spring Boot 3.2→4.1 là major jump, tiềm ẩn breaking changes chưa liệt kê hết |
| Kiểm thử được (Testability) | 20/25 | FR-008, FR-009: Full test phụ thuộc base-core publish — hiện không test được |
| **Tổng** | **88/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -2 | FR-008 | "compile thành công (trừ base-core)" — điều kiện loại trừ mơ hồ | Định nghĩa rõ scope verify: chỉ system-admin-service code, không tính external deps |
| 2 | Completeness | -3 | FR-005 | Research chỉ nêu "align với services khác" — thiếu rollback strategy | Thêm plan: nếu convention plugin fail → fallback Option B (manual upgrade) |
| 3 | Consistency | -2 | FR-003 | "Spring Boot 4.1.0" nhưng research liệt kê breaking changes (Jackson 3, Security 7) mà không map rõ impact | Xác nhận: service hiện tại không dùng Jackson advanced features hay Security |
| 4 | Testability | -5 | FR-008/009 | "base-core starters not published" — blocking full compile + integration test | Sau khi publish base-core → re-run full test suite |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | base-core `publishToMavenLocal` fails do `CommonCompilerArguments.getPluginClasspaths() is null` — block full compile verification | FR-008 | Fix base-core trước hoặc comment out base-core imports tạm thời |
| 2 | Risk | 🟡 | Spring Boot 3.2→4.1 major jump: Jackson 3, Security 7, JUnit 4 removed — chưa verify hết impact | FR-003 | Service rất đơn giản nên risk thấp, nhưng cần verify khi thêm features mới |

> Không phát hiện issues mức 🔴 (Critical).

## 9. Open Questions

- OQ-001: Khi nào base-core sẽ được fix và publish thành công? Cần để verify full compile + integration test.

> Ngoài ra không có câu hỏi mở — build configuration đã được verify thành công.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain

Build Infrastructure — Gradle plugin compatibility và build configuration migration

### 10.2 Flow Type

Command (single step — modify build files, run build)

### 10.3 Candidate Services
- **system-admin-service**: Service target — build.gradle.kts + settings.gradle.kts cần update

### Detection Evidence
- Keyword: `HasConvention`, `foojay-resolver-convention` → Module: `build.gradle.kts`, `settings.gradle.kts` → File: `build.gradle.kts` (line 1-3), `settings.gradle.kts` (line 31-33)

### 10.4 External Integrations

- **mavenLocal**: Convention plugin resolution (`com.ntt:build-logic:0.0.1-SNAPSHOT`)
- **Maven Central**: Dependency download
- **Version Catalog**: `com.ntt:version-catalog:0.0.1-SNAPSHOT`

### 10.5 Required Modules

- `build.gradle.kts` — plugin declaration + dependencies
- `settings.gradle.kts` — pluginManagement, dependencyResolutionManagement, foojay version

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Developer | Update `settings.gradle.kts` — foojay 0.9.0→1.0.0, add pluginManagement + versionCatalogs | Gradle |
| 2 | Developer | Replace plugin block in `build.gradle.kts` — dùng `ntt.spring-app-conventions` | Gradle |
| 3 | Developer | Xóa hardcoded config: java{}, repositories{}, kotlinOptions{}, dependencyManagement{} | Gradle |
| 4 | Developer | Add platform BOM dependency | Gradle |
| 5 | CI/CD | Run `./gradlew dependencies` → verify resolution | Gradle 9.4.1 |
| 6 | CI/CD | Run `./gradlew build` → verify compile + test | Gradle 9.4.1 |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class/File | Status |
|-------|-------------|-------------|---------------------|--------|
| FR-001 | research_brief §2 | technical_spec §2 | `build.gradle.kts` | [MODIFY] Mapped |
| FR-002 | research_brief §2, web_research §2 | technical_spec §3 | `build.gradle.kts` (plugin block) | [MODIFY] Mapped |
| FR-003 | research_brief §2, web_research §3 | technical_spec §3 | `build.gradle.kts` (plugin block) | [MODIFY] Mapped |
| FR-004 | research_brief §2, web_research §4 | technical_spec §2.1 | `settings.gradle.kts` | [MODIFY] Mapped |
| FR-005 | comparison_analysis §2 Option A | technical_spec §2.2 | `build.gradle.kts` | [MODIFY] Mapped |
| FR-006 | research_brief §2, web_research §2.2 | technical_spec §2.2 | `build.gradle.kts` (task block) | [MODIFY] Mapped |
| FR-007 | comparison_analysis §2 Option A | technical_spec §4 | `settings.gradle.kts` | [MODIFY] Mapped |
| FR-008 | validation_report §Test 3 | TBD | All `.kt` files | [REUSE] Pending |
| FR-009 | validation_report §Test 2 | TBD | `build.gradle.kts` (test block) | [REUSE] Pending |

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations

Feature này thuộc loại MAINTENANCE đơn giản — chỉ modify 2 build files, không thay đổi business logic. Service hiện tại rất nhỏ (4 files Kotlin, chưa có business logic phức tạp), nên risk migration rất thấp.

Đáng chú ý: `build.gradle.kts` và `settings.gradle.kts` **đã được update** trong working tree hiện tại (đã dùng convention plugin + foojay 1.0.0). Điều này cho thấy fix đã được apply — workflow này document lại decision và context cho traceability.

### Related Features / Precedents

- auth-service: Đã dùng `ntt.spring-app-conventions` + version catalog thành công
- account-service: Tương tự auth-service — cùng pattern
- Cả 2 service đều build thành công với Gradle 9.4.1

### Integration Notes

- **Convention plugin** (`ntt.spring-app-conventions`): Tự động apply Kotlin JVM, plugin.spring, Spring Boot, Spring DM, GraalVM Native, allOpen for JPA
- **Version catalog** (`libs`): Single source of truth — Kotlin 2.4.10, Spring Boot 4.1.0, Spring DM 1.1.7
- **base-core starters**: Chưa publish — `DefaultSessionManagement.kt` import `com.ntt.basecore.domain.session.SessionManagement` sẽ fail compile. Đây là pre-existing issue, không liên quan đến Gradle 9 fix.

### Suggested Approach

1. **Đã apply**: `build.gradle.kts` → `ntt.spring-app-conventions`, `settings.gradle.kts` → foojay 1.0.0 + pluginManagement + versionCatalogs
2. **Verify**: `./gradlew dependencies --configuration compileClasspath` → BUILD SUCCESSFUL
3. **Pending**: Full compile sẽ pass sau khi base-core publish thành công

### Context from Confluence Images

N/A — Không sử dụng Confluence cho feature này.
