package com.ntt.sysadmin.menu

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/menus")
class MenuController {

    @GetMapping("/tree")
    fun getMenuTree(): Map<String, Any> {
        // TODO: Implement fetching full menu tree from DB
        return mapOf("menus" to emptyList<Any>())
    }

    @GetMapping("/user-tree")
    fun getUserMenuTree(): Map<String, Any> {
        // TODO: Implement fetching accessible menu for current user, cache with Redis
        return mapOf("menus" to emptyList<Any>())
    }
}
