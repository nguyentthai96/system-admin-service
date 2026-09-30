package com.ntt.sysadmin.versioning.domain

import com.ntt.basecore.autoconfigure.file.imports.TableImportHandler
import org.springframework.stereotype.Component

/**
 * Central registry discovering and indexing all TableImportHandler beans in the application context.
 * Used by ConfigManagementController to dispatch import operations to the correct handler.
 */
@Component
class ImportHandlerRegistry(
    private val handlers: List<TableImportHandler<*>>
) {
    private val handlerMap: Map<String, TableImportHandler<*>> = handlers.associateBy { it.tableName }

    fun getHandler(tableName: String): TableImportHandler<*>? {
        return handlerMap[tableName]
    }

    fun getAllHandlers(): List<TableImportHandler<*>> = handlers

    fun getRegisteredTableNames(): Set<String> = handlerMap.keys
}
