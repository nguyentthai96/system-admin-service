## Context

See [proposal.md](proposal.md) for background and motivation.

The initial `config-management-system` change delivered core versioning infrastructure within `system-admin-service`. Export/import strategies (`SimpleJsonExportStrategy`, `RelationalJsonExportStrategy`, `MultiSheetExcelExportStrategy`) and coordinators (`RelationalImportCoordinator`, `TopologicalDependencySorter`) were implemented in `system-admin-service/shared/file/`. This limits reuse by other microservices. Additionally, multi-sheet Excel export requires manual `ColumnDefinition` configuration per entity, and import supports only 3 modes without `PATCH_VALUES` or safe self-referencing FK handling.

## Goals / Non-Goals

**Goals:**
- Promote proven export/import abstractions from `system-admin-service` into `base-file-starter` for platform-wide reuse.
- Introduce `DynamicJpaMetamodelSheetExtractor` to auto-generate `SheetExportDefinition` from JPA `Metamodel`, eliminating 100% of manual column definition boilerplate.
- Add `PATCH_VALUES` import mode (update only non-null fields) and `TwoPassTreeImportHandler<T, ID>` for safe self-referencing FK import.
- Integrate CWE-1236 `ExportSanitizer` into all Excel/CSV export paths.
- Enable Outbox event auto-republish on application restart.

**Non-Goals:**
- Changing the existing `ExcelExportStrategy<T>` contract (single-sheet, single-type — remains backward compatible).
- Introducing MongoDB as a mandatory dependency (remains optional via property switch).
- Modifying existing versioning entities (`sys_config_milestone`, `sys_config_snapshot`) DDL schema.

## Decisions

### 1. Package Structure: Flat Layout (No Sub-packages)

- **Decision**: Place all new export classes in `com.ntt.basecore.autoconfigure.file.export` and all import classes in `com.ntt.basecore.autoconfigure.file.import`. No `relational` sub-package.
- **Rationale**: User decision. Keeps package hierarchy shallow and consistent with existing `ExcelExportStrategy`, `CsvExportStrategy`. Relational variants are distinguished by naming convention (`Relational*`, `TwoPass*`).
- **Alternatives Considered**: Creating `file.export.relational` and `file.import.relational` sub-packages. Rejected — adds unnecessary navigation depth with only 3-4 classes per category.

### 2. Dynamic JPA Metamodel for Zero-Config Multi-Sheet Export

- **Decision**: Create `DynamicJpaMetamodelSheetExtractor` that uses `jakarta.persistence.metamodel.Metamodel` from `EntityManager` to auto-discover entity attributes, types, and column headers.
- **Rationale**: Eliminates boilerplate `ColumnDefinition` declarations that scale linearly with entity count. Developers only need to call `MultiSheetExcelExportStrategy.export(entities, outputStream)` — sheet names, column headers, and data types are auto-derived.
- **Architecture**:

```kotlin
@Component
class DynamicJpaMetamodelSheetExtractor(
    private val entityManager: EntityManager
) {
    fun <T : Any> extractDefinition(entityClass: KClass<T>): SheetExportDefinition<T> {
        val metamodel = entityManager.metamodel
        val entityType = metamodel.entity(entityClass.java)
        val attributes = entityType.singularAttributes
            .filter { !it.isId && !it.isVersion }
            .sortedBy { it.name }
        return SheetExportDefinition(
            sheetName = entityClass.simpleName ?: entityClass.java.simpleName,
            columns = attributes.map { attr ->
                ColumnDefinition(
                    header = attr.name.camelToTitle(),
                    getter = { entity -> attr.javaMember.accessible { (it as Field).get(entity) } },
                    type = attr.javaType
                )
            }
        )
    }
}
```

- **Override mechanism**: Annotate entity fields with `@ExportColumn(header = "Custom Header", order = 1)` to customize.

### 3. Four Import Strategy Modes

- **Decision**: Extend `ImportStrategyMode` enum with `PATCH_VALUES` alongside existing `TRUNCATE_AND_LOAD`, `DELETE_AND_INSERT`, `UPSERT_MERGE`.
- **Rationale**: Real-world config migration often requires partial updates (e.g., only updating `value` column while preserving other metadata). `PATCH_VALUES` copies only non-null fields from import payload to existing entity.
- **Behavior Matrix**:

| Mode | Delete Existing | Insert New | Update Existing | Preserve Sequence |
|------|:-:|:-:|:-:|:-:|
| `TRUNCATE_AND_LOAD` | ✅ (Truncate all) | ✅ | N/A | ❌ |
| `DELETE_AND_INSERT` | ✅ (Scoped) | ✅ | N/A | ✅ |
| `UPSERT_MERGE` | ❌ | ✅ | ✅ (Full) | ✅ |
| `PATCH_VALUES` | ❌ | ❌ | ✅ (Non-null only) | ✅ |

### 4. Two-Pass Tree Import for Self-Referencing FK

- **Decision**: Implement `TwoPassTreeImportHandler<T, ID>` with `SET CONSTRAINTS ALL DEFERRED` + two-pass execution.
- **Rationale**: Brainstorm decision (Q2). Self-referencing trees (Menu, Department) cannot be inserted in arbitrary order due to FK constraints. Two-pass approach:
  1. **Pass 1**: Insert all records with `parent_id = null`.
  2. **Pass 2**: Update `parent_id` to re-establish hierarchy.
- **Architecture**:

