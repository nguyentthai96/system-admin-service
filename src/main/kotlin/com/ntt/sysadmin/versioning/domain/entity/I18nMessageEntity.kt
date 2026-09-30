package com.ntt.sysadmin.versioning.domain.entity

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Entity
@Table(name = "i18n_messages")
class I18nMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(length = 128, nullable = false)
    var code: String = ""

    @Column(length = 10, nullable = false)
    var locale: String = ""

    @Column(columnDefinition = "TEXT", nullable = false)
    var message: String = ""

    @Column(length = 64, nullable = false)
    var module: String = "common"

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_at", nullable = false)
    var createdAt: Long = System.currentTimeMillis()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = System.currentTimeMillis()
}

@Repository
interface I18nMessageRepository : JpaRepository<I18nMessageEntity, Long> {
    fun findByCodeAndLocale(code: String, locale: String): I18nMessageEntity?
    fun findAllByIsActiveTrue(): List<I18nMessageEntity>
}
