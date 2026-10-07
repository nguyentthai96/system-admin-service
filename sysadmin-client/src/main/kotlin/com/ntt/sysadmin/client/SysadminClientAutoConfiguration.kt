package com.ntt.sysadmin.client

import com.ntt.basebusiness.shared.IConfigReader
import com.ntt.basebusiness.shared.IFeatureFlagReader
import com.ntt.basebusiness.shared.II18nReader
import com.ntt.basebusiness.shared.IMenuReader
import com.ntt.sysadmin.client.entity.I18nMessageReadModel
import com.ntt.sysadmin.client.repository.FeatureFlagReadRepository
import com.ntt.sysadmin.client.repository.I18nMessageReadRepository
import com.ntt.sysadmin.client.repository.MenuReadRepository
import com.ntt.sysadmin.client.repository.SystemConfigReadRepository
import com.ntt.sysadmin.client.service.JpaConfigReader
import com.ntt.sysadmin.client.service.JpaFeatureFlagReader
import com.ntt.sysadmin.client.service.JpaI18nReader
import com.ntt.sysadmin.client.service.JpaMenuReader
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Zero-config auto-configuration for sysadmin-client.
 *
 * Activated by default (`app.sysadmin.client.enabled=true`).
 * Consumer services just add the dependency — beans are registered automatically.
 *
 * Uses `basePackageClasses` to scope entity/repository scanning precisely
 * and avoid conflicts with the consumer's own EntityManagerFactory.
 *
 * Each bean uses @ConditionalOnMissingBean, allowing override for
 * ENTERPRISE profile (secondary DataSource) scenarios.
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "app.sysadmin.client",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@EntityScan(basePackageClasses = [I18nMessageReadModel::class])
@EnableJpaRepositories(basePackageClasses = [I18nMessageReadRepository::class])
class SysadminClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(II18nReader::class)
    fun jpaI18nReader(repository: I18nMessageReadRepository): II18nReader {
        return JpaI18nReader(repository)
    }

    @Bean
    @ConditionalOnMissingBean(IConfigReader::class)
    fun jpaConfigReader(repository: SystemConfigReadRepository): IConfigReader {
        return JpaConfigReader(repository)
    }

    @Bean
    @ConditionalOnMissingBean(IMenuReader::class)
    fun jpaMenuReader(repository: MenuReadRepository): IMenuReader {
        return JpaMenuReader(repository)
    }

    @Bean
    @ConditionalOnMissingBean(IFeatureFlagReader::class)
    fun jpaFeatureFlagReader(repository: FeatureFlagReadRepository): IFeatureFlagReader {
        return JpaFeatureFlagReader(repository)
    }
}
