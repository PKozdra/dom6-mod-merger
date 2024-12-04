// File: IdMapper.kt
package com.dominions.modmerger.core.mapping

import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.domain.*
import com.dominions.modmerger.infrastructure.Logging

/**
 * Handles ID mapping and conflict resolution for mod entities using a FIFO strategy.
 * This class focuses on the core mapping logic while delegating statistics to IdMappingStatistics.
 */
class IdMapper : Logging {
    private val ranges = initializeRanges()
    private val preferredStarts = initializePreferredStarts()
    private val statistics = IdMappingStatistics()

    fun createMappings(modDefinitions: Map<String, ModDefinition>): Map<String, MappedModDefinition> {
        if (modDefinitions.isEmpty()) return emptyMap()

        info("Starting ID mapping for ${modDefinitions.size} mods", useDispatcher = true)
        val startTime = System.currentTimeMillis()

        val state = MappingState()
        val conflicts = findAndProcessConflicts(modDefinitions)
        val vanillaModifications = findVanillaModifications(modDefinitions)

        logInitialAnalysis(conflicts, vanillaModifications)

        return modDefinitions.entries
            .sortedBy { it.key }
            .associate { (name, def) ->
                name to createMappingForMod(def, state)
            }
//            .also { mappedDefs ->
//                statistics.generateAndLogStatistics(
//                    modDefinitions,
//                    mappedDefs,
//                    conflicts,
//                    vanillaModifications,
//                    state,
//                    System.currentTimeMillis() - startTime
//                )
//            }
    }

    data class MappingState(
        val usedIds: MutableMap<EntityType, MutableSet<Long>> = mutableMapOf(),
        val currentPositions: MutableMap<EntityType, Long> = mutableMapOf(),
        val implicitCounts: MutableMap<EntityType, Int> = mutableMapOf(),
        val attemptedPreferredStarts: MutableSet<EntityType> = mutableSetOf()
    ) {
        fun initializeEntityType(type: EntityType) {
            if (!usedIds.containsKey(type)) {
                usedIds[type] = mutableSetOf()
            }
        }
    }

    private fun createMappingForMod(modDef: ModDefinition, state: MappingState): MappedModDefinition {
        val mappedDef = MappedModDefinition(modDef.modFile)
        var totalRemapped = 0
        var totalKept = 0

        EntityType.entries.forEach { type ->
            state.initializeEntityType(type)
            handleImplicitDefinitions(type, modDef, state)
            processEntityIds(type, modDef.getDefinition(type), mappedDef, state)
                .let { (remapped, kept) ->
                    totalRemapped += remapped
                    totalKept += kept
                }
        }

        if (totalRemapped > 0) {
            debug(
                "Processed ${modDef.modFile.name}: remapped $totalRemapped IDs, kept $totalKept original IDs",
                useDispatcher = false
            )
        }

        return mappedDef
    }

    private fun handleImplicitDefinitions(type: EntityType, modDef: ModDefinition, state: MappingState) {
        val implicitCount = modDef.getDefinition(type).implicitDefinitions
        if (implicitCount > 0) {
            state.implicitCounts.merge(type, implicitCount, Int::plus)
        }
    }

    private data class ProcessingResult(val remappedCount: Int, val keptCount: Int)

    private fun processEntityIds(
        type: EntityType,
        definition: EntityDefinition,
        mappedDef: MappedModDefinition,
        state: MappingState
    ): ProcessingResult {
        var remappedCount = 0
        var keptCount = 0

        definition.definedIds.forEach { id ->
            when {
                ModRanges.Validator.isVanillaId(type, id) -> {
                    keptCount++
                    state.usedIds[type]?.add(id)
                }
                state.usedIds[type]?.contains(id) == true -> {
                    val newId = getNextAvailableId(type, state)
                    mappedDef.addMapping(type, id, newId)
                    state.usedIds[type]?.add(newId)
                    remappedCount++
                }
                else -> {
                    state.usedIds[type]?.add(id)
                    keptCount++
                }
            }
        }

        return ProcessingResult(remappedCount, keptCount)
    }

    private fun getNextAvailableId(type: EntityType, state: MappingState): Long {
        val range = ranges[type] ?: throw IllegalStateException("No range defined for $type")
        val used = state.usedIds[type] ?: throw IllegalStateException("No used IDs tracking for $type")

        validateRangeCapacity(type, range, used.size, state.implicitCounts[type] ?: 0)

        return tryPreferredStart(type, range, used, state)
            ?: findNextAvailableId(type, range, used, state)
    }

    private fun validateRangeCapacity(type: EntityType, range: LongRange, usedCount: Int, implicitCount: Int) {
        val availableCount = range.last - range.first + 1 - usedCount - implicitCount
        if (availableCount <= 0) {
            throw IllegalStateException(
                "ID range exhausted for $type (${range.first}..${range.last}). " +
                        "Used: $usedCount, Implicit: $implicitCount"
            )
        }
    }

