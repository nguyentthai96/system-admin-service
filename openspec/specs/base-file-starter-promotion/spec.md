# Spec: Base File Starter Promotion

> Promote proven export/import abstractions from `system-admin-service/shared/file/` into `base-file-starter` for platform-wide reuse.

## Functional Requirements Covered

- **FR-001**: Xuất JSON phẳng dạng streaming — `SimpleJsonExportStrategy<T>`
- **FR-002**: Xuất đồ thị quan hệ JSON có SHA-256 — `RelationalJsonExportStrategy`
- **FR-003**: Xuất Excel nhiều Sheet — `MultiSheetExcelExportStrategy`
- **FR-004**: Thuật toán Kahn sắp xếp phụ thuộc — `TopologicalDependencySorter`
- **FR-005**: Bốn chế độ nạp cấu hình — `ImportStrategyMode` enum + handlers
- **FR-006**: Khung mặc định giảm mã thừa — `DefaultSimpleImportHandler<T, ID>`
- **FR-013**: Vô hiệu hóa Formula Injection CWE-1236 — `ExportSanitizer` integration
- **FR-015**: Deferred FK Constraints + Two-Pass — `TwoPassTreeImportHandler<T, ID>`

## Components to Promote (MOVE)

| Class | From (system-admin-service) | To (base-file-starter) | Action |
|-------|---------------------------|----------------------|--------|
| `SimpleJsonExportStrategy<T>` | `shared/file/export/` | `autoconfigure/file/export/` | MOVE |
| `RelationalJsonExportStrategy` | `shared/file/export/` | `autoconfigure/file/export/` | MOVE |
| `MultiSheetExcelExportStrategy` | `shared/file/export/` | `autoconfigure/file/export/` | MOVE |
| `TopologicalDependencySorter` | `shared/file/import/` | `autoconfigure/file/import/` | MOVE |
| `TableImportHandler<T>` | `shared/file/import/` | `autoconfigure/file/import/` | MOVE |
| `ImportStrategyMode` | `shared/file/import/` | `autoconfigure/file/import/` | MOVE |
| `DefaultSimpleImportHandler<T, ID>` | `shared/file/import/` | `autoconfigure/file/import/` | MOVE |
| `RelationalImportCoordinator` | `shared/file/import/` | `autoconfigure/file/import/` | MOVE |

## Components to Create (NEW)

| Class | Package | Purpose |
|-------|---------|---------|
| `TwoPassTreeImportHandler<T, ID>` | `autoconfigure/file/import/` | Self-referencing FK-safe two-pass hierarchy import |
| `DynamicJpaMetamodelSheetExtractor` | `autoconfigure/file/export/` | Auto-introspect JPA Metamodel for column definitions |
| `SheetExportDefinition<T>` | `autoconfigure/file/export/` | Data class holding sheet name + column definitions |
| `ExportFormat.JSON` | `domain/file/ExportFormat` | Add JSON enum value |

## Package Layout (Target State)

```
base-file-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/file/
├── export/
│   ├── ExcelExportStrategy.kt          (existing, unchanged)
│   ├── CsvExportStrategy.kt            (existing, unchanged)
│   ├── ExportService.kt                (existing, unchanged)
│   ├── ExportSanitizer.kt              (existing, unchanged)
│   ├── SimpleJsonExportStrategy.kt     (MOVED)
│   ├── RelationalJsonExportStrategy.kt (MOVED)
│   ├── MultiSheetExcelExportStrategy.kt(MOVED)
│   ├── DynamicJpaMetamodelSheetExtractor.kt (NEW)
│   └── SheetExportDefinition.kt        (NEW)
└── import/
    ├── ImportService.kt                (existing, unchanged)
    ├── ImportProgressNotifier.kt       (existing, unchanged)
    ├── TopologicalDependencySorter.kt  (MOVED)
    ├── TableImportHandler.kt           (MOVED)
    ├── ImportStrategyMode.kt           (MOVED)
    ├── DefaultSimpleImportHandler.kt   (MOVED)
    ├── TwoPassTreeImportHandler.kt     (NEW)
    └── RelationalImportCoordinator.kt  (MOVED)
```

## Validation Criteria

- All existing unit tests for `ExcelExportStrategy`, `CsvExportStrategy` MUST pass without modification.
- MOVED classes MUST retain identical public API signatures.
- `system-admin-service` compile MUST succeed after updating imports.
