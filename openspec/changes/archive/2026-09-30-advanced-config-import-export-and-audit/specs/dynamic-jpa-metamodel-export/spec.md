# Spec: Dynamic JPA Metamodel Export

> Auto-generate multi-sheet Excel export definitions from JPA entity metadata without manual column configuration.

## Functional Requirements Covered

- **FR-003**: Xuất Excel nhiều Sheet tự động qua Dynamic JPA Metamodel

## Technical Design

### DynamicJpaMetamodelSheetExtractor

```kotlin
@Component
@ConditionalOnBean(EntityManager::class)
class DynamicJpaMetamodelSheetExtractor(
    private val entityManager: EntityManager
) {
    /**
     * Auto-extract SheetExportDefinition from JPA Metamodel.
     * Excludes @Id and @Version fields by default.
     * Respects @ExportColumn annotation for custom headers and ordering.
     */
    fun <T : Any> extractDefinition(entityClass: KClass<T>): SheetExportDefinition<T>
    
    /**
     * Extract definitions for multiple entity classes.
     */
    fun extractDefinitions(entityClasses: List<KClass<*>>): List<SheetExportDefinition<*>>
}
```

### SheetExportDefinition

```kotlin
data class SheetExportDefinition<T>(
    val sheetName: String,
    val columns: List<ColumnDefinition<T>>,
    val entityClass: KClass<T>
)
```

### @ExportColumn Annotation (Optional Override)

```kotlin
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExportColumn(
    val header: String = "",      // Custom header (default: camelCase → Title Case)
    val order: Int = Int.MAX_VALUE, // Column order (default: alphabetical)
    val exclude: Boolean = false   // Exclude from export
)
```

### Integration with MultiSheetExcelExportStrategy

```kotlin
class MultiSheetExcelExportStrategy(
    private val metaExtractor: DynamicJpaMetamodelSheetExtractor?,  // nullable for non-JPA
    private val sanitizer: ExportSanitizer
) {
    /**
     * Auto-export using JPA Metamodel introspection.
     * Each entity class → one sheet.
     */
    fun exportAll(
        entityDataMap: Map<KClass<*>, () -> Stream<*>>,
        outputStream: OutputStream
    )
    
    /**
     * Manual export with explicit definitions.
     * For non-JPA or custom column layouts.
     */
    fun export(
        definitions: List<SheetExportDefinition<*>>,
        dataProviders: Map<String, () -> Stream<*>>,
        outputStream: OutputStream
    )
}
```

## Validation Criteria

- Export 5 entity classes → produces 5-sheet Excel file with auto-generated column headers.
- Memory usage remains O(1) via `SXSSFWorkbook(100)`.
- `@ExportColumn(exclude = true)` fields MUST NOT appear in export.
- `@ExportColumn(header = "Custom")` overrides auto-generated header.
- `workbook.dispose()` called in `finally` block.
