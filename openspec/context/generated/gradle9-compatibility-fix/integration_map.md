# Integration Map

_Generated: 2026-08-06_

## Redis (Cache / Rate Limiting)

- Client: `StringRedisTemplate` (Spring Data Redis) — used in `ApiKeyService`
- Class: `ApiKeyService` — `src/main/kotlin/com/ntt/sysadmin/apipartner/ApiKeyService.kt`
- Protocol: Redis protocol via Spring Data Redis
- Request: `redisTemplate.opsForValue().set(key, value)` — direct Redis operations
- Response: N/A (fire-and-forget write)
- Purpose: Sync rate limit config to Redis for API Gateway consumption

## base-core (Internal Dependency)

- Interface: `SessionManagement` — from `com.ntt.basecore.domain.session`
- Class: `DefaultSessionManagement` — `src/main/kotlin/com/ntt/systemadminservice/shared/session/DefaultSessionManagement.kt`
- Protocol: In-process (library dependency)
- Status: ⚠️ Dependency not available — base-core starters not published to mavenLocal

## NOT DETECTED

- REST Client (RestTemplate, WebClient, FeignClient)
- Message Queue (Kafka, RabbitMQ)
- External API Gateway
- Database (JPA, JdbcTemplate) — referenced in test config but no entity/repository code
