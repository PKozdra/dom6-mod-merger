package com.dominions.modmerger.domain

// ModGroupRegistry.kt
class ModGroupRegistry {
    private val groups = mutableListOf<ModGroup>()

    init {
        // Add default groups
        addGroup(ModGroup(
            id = "warhammer",
            name = "Sombre Warhammer",
            description = "Sombre Warhammer mod collection, will be treated as single mod when merging.",
            autoGrouping = { mod ->
                mod.name.contains("Warhammer", ignoreCase = true) ||
                        mod.modName.contains("Warhammer", ignoreCase = true)
            }
        ))
    }

    fun addGroup(group: ModGroup) {
        groups.add(group)
    }

    fun getGroups(): List<ModGroup> = groups.toList()

    fun findGroupForMod(mod: ModFile): ModGroup? =
        groups.firstOrNull { it.autoGrouping(mod) }
}