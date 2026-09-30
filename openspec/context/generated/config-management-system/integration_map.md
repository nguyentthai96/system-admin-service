# Integration Map

_Generated: 2026-09-30_

## PostgreSQL Database (Primary Storage)

- Client: Spring Data JPA `EntityManager` & Hibernate 6.x
- Protocol: JDBC / PostgreSQL Native Protocol
- Driver: `org.postgresql:postgresql`
- Tables used:
  - `domain_configs`, `domain_config_history`
  - `menu_items`, `menu_roles`
  - `departments`, `positions`
  - `EVENT_PUBLICATION` (Transactional Outbox table via Spring Modulith)
  - `sys_config_snapshot` (Target table for JSONB snapshots)

## Redis Cache

- Client: `StringRedisTemplate`
- Protocol: RESP (Redis Serialization Protocol)
- Usage:
  - Domain Config cache: `domain:<id>:config` (TTL 30 min)
  - Menu permission cache: `menu:role:<id>`

## Spring Modulith Event Registry (Transactional Outbox)

- Client: `spring-modulith-starter-core` + `spring-modulith-starter-jpa`
- Table: `EVENT_PUBLICATION`
- Purpose: At-least-once delivery, zero-loss guarantee cho các sự kiện miền (`ConfigDomainChangedEvent`)

## Apache Kafka

- Client: `org.springframework.kafka:spring-kafka`
- Protocol: Kafka TCP
- Consumer: `PermissionChangedMenuConsumer` — `/home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/menu/adapter/in/kafka/PermissionChangedMenuConsumer.kt`

## MongoDB (Optional Pluggable Storage)

- Target Client: `org.springframework.boot:spring-boot-starter-data-mongodb` (khi bật `app.config.audit.storage-type=mongodb`)
- Target Collection: `config_audit_snapshots`
- Protocol: MongoDB Wire Protocol

## NOT DETECTED

- SOAP / XML Web Services: NOT DETECTED
- External Banking Gateway: NOT DETECTED (đây là admin microservice nội bộ)
