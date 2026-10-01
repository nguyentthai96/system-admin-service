# DTO Pattern

_Generated: 2026-10-01_

## Entity (JPA)

- `I18nMessageEntity` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageEntity.kt`
  - Fields: `id`, `code`, `locale`, `message`, `module`, `isActive`, `createdAt`, `updatedAt`
  - Annotations: `@Entity`, `@Table(name = "i18n_messages")`, `@Id`, `@GeneratedValue`

- `I18nMessageEntity` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/entity/I18nMessageEntity.kt`
  - Fields: same as auth-service (duplicate)
  - Annotations: `@Entity`, `@Table(name = "i18n_messages")`, `@Id`, `@GeneratedValue`

## Request DTO

NOT DETECTED (i18n CRUD managed through ConfigManagementController which uses generic VersionedConfigDomain SPI — no i18n-specific request DTOs)

## Response DTO

NOT DETECTED (responses handled by VersionedConfigDomain export/template mechanism)

## Filter

NOT DETECTED

## Annotations Pattern

- `@Valid` validation on DTOs — standard pattern across services
- `@NotNull`, `@NotBlank`, `@Size` — Bean Validation
