# Comparison Analysis: Gradle 9 Compatibility Fix Strategies

## 1. Comparison Matrix

| Tiêu chí | Option A: Convention Plugins | Option B: Manual Upgrade | Option C: Downgrade Gradle |
|----------|:---------------------------:|:------------------------:|:--------------------------:|
| **Fix build ngay** | ✅ | ✅ | ✅ |
| **Align với services khác** | ✅ (100%) | ❌ (partial) | ❌ |
| **Maintenance cost** | Thấp | Cao | Trung bình |
| **Single source of truth** | ✅ (version catalog) | ❌ | ❌ |
| **Effort** | Trung bình | Trung bình | Thấp |
| **Risk** | Trung bình (SB 3.2→4.1) | Trung bình (SB 3.2→4.1) | Thấp |
| **Future-proof** | ✅ | ⚠️ | ❌ |
| **Breaking changes** | Cần review SB 4.x | Cần review SB 4.x | Không |

## 2. Phân tích chi tiết

### Option A: Migrate sang Convention Plugins (✅ KHUYẾN NGHỊ)

**Mô tả:**
Chuyển `build.gradle.kts` và `settings.gradle.kts` sang dùng convention plugin `ntt.spring-app-conventions` + version catalog `libs`, giống auth-service và account-service.

**Thay đổi cần thực hiện:**

#### build.gradle.kts
```diff
 plugins {
-    id("org.springframework.boot") version "3.2.0"
-    id("io.spring.dependency-management") version "1.1.4"
-    kotlin("jvm") version "1.9.20"
-    kotlin("plugin.spring") version "1.9.20"
+    id("ntt.spring-app-conventions")
 }
 
 group = "com.ntt"
 version = "0.0.1-SNAPSHOT"
 
-java {
-    sourceCompatibility = JavaVersion.VERSION_21
-}
-
-repositories {
-    mavenCentral()
-}
-
 dependencies {
+    implementation(platform("com.ntt:platform:0.0.1-SNAPSHOT"))
     implementation("org.springframework.boot:spring-boot-starter-web")
-    implementation("org.springframework.modulith:spring-modulith-starter-core")
     implementation("org.springframework.boot:spring-boot-starter-data-redis")
+    implementation("org.springframework.modulith:spring-modulith-starter-core")
 
     testImplementation("org.springframework.boot:spring-boot-starter-test")
     testImplementation("org.springframework.modulith:spring-modulith-starter-test")
 }
 
-dependencyManagement {
-    imports {
-        mavenBom("org.springframework.modulith:spring-modulith-bom:1.1.0")
-    }
-}
-
-tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
-    kotlinOptions {
-        freeCompilerArgs += "-Xjsr305=strict"
-        jvmTarget = "21"
-    }
-}
-
 tasks.withType<Test> {
     useJUnitPlatform()
 }
```

#### settings.gradle.kts
```diff
 plugins {
-    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
+    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
 }
```

**Pros:**
- Align 100% với auth-service, account-service
- Convention plugin đã xử lý: Kotlin config, JVM target, Spring Boot BOM, Spring Modulith BOM, allOpen annotation
- Version catalog quản lý tất cả versions tập trung
- `build.gradle.kts` rất gọn

**Cons:**
- Spring Boot jump 3.2 → 4.1 (major)
- Spring Modulith jump 1.1 → 2.1 (major)
- Kotlin jump 1.9.20 → 2.4.10 (major)
- JVM target jump 21 → 25
- Cần verify application code compatible

---

### Option B: Manual Version Upgrade

**Mô tả:**
Giữ cấu hình thủ công, chỉ update plugin versions.

```kotlin
plugins {
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
}
```

**Pros:**
- Kiểm soát từng version
- Không phụ thuộc vào convention plugin

**Cons:**
- Không align với các service khác
- Duplicate cấu hình (repos, kotlin config, etc.)
- Phải tự quản lý Spring Modulith BOM version
- Maintenance burden cao

---

### Option C: Downgrade Gradle

**Mô tả:**
Thay Gradle 9.4.1 → 8.14 trong wrapper.

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-all.zip
```

**Pros:**
- Zero code changes
- Immediate fix

**Cons:**
- Đi ngược hướng phát triển
- Không align với services khác (tất cả dùng Gradle 9.4.1)
- Mất lợi ích performance và features của Gradle 9
- Vẫn dùng Spring Boot 3.2 (EOL từ Dec 2024)

## 3. Gap Analysis

### Với Option A (Recommended):

| Area | Hiện tại | Sau upgrade | Gap / Action |
|------|---------|-------------|--------------|
| Kotlin | 1.9.20 | 2.4.10 | Review K2 compiler impact |
| Spring Boot | 3.2.0 | 4.1.0 | Review Jackson 3, Security 7, module changes |
| Spring Modulith | 1.1.0 BOM | 2.1.0 (auto from catalog) | API may change |
| JVM | 21 | 25 | Runtime JDK phải ≥ 25 |
| Gradle | 9.4.1 | 9.4.1 | Giữ nguyên |
| foojay | 0.9.0 | 1.0.0 | Update settings.gradle.kts |
| Build config | Manual | Convention plugin | Simpler, aligned |

### Đánh giá rủi ro

**Source code impact (system-admin-service):**

Service hiện tại **rất đơn giản** — chỉ có starter dependencies, chưa có business logic phức tạp:
- `spring-boot-starter-web`
- `spring-modulith-starter-core`
- `spring-boot-starter-data-redis`
- Internal base-core deps (commented out)

→ **Rủi ro thấp** vì chưa có code sử dụng các API bị breaking change.

## 4. Recommendation

> **Option A: Migrate sang Convention Plugins** là giải pháp tốt nhất.

**Lý do:**
1. Service hiện tại rất đơn giản → rủi ro migration thấp
2. Align với monorepo standards (auth-service, account-service đã dùng)
3. Single source of truth cho versions → bảo trì dễ dàng
4. Convention plugin đã được kiểm chứng hoạt động với Gradle 9.4.1
5. Future-proof — không phải lặp lại quá trình upgrade cho mỗi service

## 5. Implementation Checklist

- [ ] Update `settings.gradle.kts`: foojay-resolver-convention → 1.0.0
- [ ] Update `build.gradle.kts`: chuyển sang `ntt.spring-app-conventions`
- [ ] Xóa hardcoded versions, kotlinOptions, manual repos
- [ ] Add platform BOM dependency
- [ ] Remove manual Spring Modulith BOM (convention plugin handles it)
- [ ] Verify build: `./gradlew build`
- [ ] Verify src code compatibility (nếu có)
