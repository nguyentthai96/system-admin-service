# Spec: Four-Mode Policy Import & Two-Pass Tree Import

> Flexible configuration import with 4 strategy modes and safe self-referencing FK handling.

## Functional Requirements Covered

- **FR-005**: Bốn chế độ nạp cấu hình linh hoạt
- **FR-006**: Khung thực thi mặc định giảm mã thừa
- **FR-015**: Hoãn kiểm tra ràng buộc khóa ngoại (Deferred FK Constraints)

## Technical Design

### ImportStrategyMode Enum

```kotlin
enum class ImportStrategyMode {
    /** Truncate entire table, then bulk insert all records. Fastest but loses sequence. */
    TRUNCATE_AND_LOAD,
    
    /** Delete by domain scope, re-insert to preserve sequences and audit trails. */
    DELETE_AND_INSERT,
    
    /** Match by natural business key: update if exists, insert if new. Full field replacement. */
    UPSERT_MERGE,
    
    /** Match by natural key: update only non-null fields from import payload. No inserts. */
    PATCH_VALUES
}
```

### TableImportHandler SPI

```kotlin
interface TableImportHandler<T> {
    /** Domain name for this handler (e.g., "menu", "department"). */
    val domainName: String
    
    /** Entity class managed by this handler. */
    val entityClass: KClass<T>
    
    /** FK dependencies — other domain names that must be imported before this one. */
    val dependencies: Set<String>
        get() = emptySet()
    
    /** Execute import with given strategy mode. ACID within coordinator transaction. */
    fun process(entities: List<T>, mode: ImportStrategyMode)
    
    /** Optional: Pre-import validation. Throw ImportValidationException on failure. */
    fun validate(entities: List<T>): List<String>
        = emptyList()
    
    /** Optional: Cleanup after successful import (e.g., cache invalidation). */
    fun afterImport(mode: ImportStrategyMode) {}
}
```

### DefaultSimpleImportHandler (Flat Table)

```kotlin
abstract class DefaultSimpleImportHandler<T : Any, ID : Serializable>(
    private val repository: JpaRepository<T, ID>
) : TableImportHandler<T> {
    
    /** Natural business key extractor for UPSERT_MERGE matching. */
    abstract fun extractNaturalKey(entity: T): String
    
    /** Find existing entity by natural key. */
    abstract fun findByNaturalKey(key: String): T?
    
    override fun process(entities: List<T>, mode: ImportStrategyMode) {
        when (mode) {
            TRUNCATE_AND_LOAD -> {
                repository.deleteAllInBatch()
                repository.saveAll(entities)
            }
            DELETE_AND_INSERT -> {
                repository.deleteAllInBatch()
                repository.saveAll(entities)
                repository.flush()
            }
            UPSERT_MERGE -> {
                entities.forEach { entity ->
                    val existing = findByNaturalKey(extractNaturalKey(entity))
                    if (existing != null) {
                        mergeFields(existing, entity)
                        repository.save(existing)
                    } else {
                        repository.save(entity)
                    }
                }
            }
            PATCH_VALUES -> {
                entities.forEach { entity ->
                    val existing = findByNaturalKey(extractNaturalKey(entity))
                    if (existing != null) {
                        patchNonNullFields(existing, entity)
                        repository.save(existing)
                    }
                }
            }
        }
    }
    
    /** Override to customize merge behavior. Default: copy all non-null fields. */
    open fun mergeFields(target: T, source: T) { /* reflection-based copy */ }
    
    /** Override to customize patch behavior. Default: copy only non-null fields. */
    open fun patchNonNullFields(target: T, source: T) { /* reflection-based selective copy */ }
}
```

### TwoPassTreeImportHandler (Self-Referencing FK)

```kotlin
abstract class TwoPassTreeImportHandler<T : Any, ID : Serializable>(
    private val repository: JpaRepository<T, ID>,
    private val jdbcTemplate: JdbcTemplate
) : TableImportHandler<T> {
    
    abstract fun extractId(entity: T): ID
    abstract fun extractParentId(entity: T): ID?
    abstract fun setParentId(entity: T, parentId: ID?)
    abstract fun updateParentIdSql(): String  // e.g., "UPDATE menu SET parent_id = ? WHERE id = ?"

    override fun process(entities: List<T>, mode: ImportStrategyMode) {
        // Step 0: Defer FK constraints
        jdbcTemplate.execute("SET CONSTRAINTS ALL DEFERRED")
        
        // Step 1: Handle deletion based on mode
        when (mode) {
            TRUNCATE_AND_LOAD, DELETE_AND_INSERT -> repository.deleteAllInBatch()
            else -> {} // UPSERT/PATCH don't delete
        }
        
        // Step 2: Pass 1 — Insert all with parent_id = null
        val parentMap = mutableMapOf<ID, ID?>()
        entities.forEach { entity ->
            parentMap[extractId(entity)] = extractParentId(entity)
            setParentId(entity, null)
        }
        repository.saveAll(entities)
        repository.flush()
        
        // Step 3: Pass 2 — Restore parent relationships
        parentMap.forEach { (id, parentId) ->
            if (parentId != null) {
                jdbcTemplate.update(updateParentIdSql(), parentId, id)
            }
        }
    }
}
```

### RelationalImportCoordinator

```kotlin
@Component
class RelationalImportCoordinator(
    private val handlers: List<TableImportHandler<*>>,
    private val sorter: TopologicalDependencySorter,
    private val transactionTemplate: TransactionTemplate,
    private val eventPublisher: ApplicationEventPublisher
) {
    /**
     * Import multiple tables in a single ACID transaction.
     * Tables are sorted by FK dependency (Kahn's algorithm).
     */
    fun importAll(
        importData: Map<String, ImportTablePayload>,
        mode: ImportStrategyMode,
        checksumSha256: String?
    ): ImportSummaryResponse {
        // 1. Validate checksum if provided
        // 2. Sort handlers by dependency order
        // 3. Execute within @Transactional
        // 4. Publish ConfigDomainChangedEvent per domain
        // 5. Return summary
    }
}
```

## Validation Criteria

- `TRUNCATE_AND_LOAD` on 3 tables → all data replaced, no FK violations.
- `DELETE_AND_INSERT` on Menu tree → data re-inserted with preserved sequence IDs.
- `UPSERT_MERGE` on CommonConfig → existing updated, new inserted.
- `PATCH_VALUES` on FeatureFlags → only non-null fields updated, others unchanged.
- Self-referencing Menu tree (3 levels deep) → imported without FK constraint violation.
- Circular dependency in DAG → throws `SysAdminException(CONFIG_CIRCULAR_DEPENDENCY)`.
- Any table failure → entire import rolled back (ACID).
