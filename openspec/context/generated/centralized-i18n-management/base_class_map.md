# Base Class Map

_Generated: 2026-10-01 | Services: base-core, auth-service, system-admin-service_

## Configuration / Auto-Configuration

- `I18nAutoConfiguration` — `components/base-core/src/main/kotlin/com/ntt/basecore/configuration/I18nAutoConfiguration.kt`
  - extends: N/A (standalone `@AutoConfiguration`)
  - provides: `localeResolver()`, `messageSource()` with `@ConditionalOnMissingBean`

- `BaseCoreAutoConfiguration` — `components/base-core/src/main/kotlin/com/ntt/basecore/configuration/BaseCoreAutoConfiguration.kt`

- `BaseCoreServletAutoConfiguration` — `components/base-core/src/main/kotlin/com/ntt/basecore/configuration/BaseCoreServletAutoConfiguration.kt`

## Controller / Exception Handling

- `BaseControllerAdvice` — `components/base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt`
  - extends: N/A (abstract class)
  - key method: `resolveMessage(msgCode, args, fallback)` — uses optional `MessageSource?`

- `BaseController` — `components/base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseController.kt`
  - extends: N/A (abstract class)

## Service / CRUD

- `AbstractCrudService` — `components/base-core/src/main/kotlin/com/ntt/basecore/domain/service/AbstractCrudService.kt`
- `AbstractSpecificationCrudService` — `components/base-core/src/main/kotlin/com/ntt/basecore/domain/service/AbstractSpecificationCrudService.kt`

## i18n (Existing — to be refactored)

- `DatabaseMessageSource` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
  - extends: `AbstractMessageSource` (Spring)
  - uses: `I18nMessageRepository`, Caffeine cache (5min TTL, maxSize=500)

## Error Code

- `ErrorCodeBase` — `components/base-core/src/main/kotlin/com/ntt/basecore/exception/base/ErrorCodeBase.kt`
  - extends: N/A (abstract class)

## Handler / Factory

NOT DETECTED (project uses Controller pattern, not Handler/Factory pattern)

## Client / Gateway

NOT DETECTED (no external API gateway clients for i18n)
