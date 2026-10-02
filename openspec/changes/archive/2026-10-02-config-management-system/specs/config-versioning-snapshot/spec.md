## Purpose

Provides point-in-time configuration state snapshots, named milestone groupings across multiple domains, visual state diff comparisons, and conflict-aware rollback capabilities for system configurations.

## ADDED Requirements

### Requirement: Named Milestone Creation
The system SHALL allow administrators to create a named milestone that groups and snapshots the current state of one or more configuration domains into a permanent historical milestone.

#### Scenario: Successful milestone snapshot creation
- **WHEN** an administrator requests to create milestone "v1.2-release" covering domains `["MENU", "SYSTEM_CONFIG", "I18N"]`
- **THEN** the system generates a full state snapshot for each specified domain, calculates a SHA-256 checksum for payload integrity, and records the milestone with status "ACTIVE"

#### Scenario: Duplicate milestone name rejection
- **WHEN** an administrator attempts to create a milestone with an identifier that already exists
- **THEN** the system rejects the request with HTTP 409 Conflict and error code `SYS_006`

### Requirement: Cross-Snapshot Diff Analysis
The system SHALL provide a deep comparison mechanism between two configuration snapshots or between a past snapshot and the current active system state.

#### Scenario: Diff calculation between snapshots
- **WHEN** an administrator requests a diff between milestone snapshot A and current active state for domain "SYSTEM_CONFIG"
- **THEN** the system returns a structured list of changes categorized as ADDED, MODIFIED, or REMOVED along with exact before and after field values

### Requirement: Conflict-Aware Configuration Rollback
The system SHALL allow administrators to revert a configuration domain or entire milestone to a previously captured snapshot state, while identifying conflicting changes made after the snapshot timestamp.

#### Scenario: Clean rollback without conflicts
- **WHEN** an administrator requests rollback to snapshot S1 and no target configuration records have been modified since S1 was created
- **THEN** the system restores the configuration values from S1, evicts relevant caches, and records an audit log entry documenting the rollback event

#### Scenario: Rollback with detected conflicts and force override
- **WHEN** an administrator requests rollback to snapshot S1 and target records have been modified post-snapshot, but `forceOverwrite=true` is specified
- **THEN** the system overwrites the current records with the snapshot values and records a warning audit entry listing all overwritten keys

#### Scenario: Rollback rejected due to unconfirmed conflicts
- **WHEN** an administrator requests rollback to snapshot S1 with post-snapshot modifications and `forceOverwrite=false`
- **THEN** the system aborts the rollback with HTTP 409 Conflict, returning a detailed list of all conflicting entity keys
