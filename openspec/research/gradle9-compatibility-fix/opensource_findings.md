# Open Source Findings: Gradle 9 Migration

## 1. Tổng quan

Vì đây là vấn đề **cấu hình build** (không phải tính năng mới), open source analysis tập trung vào **Gradle ecosystem tools và migration guides** thay vì so sánh libraries.

---

## 2. Project đánh giá

### 2.1 Gradle Build Tool (gradle/gradle)

| Tiêu chí | Score (1-5) |
|----------|:-----------:|
| Maturity | 5 |
| Documentation | 5 |
| Community | 5 |
| Migration Guide | 5 |
| **Tổng** | **20/20** |

**URL:** https://github.com/gradle/gradle

**Key Findings:**
- Gradle 9.0 release notes document all removed APIs including `HasConvention`
- Migration guide provides step-by-step replacement patterns
- `Convention` → `Extensions` migration is well-documented

---

### 2.2 Kotlin Gradle Plugin (JetBrains/kotlin)

| Tiêu chí | Score (1-5) |
|----------|:-----------:|
| Maturity | 5 |
| Documentation | 4 |
| Gradle 9 Support | 5 |
| Migration Path | 4 |
| **Tổng** | **18/20** |

**URL:** https://github.com/JetBrains/kotlin

**Key Findings:**
- KGP ≥ 2.0.0 removed all `HasConvention` usage
- `kotlinOptions` DSL deprecated since 2.0.0, replaced by `compilerOptions`
- K2 compiler is default since 2.0.0
- Version 2.4.10 is latest stable (Aug 2026)

---

### 2.3 foojay-toolchains (gradle/foojay-toolchains)

| Tiêu chí | Score (1-5) |
|----------|:-----------:|
| Maturity | 4 |
| Documentation | 3 |
| Gradle 9 Support | 5 (since 1.0.0) |
| Migration Path | 5 |
| **Tổng** | **17/20** |

**URL:** https://github.com/gradle/foojay-toolchains

**Key Findings:**
- Version 0.9.0 incompatible with Gradle 9 (uses `FoojayToolchainsPlugin`, `JvmVendorSpec.IBM_SEMERU`)
- Version 1.0.0 specifically released to fix Gradle 9 compatibility
- Simple version bump fix — no configuration changes needed

---

### 2.4 Spring Boot Gradle Plugin (spring-projects/spring-boot)

| Tiêu chí | Score (1-5) |
|----------|:-----------:|
| Maturity | 5 |
| Documentation | 5 |
| Gradle 9 Support | 5 (since 4.0) |
| Migration Path | 3 (major version jump) |
| **Tổng** | **18/20** |

**URL:** https://github.com/spring-projects/spring-boot

**Key Findings:**
- Spring Boot 3.2.0 does NOT support Gradle 9
- Spring Boot 4.0+ officially supports Gradle 9
- Migration from 3.x → 4.x is significant (Jackson 3, Security 7, modularization)
- `spring-boot-starter-classic` provided for smooth migration

---

## 3. Gap Analysis

| Gap | Solution Source | Coverage |
|-----|----------------|:--------:|
| HasConvention removal | KGP 2.0+ / SB 4.0+ | 100% |
| kotlinOptions deprecated | KGP compilerOptions DSL | 100% |
| foojay API removals | foojay 1.0.0 | 100% |
| Convention API to Extensions | Convention plugins (build-logic) | 100% |

**Overall Gap Coverage: 100%** — all gaps have well-documented, production-ready solutions.

---

## 4. Scoring Matrix Summary

| Project | Score | Relevance |
|---------|:-----:|:---------:|
| Gradle | 20/20 | Core — defines the breaking change |
| JetBrains Kotlin | 18/20 | Primary — cause of HasConvention error |
| Spring Boot | 18/20 | Primary — requires version upgrade |
| foojay-toolchains | 17/20 | Secondary — settings.gradle.kts fix |
