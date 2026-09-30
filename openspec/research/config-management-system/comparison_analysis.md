# Comparison Analysis: Configuration Management System

**Date:** 2026-09-30

---

## 1. Approach Comparison Matrix

| Approach | Versioning | Cross-domain Snapshot | Export/Import | Audit Trail | Rollback | Complexity |
|----------|:---------:|:--------------------:|:------------:|:-----------:|:--------:|:----------:|
| **Hibernate Envers** | ✅ Auto | ❌ Per-entity only | ❌ Manual | ✅ Full | ⚠️ Manual reconstruct | Low |
| **Custom Centralized (★ đề xuất)** | ✅ Selective | ✅ Native | ✅ Native | ✅ Diff-based | ✅ Native | Medium |
| **Event Sourcing** | ✅ Full | ✅ Via aggregates | ⚠️ Via projection | ✅ Complete | ✅ Replay | High |
| **GitOps (Git-backed)** | ✅ Git | ✅ Via commits | ✅ Files | ✅ Git log | ✅ Git revert | Medium |
| **Per-domain History Tables** | ✅ Per-domain | ❌ No cross-domain | ⚠️ Per-domain | ✅ Per-domain | ✅ Per-domain | Low |

## 2. Feature-by-Feature Comparison

| Feature | Envers | Custom Centralized | Event Sourcing | GitOps | Cần cho project? |
|---------|:------:|:-----------------:|:--------------:|:------:|:----------------:|
| Change tracking per field | ✅ | ✅ | ✅ | ⚠️ | ⭐ Must |
| Named milestones/snapshots | ❌ | ✅ | ⚠️ | ✅ (tags) | ⭐ Must |
| Cross-domain rollback | ❌ | ✅ | ✅ | ✅ | ⭐ Must |
| Export Excel/CSV/JSON | ❌ | ✅ | ❌ | ✅ (files) | ⭐ Must |
| Import with validation | ❌ | ✅ | ❌ | ⚠️ | ⭐ Must |
| 5 recent changes trace | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Selective domain tracking | ❌ (all entities) | ✅ | ✅ | ✅ | Nice to have |
| No schema coupling | ❌ | ✅ | ✅ | ✅ | Nice to have |
| Spring Boot integration | ✅ Native | ✅ JPA | ⚠️ Custom | ❌ External | ⭐ Must |
| DB-only (no external tools) | ✅ | ✅ | ✅ | ❌ (needs Git) | ⭐ Must |

## 3. Recommendation

### Approach: **Custom Centralized** (BUILD)

**Lý do chính:**
1. **Cross-domain snapshot** là requirement chính → Envers không hỗ trợ
2. **Export/Import integration** cần native → không tool nào cung cấp sẵn
3. **Named milestones** → Envers không có concept này
4. **Selective tracking** → chỉ config entities, không phải toàn bộ domain
5. **Tech stack fit** → Spring Boot + JPA + PostgreSQL JSONB = đã có sẵn

**Trade-off chấp nhận:**
- Phải implement custom → ~50h dev effort
- Phải maintain custom code → nhưng clean, well-structured
- Centralized table sẽ grow → managed by partitioning

### Gap Score: 85% covered

| Gap | Severity | Resolution |
|-----|----------|------------|
| Partition strategy cho config_change_history | LOW | Implement monthly partition |
| Async export cho large datasets | LOW | Phase 2 enhancement |
| Conflict detection khi 2 admin edit cùng lúc | MEDIUM | Optimistic locking (version column) |
| Encrypted field import/re-encrypt | LOW | Phase 2 — for now skip encrypted in import |

---

## 4. Open Source Evaluation Summary

### 4.1 Configu (configu/configu)

| Tiêu chí | Score | Notes |
|----------|:-----:|-------|
| Feature completeness | 5/10 | App config focus, not multi-domain admin |
| Applicability | 3/10 | Different paradigm (CLI-based, not DB-driven) |
| Activity | 7/10 | Active development |
| Documentation | 6/10 | Good README |
| Code quality | 6/10 | TypeScript, well-structured |
| Community | 5/10 | ~1K stars |
| Popularity | 4/10 | Niche |
| **Overall** | **4.2/10** | **Not applicable — different paradigm** |

**Verdict**: Không phù hợp — Configu là CLI tool cho environment config, không phải admin panel CRUD.

### 4.2 Spring Cloud Config

| Tiêu chí | Score | Notes |
|----------|:-----:|-------|
| Feature completeness | 4/10 | Git-backed, no CRUD UI, no snapshots |
| Applicability | 5/10 | Spring ecosystem nhưng khác use case |
| Activity | 8/10 | Official Spring project |
| **Overall** | **4.5/10** | **Partial fit — chỉ cho env config, không cho admin CRUD** |

### 4.3 Hibernate Envers

| Tiêu chí | Score | Notes |
|----------|:-----:|-------|
| Feature completeness | 7/10 | Full audit, querying, BUT no cross-domain snapshot |
| Applicability | 7/10 | Same tech stack |
| Activity | 9/10 | Part of Hibernate core |
| **Overall** | **6.8/10** | **Good for single-entity audit, not for this use case** |

**Recommendation**: Tham khảo Envers patterns (AuditReader, revision tracking) nhưng implement custom.

---

## 5. Technology Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Versioning approach | Custom centralized | Cross-domain snapshots requirement |
| Audit storage | PostgreSQL JSONB | Already in stack, flexible schema |
| Export library | Apache POI (SXSSFWorkbook) | Streaming, multi-sheet, enterprise standard |
| CSV library | OpenCSV | Lightweight, simple |
| Cache | Redis (existing) | Already integrated |
| ID generation | Snowflake (existing) | Consistent with project |
| DB partition | Monthly on `changed_at` | Manage table growth |
