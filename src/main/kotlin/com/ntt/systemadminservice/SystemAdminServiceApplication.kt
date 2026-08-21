package com.ntt.systemadminservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import com.ntt.basecore.autoconfigure.data.DataProperties

@SpringBootApplication
@EnableConfigurationProperties(DataProperties::class)
class SystemAdminServiceApplication

fun main(args: Array<String>) {
	runApplication<SystemAdminServiceApplication>(*args)
}
