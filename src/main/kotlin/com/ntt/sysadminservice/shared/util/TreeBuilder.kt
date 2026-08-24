package com.ntt.sysadminservice.shared.util

/**
 * Utility for building hierarchical tree structures and detecting cycles.
 * Used by department tree, menu tree.
 */
object TreeBuilder {

    const val MAX_DEPTH = 10

    /**
     * Build a tree from a flat list of nodes.
     */
    fun <T> buildTree(
        nodes: List<T>,
        getId: (T) -> Long?,
        getParentId: (T) -> Long?,
        setChildren: (T, List<T>) -> Unit
    ): List<T> {
        val nodeMap = nodes.associateBy { getId(it) }
        val roots = mutableListOf<T>()

        nodes.forEach { node ->
            val parentId = getParentId(node)
            if (parentId == null || !nodeMap.containsKey(parentId)) {
                roots.add(node)
            } else {
                val parent = nodeMap[parentId]
                if (parent != null) {
                    val children = mutableListOf<T>()
                    setChildren(parent, children + node)
                }
            }
        }
        return roots
    }

    /**
     * Detect circular reference in a tree using DFS.
     * Returns true if cycle detected.
     */
    fun detectCycle(nodeId: Long, parentId: Long?, getParent: (Long) -> Long?): Boolean {
        val visited = mutableSetOf<Long>()
        var current = parentId
        while (current != null) {
            if (current == nodeId || !visited.add(current)) {
                return true
            }
            if (visited.size > MAX_DEPTH) {
                return true // exceed max depth = treat as cycle
            }
            current = getParent(current)
        }
        return false
    }

    /**
     * Calculate tree path string (e.g., "/1/5/12/").
     */
    fun calculateTreePath(nodeId: Long, parentPath: String?): String {
        return if (parentPath.isNullOrBlank()) "/$nodeId/" else "$parentPath$nodeId/"
    }

    /**
     * Validate max depth constraint.
     */
    fun validateMaxDepth(level: Int) {
        if (level > MAX_DEPTH) {
            throw IllegalStateException("SYS_018: Maximum tree depth ($MAX_DEPTH) exceeded at level $level")
        }
    }
}
