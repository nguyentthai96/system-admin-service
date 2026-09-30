package com.ntt.systemadminservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import com.ntt.basecore.autoconfigure.data.DataProperties

@SpringBootApplication(scanBasePackages = ["com.ntt.systemadminservice", "com.ntt.sysadmin.versioning", "com.ntt.sysadmin.shared"])
@EnableJpaRepositories(basePackages = ["com.ntt.sysadmin.versioning", "com.ntt.sysadmin.menu", "com.ntt.sysadmin.tenant", "com.ntt.sysadminservice"])
@EntityScan(basePackages = ["com.ntt.sysadmin.versioning", "com.ntt.sysadmin.menu", "com.ntt.sysadmin.tenant", "com.ntt.sysadminservice"])
@EnableConfigurationProperties(DataProperties::class)
class SystemAdminServiceApplication

fun main(args: Array<String>) {
	runApplication<SystemAdminServiceApplication>(*args)
}
