# Research Brief: Gradle 9 Compatibility Fix — system-admin-service

## 1. Mô tả vấn đề

`system-admin-service` build thất bại với Gradle 9.4.1 do lỗi:
```
org/gradle/api/internal/HasConvention
> org.gradle.api.internal.HasConvention
```

Lỗi thứ hai là hậu quả cascade:
```
Failed to query the value of property 'buildFlowServiceProperty'.
> Could not isolate value ... of type BuildFlowService.Parameters
```

## 2. Nguyên nhân gốc (Root Cause)

`org.gradle.api.internal.HasConvention` là internal API đã bị **xóa hoàn toàn** trong Gradle 9.0. Các plugin cũ sử dụng Convention API sẽ throw `ClassNotFoundException` khi chạy trên Gradle 9.

### Plugin gây lỗi trong `system-admin-service`:

| Plugin | Version hiện tại | Vấn đề | Version tối thiểu cho Gradle 9 |
|--------|-----------------|--------|-------------------------------|
| `kotlin("jvm")` | **1.9.20** | Dùng `HasConvention` API | **≥ 2.0.0** (khuyến nghị 2.3.0+) |
| `kotlin("plugin.spring")` | **1.9.20** | Cùng vấn đề | **≥ 2.0.0** |
| `org.springframework.boot` | **3.2.0** | Không hỗ trợ Gradle 9 | **≥ 4.0.0** (hoặc 3.5 với Gradle 8) |
| `io.spring.dependency-management` | **1.1.4** | Không tương thích Gradle 9 | **1.1.7** (hoặc dùng native platform) |
| `foojay-resolver-convention` | **0.9.0** | Dùng removed API (`FoojayToolchainsPlugin`, `JvmVendorSpec.IBM_SEMERU`) | **≥ 1.0.0** |

### Deprecated syntax trong `build.gradle.kts`:
```kotlin
// ❌ Deprecated — bị xóa trong Kotlin 2.x
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}
```

## 3. So sánh với các service khác

### auth-service & account-service (ĐÃ HOẠT ĐỘNG):
- Dùng convention plugin: `ntt.spring-app-conventions`
- Dùng version catalog: `libs` (from `com.ntt:version-catalog:0.0.1-SNAPSHOT`)
- Kotlin **2.4.10**, Spring Boot **4.1.0**, Spring DM **1.1.7**
- Dùng `compilerOptions` DSL (mới) thay `kotlinOptions` (deprecated)
- Java/Kotlin target: **JVM 25**

### system-admin-service (LỖI):
- Hardcode version trực tiếp trong `build.gradle.kts`
- Kotlin **1.9.20**, Spring Boot **3.2.0**, Spring DM **1.1.4**
- Dùng `kotlinOptions` DSL (deprecated)
- Java target: **JVM 21**
- `foojay-resolver-convention` **0.9.0** trong `settings.gradle.kts`

## 4. Phân tích giải pháp

### Option A: Migrate sang convention plugins (✅ KHUYẾN NGHỊ)
- Align với auth-service và account-service
- Dùng `ntt.spring-app-conventions` + version catalog `libs`
- Single source of truth cho tất cả versions
- Tự động nhận Kotlin 2.4.10, Spring Boot 4.1.0
- **Rủi ro**: Cần review breaking changes Spring Boot 3.2 → 4.1

### Option B: Chỉ nâng plugin versions (MANUAL)
- Giữ cấu hình thủ công nhưng update versions
- Kotlin → 2.4.10, Spring Boot → 4.1.0, Spring DM → 1.1.7
- foojay → 1.0.0
- **Rủi ro**: Không align với các service khác, maintenance burden

### Option C: Downgrade Gradle (KHÔNG KHUYẾN NGHỊ)
- Quay về Gradle 8.14.x
- Giữ nguyên plugin versions cũ
- **Rủi ro**: Đi ngược hướng phát triển, mất lợi ích Gradle 9

## 5. Keywords
- HasConvention, Convention API removal, Gradle 9 compatibility
- Kotlin Gradle Plugin 2.x migration, compilerOptions DSL
- Spring Boot 4.x migration, foojay-resolver-convention
- Convention plugins, version catalog, build-logic

## 6. Kết luận

**Option A là giải pháp tối ưu** vì:
1. Align 100% với các service khác trong monorepo
2. Single source of truth cho versions (version catalog)
3. Ít code hơn trong `build.gradle.kts`
4. Dễ maintain — chỉ cần update version catalog 1 nơi
