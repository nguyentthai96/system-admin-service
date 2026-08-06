# Business Analysis: Gradle 9 Compatibility Fix

## 1. Tổng quan

### 1.1 Semantic Description
Service `system-admin-service` không thể build trên Gradle 9.4.1 do plugin versions quá cũ, gây gián đoạn phát triển và deploy pipeline.

### 1.2 Tại sao cần fix
- **Build bị block hoàn toàn** — không thể compile, test, hay deploy
- **Không align với các service khác** — auth-service, account-service đã chạy trên Gradle 9 + Spring Boot 4.1
- **Technical debt tích lũy** — Spring Boot 3.2 đã EOL (Dec 2024), Kotlin 1.9.20 đã quá cũ

---

## 2. Use Cases

### UC-01: Build system-admin-service trên Gradle 9.4.1

**Actor:** Developer / CI/CD Pipeline

**Pre-conditions:**
- Gradle wrapper 9.4.1 đã cấu hình
- Convention plugin `build-logic` đã publish vào mavenLocal
- Version catalog đã publish vào mavenLocal

**Basic Flow:**
1. Developer chạy `./gradlew build`
2. Gradle resolve plugins từ convention plugin `ntt.spring-app-conventions`
3. Kotlin Gradle Plugin 2.4.10 compile Kotlin source
4. Spring Boot 4.1.0 package application
5. Build thành công

**Exception Flow:**
- E1: `com.ntt:platform` chưa publish → `Could not resolve` error → Cần publish base-core trước
- E2: Source code dùng deprecated API → Compilation error → Cần update source code

**Post-conditions:**
- JAR file được tạo tại `build/libs/`
- Tests passed (nếu có)

---

## 3. Traceability Matrix

| Requirement | Source | Implementation |
|------------|--------|---------------|
| Fix HasConvention error | Build output error | Plugin version upgrade |
| Align với service khác | Architecture decision | Convention plugin migration |
| Remove deprecated APIs | Kotlin 2.x migration | kotlinOptions → compilerOptions |
| Fix foojay incompatibility | Build output error | foojay 0.9.0 → 1.0.0 |

---

## 4. Business Rules

| # | Rule | Justification |
|---|------|---------------|
| BR-01 | Tất cả services phải dùng convention plugins | Single source of truth |
| BR-02 | Version catalog là nguồn duy nhất cho dependency versions | Tránh version drift |
| BR-03 | Không hardcode plugin versions trong service build files | Convention plugin quản lý |
| BR-04 | Base-core phải được publish trước khi build service | Dependency chain |

---

## 5. Impact Assessment

### Rủi ro
- **Thấp**: Service hiện tại rất đơn giản, ít code → ít breaking changes
- **Trung bình**: Spring Boot 3.2 → 4.1 là major upgrade nhưng không có code sử dụng breaking APIs

### Lợi ích
- Build hoạt động trở lại
- Align 100% với monorepo standards
- Dễ maintain — future updates chỉ cần update version catalog
- Nhận được tất cả fixes và improvements từ Spring Boot 4.1, Kotlin 2.4.10