    private fun tryPreferredStart(
        type: EntityType,
        range: LongRange,
        used: Set<Long>,
        state: MappingState
    ): Long? {
        return preferredStarts[type]?.let { preferred ->
            if (!state.attemptedPreferredStarts.contains(type) &&
                preferred in range &&
                !used.contains(preferred)
            ) {
                state.attemptedPreferredStarts.add(type)
                state.currentPositions[type] = preferred + 1
                preferred
            } else null
        }
    }

    private fun findNextAvailableId(
        type: EntityType,
        range: LongRange,
        used: Set<Long>,
        state: MappingState
    ): Long {
        var current = state.currentPositions.getOrPut(type) { range.first }

        while (true) {
            if (current > range.last) {
                current = range.first
            }

            if (!used.contains(current)) {
                state.currentPositions[type] = current + 1
                return current
            }
            current++
        }
    }

    private fun findAndProcessConflicts(
        modDefinitions: Map<String, ModDefinition>
    ): Map<EntityType, List<ModConflict>> {
        val conflicts = mutableMapOf<EntityType, MutableMap<Long, MutableList<String>>>()

        modDefinitions.forEach { (modName, def) ->
            EntityType.entries.forEach { type ->
                def.getDefinition(type).definedIds
                    .filterNot { ModRanges.Validator.isVanillaId(type, it) }
                    .forEach { id ->
                        conflicts.getOrPut(type) { mutableMapOf() }
                            .getOrPut(id) { mutableListOf() }
                            .add(modName)
                    }
            }
        }

        return conflicts.mapValues { (type, idMap) ->
            idMap.filter { it.value.size > 1 }
                .map { (id, mods) ->
                    ModConflict(
                        type = type,
                        conflictingIds = setOf(ModIdentifier(id)),
                        firstMod = mods.first(),
                        secondMod = mods[1]
                    )
                }
        }.filterValues { it.isNotEmpty() }
    }

    private fun findVanillaModifications(
        modDefinitions: Map<String, ModDefinition>
    ): Map<EntityType, Map<Long, List<String>>> {
        val modifications = mutableMapOf<EntityType, MutableMap<Long, MutableList<String>>>()

        modDefinitions.forEach { (modName, def) ->
            EntityType.entries.forEach { type ->
                def.getDefinition(type).vanillaEditedIds
                    .filter { ModRanges.Validator.isVanillaId(type, it) }
                    .forEach { id ->
                        modifications.getOrPut(type) { mutableMapOf() }
                            .getOrPut(id) { mutableListOf() }
                            .add(modName)
                    }
            }
        }

        return modifications.mapValues { it.value.toMap() }
    }

    private fun logInitialAnalysis(
        conflicts: Map<EntityType, List<ModConflict>>,
        vanillaModifications: Map<EntityType, Map<Long, List<String>>>
    ) {
        if (conflicts.isNotEmpty()) {
            val totalConflicts = conflicts.values.sumOf { it.size }
            info("Found $totalConflicts ID conflicts to resolve", useDispatcher = true)
            conflicts.forEach { (type, typeConflicts) ->
                debug("$type has ${typeConflicts.size} conflicts:", useDispatcher = false)
                typeConflicts.forEach { debug("  $it", useDispatcher = false) }
            }
        }

//        vanillaModifications.forEach { (type, mods) ->
//            mods.forEach { (id, modNames) ->
//                if (modNames.size > 1) {
//                    warn(
//                        "Multiple mods modifying vanilla $type ID $id: ${modNames.joinToString()}",
//                        useDispatcher = false
//                    )
//                }
//            }
//        }
    }

    private fun initializeRanges() = ModRanges.Modding.run {
        EntityType.entries.associateWith { type ->
            when (type) {
                EntityType.WEAPON -> WEAPON_START..WEAPON_END
                EntityType.ARMOR -> ARMOR_START..ARMOR_END
                EntityType.MONSTER -> MONSTER_START..MONSTER_END
                EntityType.SPELL -> SPELL_START..SPELL_END
                EntityType.ITEM -> ITEM_START..ITEM_END
                EntityType.SITE -> SITE_START..SITE_END
                EntityType.NATION -> NATION_START..NATION_END
                EntityType.NAME_TYPE -> NAMETYPE_START..NAMETYPE_END
                EntityType.ENCHANTMENT -> ENCHANTMENT_START..ENCHANTMENT_END
                EntityType.MONTAG -> MONTAG_START..MONTAG_END
                EntityType.EVENT_CODE -> EVENTCODE_START..EVENTCODE_END
                EntityType.POPTYPE -> POPTYPE_START..POPTYPE_END
                EntityType.RESTRICTED_ITEM -> RESTRICTED_ITEM_START..RESTRICTED_ITEM_END
            }
        }
    }

    private fun initializePreferredStarts() = mapOf(
        EntityType.WEAPON to 2250L,
        EntityType.ARMOR to 1250L,
        EntityType.MONSTER to 13500L,
        EntityType.NAME_TYPE to 250L,
        EntityType.SPELL to 5750L,
        EntityType.ENCHANTMENT to 7750L,
        EntityType.ITEM to 1450L,
        EntityType.SITE to 2150L,
        EntityType.NATION to 330L,
        EntityType.POPTYPE to 205L
    )
}