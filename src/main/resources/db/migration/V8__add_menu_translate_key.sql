-- V8: Add translate_key column to menu_items table for frontend i18n support.
-- Part of dynamic-navigation-menu feature (FR-013).

ALTER TABLE menu_items ADD COLUMN translate_key VARCHAR(200);
COMMENT ON COLUMN menu_items.translate_key IS 'i18n translation key for frontend dynamic menu rendering';
