package com.ntt.sysadmin.client.entity

import jakarta.persistence.*
import org.hibernate.annotations.Immutable

/**
 * Read-only projection of `i18n_messages` table.
 * @Immutable disables Hibernate dirty-checking — no UPDATE/DELETE queries.
 */
@Entity
@Immutable
@Table(name = "i18n_messages")
class I18nMessageReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "code", length = 128, nullable = false)
    var code: String = ""

    @Column(name = "locale", length = 10, nullable = false)
    var locale: String = ""

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    var message: String = ""

    @Column(name = "module", length = 64, nullable = false)
    var module: String = "common"

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
}

/**
 * Read-only projection of `system_configs` table.
 */
@Entity
@Immutable
@Table(name = "system_configs")
class SystemConfigReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "config_key", length = 100, nullable = false)
    var configKey: String = ""

    @Column(name = "config_value", length = 4000, nullable = false)
    var configValue: String = ""

    @Column(name = "value_type", length = 20, nullable = false)
    var valueType: String = "STRING"

    @Column(name = "description", length = 500)
    var description: String? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true
}

/**
 * Read-only projection of `menu_items` table.
 * Maps only the columns needed for navigation tree building.
 */
@Entity
@Immutable
@Table(name = "menu_items")
class MenuReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "parent_id")
    var parentId: Long? = null

    @Column(name = "code", length = 100, nullable = false)
    var code: String = ""

    @Column(name = "name", length = 200, nullable = false)
    var name: String = ""

    @Column(name = "icon", length = 100)
    var icon: String? = null

    @Column(name = "path", length = 500)
    var path: String? = null

    @Column(name = "route_name", length = 200)
    var routeName: String? = null

    @Column(name = "component", length = 500)
    var component: String? = null

    @Column(name = "menu_type", length = 20, nullable = false)
    var menuType: String = "MENU"

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0

    @Column(name = "level", nullable = false)
    var level: Int = 0

    @Column(name = "is_visible", nullable = false)
    var isVisible: Boolean = true

    @Column(name = "is_cacheable", nullable = false)
    var isCacheable: Boolean = false

    @Column(name = "translate_key", length = 200)
    var translateKey: String? = null

    @Column(name = "status", length = 20, nullable = false)
    var status: String = "ACTIVE"
}

/**
 * Read-only projection of `feature_flags` table.
 */
@Entity
@Immutable
@Table(name = "feature_flags")
class FeatureFlagReadModel {

    @Id
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "flag_key", length = 100, nullable = false)
    var flagKey: String = ""

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = false

    @Column(name = "rollout_pct", nullable = false)
    var rolloutPct: Int = 0

    @Column(name = "description", length = 500)
    var description: String? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true
}
