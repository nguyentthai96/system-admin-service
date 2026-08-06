# Web Research: Gradle 9 HasConvention Removal & Plugin Compatibility

## 1. Tổng quan

### 1.1 HasConvention là gì?
`org.gradle.api.internal.HasConvention` là internal interface trong Gradle cung cấp cơ chế "Convention" — cách cũ để plugins chia sẻ cấu hình. Đã deprecated từ Gradle 8.2 và **xóa hoàn toàn trong Gradle 9.0**.

**Sources:**
- [Gradle Upgrading 8.x](https://docs.gradle.org/9.4.1/userguide/upgrading_version_8.html) — official deprecation docs
- [Gradle 9.0 Release Notes](https://docs.gradle.org/9.0/release-notes.html) — removal announcement

### 1.2 Thay thế: Extensions API
```kotlin
// ❌ CŨ (Convention)
project.convention.getPlugin(JavaPluginConvention::class.java)

// ✅ MỚI (Extensions)
project.extensions.getByType(JavaPluginExtension::class.java)
```

---

## 2. Kotlin Gradle Plugin Compatibility

### 2.1 Version Matrix

| KGP Version | Gradle 8.x | Gradle 9.x | HasConvention |
|------------|:----------:|:----------:|:-------------:|
| 1.9.20 | ✅ | ❌ | Sử dụng |
| 2.0.0 | ✅ | ✅ | Đã loại bỏ |
| 2.3.21 | ✅ | ✅ | Đã loại bỏ |
| **2.4.10** (latest) | ✅ | ✅ | Đã loại bỏ |

**Source:** [JetBrains KGP Compatibility](https://kotlinlang.org/docs/gradle-configure-project.html)

### 2.2 Migration: kotlinOptions → compilerOptions

```kotlin
// ❌ DEPRECATED (Kotlin 1.x style)
tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += "-Xjsr305=strict"
    }
}

// ✅ RECOMMENDED (Kotlin 2.x style)
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
```

**Source:** [Kotlin Compiler Options Migration](https://kotlinlang.org/docs/gradle-compiler-options.html)

---

## 3. Spring Boot Compatibility

### 3.1 Version Matrix

| Spring Boot | Gradle 7.x | Gradle 8.x | Gradle 9.x |
|------------|:----------:|:----------:|:----------:|
| 3.2.0 | ✅ | ✅ | ❌ |
| 3.4.x (EOL) | ✅ | ✅ | ❌ |
| 3.5.16 (EOL 06/2026) | — | ✅ (8.14+) | ⚠️ Not official |
| **4.0.x** | — | ✅ (8.14+) | ✅ |
| **4.1.0** (latest) | — | ✅ (8.14+) | ✅ |

**Source:** [Spring Boot System Requirements](https://docs.spring.io/spring-boot/reference/getting-started/system-requirements.html)

### 3.2 Breaking Changes (3.2 → 4.1)
- Jackson 3 upgrade (major)
- Modularization (70+ granular modules)
- Spring Security 7 (new defaults)
- JUnit 4 removed
- JSpecify null safety
- Kotlin ≥ 2.2 required
- Java ≥ 17

**Source:** [Spring Boot 4 Migration Guide (GitHub)](https://github.com/spring-projects/spring-boot)

---

## 4. foojay-resolver-convention

### 4.1 Version Matrix

| Version | Gradle 8.x | Gradle 9.x | Issue |
|---------|:----------:|:----------:|-------|
| 0.9.0 | ✅ | ❌ | `FoojayToolchainsPlugin`, `JvmVendorSpec.IBM_SEMERU` removed |
| **1.0.0** | ✅ | ✅ | Fixed all removed APIs |

**Source:** [foojay-toolchains GitHub](https://github.com/gradle/foojay-toolchains)

---

## 5. io.spring.dependency-management

### 5.1 Status
- Latest: **1.1.7** (Dec 2024)
- Gradle 9 support: **không chính thức** — khuyến nghị migrate sang native Gradle platform

### 5.2 Alternative: Gradle native platform
```kotlin
dependencies {
    // Thay thế cho dependency management plugin
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
}
```

**Source:** [Gradle Platform documentation](https://docs.gradle.org/current/userguide/platforms.html)

---

## 6. Spring Modulith Compatibility

| Spring Boot | Compatible Spring Modulith |
|-------------|---------------------------|
| 3.2.x | 1.1.x |
| 3.5.x | 1.4.x |
| **4.0-4.1** | **2.1.x** |

Current system-admin-service dùng `spring-modulith-bom:1.1.0` — cần migrate lên `2.1.0` khi upgrade Spring Boot 4.x.

**Source:** [Spring Modulith Releases](https://spring.io/projects/spring-modulith#support)

---

## 7. Search Iterations

| # | Query | Kết quả chính |
|---|-------|--------------|
| 1 | "Gradle 9.4 HasConvention removed Kotlin plugin compatibility" | KGP ≥ 2.0.0 required |
| 2 | "org.gradle.api.internal.HasConvention Kotlin Gradle plugin Gradle 9" | Convention API fully removed in 9.0 |
| 3 | "Spring Boot 3.2 Gradle 9 compatibility" | SB 3.2 không hỗ trợ Gradle 9 |
| 4 | "foojay-resolver-convention 0.9.0 Gradle 9" | Need v1.0.0 |
| 5 | "Kotlin 2.x kotlinOptions deprecated replacement" | compilerOptions DSL |
| 6 | "Spring Boot 4.1 Gradle 9 support" | SB 4.x officially supports Gradle 9 |
| 7 | "Spring Modulith latest 2026 compatible Spring Boot" | SM 2.1.0 for SB 4.x |
