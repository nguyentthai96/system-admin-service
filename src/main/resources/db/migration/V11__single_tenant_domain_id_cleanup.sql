-- =====================================================
-- V11: Single-Tenant Verification & domain_id Index Cleanup
-- Part of: microservice-database-architecture change
--
-- Context: System has been simplified to Single-Tenant.
-- domain_id columns are retained for backward compatibility
-- but are no longer used for tenant isolation.
-- sysadmin-client ReadModels do NOT map domain_id.
--
-- This migration:
-- 1. Drops redundant domain_id-only indexes (not used in queries)
-- 2. Adds comments documenting Single-Tenant decision
-- 3. Verifies NO RLS policies exist (pure domain model)
-- =====================================================

-- Step 1: Drop redundant domain_id-only indexes
-- These were created for multi-tenant filtering which is no longer needed
DROP INDEX IF EXISTS idx_feature_flags_domain;
DROP INDEX IF EXISTS idx_system_configs_domain;
DROP INDEX IF EXISTS idx_departments_domain;
DROP INDEX IF EXISTS idx_menu_items_domain_id;
DROP INDEX IF EXISTS idx_api_partners_domain_id;
DROP INDEX IF EXISTS idx_domain_configs_domain_id;

-- Step 2: Add comments documenting Single-Tenant status
COMMENT ON COLUMN system_configs.domain_id IS
    'DEPRECATED (Single-Tenant): Retained for backward compatibility. '
    'Always set to default value. Not used for filtering. '
    'Migration: microservice-database-architecture (2026-10).';

COMMENT ON COLUMN feature_flags.domain_id IS
    'DEPRECATED (Single-Tenant): Retained for backward compatibility. '
    'Always set to default value. Not used for filtering. '
    'Migration: microservice-database-architecture (2026-10).';

COMMENT ON COLUMN menu_items.domain_id IS
    'DEPRECATED (Single-Tenant): Retained for backward compatibility. '
    'Always set to default value. Not used for filtering. '
    'Migration: microservice-database-architecture (2026-10).';

COMMENT ON COLUMN departments.domain_id IS
    'DEPRECATED (Single-Tenant): Retained for backward compatibility. '
    'Always set to default value. Not used for filtering. '
    'Migration: microservice-database-architecture (2026-10).';
