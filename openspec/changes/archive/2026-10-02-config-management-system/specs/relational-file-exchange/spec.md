## Purpose

Enables structured data exchange for simple flat tables as well as complex relational and hierarchical configuration graphs across environments using JSON, CSV, and Multi-Sheet Excel formats with policy-driven import strategies.

## ADDED Requirements

### Requirement: Relational JSON Export with Checksum
The system SHALL export multi-table relational configuration graphs into a unified JSON document containing parent-child hierarchies, schema metadata, and a SHA-256 payload checksum.

#### Scenario: Export hierarchical menu tree to JSON
- **WHEN** an administrator requests JSON export for the "MENU" domain
- **THEN** the system streams the full hierarchy of menus, menu items, and associated action permissions into a structured JSON file containing `schemaVersion`, `exportedAt`, and `checksum`

### Requirement: Multi-Sheet Excel Export
The system SHALL support exporting all configuration domains or multiple interrelated tables into a single Excel workbook containing dedicated sheets for each domain or table.

#### Scenario: Export all system configurations to multi-sheet workbook
- **WHEN** an administrator requests an "Export All" in Excel format
- **THEN** the system generates a single `.xlsx` workbook where each configuration domain (Menus, Parameters, I18n Messages, Tenant Settings) is rendered in its own separate tab sheet with appropriate column headers

### Requirement: Policy-Driven Import Strategies
The system SHALL support multiple configurable import strategy modes per domain or table, including `DELETE_AND_INSERT`, `UPSERT_MERGE`, and `TRUNCATE_AND_LOAD`.

#### Scenario: Import with sequence preservation via DELETE_AND_INSERT
- **WHEN** a configuration file is imported under `DELETE_AND_INSERT` mode for a hierarchical menu structure
- **THEN** the system deletes existing child items within the specified scope in reverse dependency order and re-inserts items from the file preserving original array sequence and hierarchy paths

#### Scenario: Non-destructive update via UPSERT_MERGE
- **WHEN** a configuration file is imported under `UPSERT_MERGE` mode for system parameters
- **THEN** existing parameters matching unique keys are updated with new values while new parameter keys are inserted, leaving existing audit identifiers intact

#### Scenario: Fast environment initialization via TRUNCATE_AND_LOAD
- **WHEN** an administrator with super-admin privileges executes import with `TRUNCATE_AND_LOAD` and `confirm=true`
- **THEN** the target tables are truncated and the imported data is bulk-inserted directly within a single database transaction

### Requirement: Pre-Import Auto-Snapshot
The system SHALL automatically capture a pre-import configuration snapshot before applying any imported changes to enable immediate rollback if the import causes regression.

#### Scenario: Automatic safety snapshot prior to file import
- **WHEN** an import request is submitted for any domain
- **THEN** the system creates an automated snapshot tagged `pre-import-<timestamp>` before executing any write operations on the database