```kotlin
abstract class TwoPassTreeImportHandler<T : TreeEntity<ID>, ID : Serializable>(
    private val repository: JpaRepository<T, ID>,
    private val jdbcTemplate: JdbcTemplate
) : TableImportHandler<T> {

    override fun process(entities: List<T>, mode: ImportStrategyMode) {
        jdbcTemplate.execute("SET CONSTRAINTS ALL DEFERRED")

        // Pass 1: Insert with null parent
        val detachedEntities = entities.map { it.apply { parentId = null } }
        repository.saveAll(detachedEntities)
        repository.flush()

        // Pass 2: Restore parent relationships
        val parentMap = entities.associate { extractId(it) to extractParentId(it) }
        parentMap.forEach { (id, parentId) ->
            if (parentId != null) {
                updateParentId(id, parentId)
            }
        }
    }

    abstract fun extractId(entity: T): ID
    abstract fun extractParentId(entity: T): ID?
    abstract fun updateParentId(id: ID, parentId: ID)
}
```

### 5. Security: SHA-256 Checksum (No RSA)

- **Decision**: Use SHA-256 hash for file integrity verification. No RSA digital signatures.
- **Rationale**: User decision. SHA-256 provides cryptographic integrity verification sufficient for internal configuration transfer between controlled environments. RSA adds key management complexity without proportional security benefit in this context.

### 6. ExportSanitizer Integration (CWE-1236)

- **Decision**: Existing `ExportSanitizer` in `base-file-starter` is integrated into `MultiSheetExcelExportStrategy` cell writing pipeline.
- **Implementation**: Before writing any string cell value, check if first character is `=`, `+`, `-`, `@`, `\t`, `\r`. If so, prepend single quote `'`.

## Component Mapping

```
base-file-starter (NEW/MOVE classes)
├── export/
│   ├── SimpleJsonExportStrategy<T>          [MOVE from sysadmin]
│   ├── RelationalJsonExportStrategy         [MOVE from sysadmin]
│   ├── MultiSheetExcelExportStrategy        [MOVE from sysadmin]
│   ├── DynamicJpaMetamodelSheetExtractor    [NEW]
│   ├── SheetExportDefinition<T>             [NEW]
│   └── ExportFormat.JSON                    [MODIFY enum]
└── import/
    ├── TopologicalDependencySorter          [MOVE from sysadmin]
    ├── TableImportHandler<T>                [MOVE from sysadmin]
    ├── ImportStrategyMode                   [MOVE from sysadmin]
    ├── DefaultSimpleImportHandler<T, ID>    [MOVE from sysadmin]
    ├── TwoPassTreeImportHandler<T, ID>      [NEW]
    └── RelationalImportCoordinator          [MOVE from sysadmin]

system-admin-service (MODIFY/NEW)
├── versioning/domain/
│   └── ConfigDomainRegistry                 [NEW]
├── versioning/adapter/in/web/
│   └── ConfigManagementController           [MODIFY: add export/all, import endpoints]
├── menu/
│   └── MenuTreeImportHandler                [NEW: extends TwoPassTreeImportHandler]
├── organization/
│   └── DepartmentTreeImportHandler          [NEW: extends TwoPassTreeImportHandler]
└── config/
    └── CommonConfigImportHandler            [NEW: extends DefaultSimpleImportHandler]
```

## Risks / Trade-offs

- **[Risk] Breaking existing sysadmin code during MOVE** → *Mitigation*: MOVE operations create new classes in `base-file-starter` first, then update `system-admin-service` imports. Old files removed only after all references updated. Zero downtime.
- **[Risk] JPA Metamodel not available in non-JPA contexts** → *Mitigation*: `DynamicJpaMetamodelSheetExtractor` is `@ConditionalOnBean(EntityManager.class)`. Manual `SheetExportDefinition` remains available as fallback.
- **[Risk] `SET CONSTRAINTS ALL DEFERRED` only works with PostgreSQL** → *Mitigation*: `TwoPassTreeImportHandler` uses `JdbcTemplate.execute()` with database-specific SQL. For non-PostgreSQL databases, skip deferred constraints and rely solely on two-pass null-parent approach (works but less safe).
- **[Risk] Performance regression on large multi-sheet exports** → *Mitigation*: `SXSSFWorkbook(100)` limits memory to 100 rows per sheet. Style Pool prevents exceeding Excel's 64,000 style limit. `workbook.dispose()` in `finally` block ensures temp file cleanup.

## Migration Plan

1. **Phase 1: Base Infrastructure Promotion** (base-file-starter):
   - Create new classes in `base-file-starter/export/` and `import/` packages.
   - Add `ExportFormat.JSON` to existing enum.
   - Create `DynamicJpaMetamodelSheetExtractor` and `TwoPassTreeImportHandler`.
   - Unit test all new/moved classes.

2. **Phase 2: System-Admin Integration** (system-admin-service):
   - Update `system-admin-service` imports to use `base-file-starter` base classes.
   - Implement domain-specific `TableImportHandler` for Menu, Department, CommonConfig.
   - Add `ConfigDomainRegistry` and enhance controller endpoints.
   - Remove old `shared/file/` classes (replaced by base-file-starter).

3. **Phase 3: Security & Resilience**:
   - Integrate `ExportSanitizer` into `MultiSheetExcelExportStrategy`.
   - Enable `spring.modulith.republish-outstanding-events-on-restart=true`.
   - Integration test full export/import cycle with SHA-256 verification.

## Open Questions

- *None*: All design questions resolved during brainstorm phase (package structure → flat, security → SHA-256 only, self-ref FK → Two-Pass + Deferred, multi-sheet → Dynamic JPA Metamodel).
