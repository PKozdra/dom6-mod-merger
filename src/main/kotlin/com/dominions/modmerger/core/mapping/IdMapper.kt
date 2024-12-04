package com.dominions.modmerger.core.mapping

import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.domain.*
import com.dominions.modmerger.infrastructure.Logging

/**
 * Handles ID mapping and conflict resolution for mod entities.
 * Uses a first-come-first-served strategy where:
 * - First mod to use an ID keeps it
 * - Subsequent mods that try to use the same ID get remapped to the next available ID
 *
 * This class is stateless and thread-safe. Each operation creates its own tracking state.
 */
class IdMapper() : Logging {
    private val ranges = initializeRanges()
    private val preferredStarts = initializePreferredStarts()

    fun createMappings(modDefinitions: Map<String, ModDefinition>): Map<String, MappedModDefinition> {
        if (modDefinitions.isEmpty()) return emptyMap()

        info("Starting ID mapping for ${modDefinitions.size} mods", useDispatcher = true)

        val mappingState = MappingState()
        val conflicts = findConflicts(modDefinitions)
        logConflictsIfAny(conflicts)
        logInvalidIdsIfAny(modDefinitions)

        return modDefinitions.map { (name, def) ->
            name to createMappingForMod(def, mappingState)
        }.toMap()
    }

    private data class MappingState(
        val usedIds: MutableMap<EntityType, MutableSet<Long>> = EntityType.entries.associateWithTo(mutableMapOf()) { mutableSetOf() },
        val currentIds: MutableMap<EntityType, Long> = mutableMapOf(),
        val remappedTargetIds: MutableMap<EntityType, MutableSet<Long>> = EntityType.entries.associateWithTo(
            mutableMapOf()
        ) { mutableSetOf() },
        val attemptedPreferredStart: MutableSet<EntityType> = mutableSetOf()
    )

    private data class MappingResult(
        val mappedDefinition: MappedModDefinition,
        val remappedCount: Int,
        val keptCount: Int
    )

    private fun createMappingForMod(modDef: ModDefinition, state: MappingState): MappedModDefinition {
        val result = EntityType.entries.fold(
            MappingResult(
                mappedDefinition = MappedModDefinition(modDef.modFile),
                remappedCount = 0,
                keptCount = 0
            )
        ) { acc, type ->
            processEntityType(type, modDef.getDefinition(type), acc, state)
        }

        if (result.remappedCount > 0) {
            info("Processed ${modDef.modFile.name}: remapped ${result.remappedCount} IDs, kept ${result.keptCount} original IDs"
            )
        }

        return result.mappedDefinition
    }

    private fun processEntityType(
        type: EntityType,
        definition: EntityDefinition,
        result: MappingResult,
        state: MappingState
    ): MappingResult {
        var remappedCount = result.remappedCount
        var keptCount = result.keptCount

        definition.definedIds.forEach { id ->
            when {
                state.usedIds[type]?.contains(id) == true -> {
                    val newId = getNextAvailableId(type, state)
                    result.mappedDefinition.addMapping(type, id, newId)
                    state.usedIds[type]?.add(newId)
                    remappedCount++
                }

                else -> {
                    state.usedIds[type]?.add(id)
                    keptCount++
                }
            }
        }

        return result.copy(
            remappedCount = remappedCount,
            keptCount = keptCount
        )
    }

    private fun getNextAvailableId(type: EntityType, state: MappingState): Long {
        val range = ranges[type] ?: throw IllegalStateException("No range defined for $type")
        val used = state.usedIds[type] ?: throw IllegalStateException("No used IDs tracking for $type")
        val remappedTargets =
            state.remappedTargetIds[type] ?: throw IllegalStateException("No remapped targets tracking for $type")

        // Try preferred start first if not already attempted
        val preferredStart = preferredStarts[type]
        if (preferredStart != null && !state.attemptedPreferredStart.contains(type)) {
            state.attemptedPreferredStart.add(type)
            state.currentIds[type] = preferredStart
        }

        // Get next sequential ID that hasn't been used as source or target
        var nextId = state.currentIds.getOrPut(type) { range.first }

        // If we're past range end, loop back to start of valid modding range
        if (nextId > range.last) {
            nextId = range.first
        }

        // Find next available ID, wrapping around if necessary
        val startingId = nextId
        do {
            if (!used.contains(nextId) && !remappedTargets.contains(nextId)) {
                state.currentIds[type] = nextId + 1
                remappedTargets.add(nextId)
                return nextId
            }
            nextId++
            if (nextId > range.last) {
                nextId = range.first
            }
        } while (nextId != startingId)

        throw IllegalStateException("ID range exhausted for $type (${range.first}..${range.last})")
    }

    private data class ConflictTracking(
        val idOwners: MutableMap<EntityType, MutableMap<Long, String>> = mutableMapOf(),
        val conflicts: MutableMap<EntityType, MutableSet<ModConflict>> = mutableMapOf()
    )

    private fun findConflicts(
        modDefinitions: Map<String, ModDefinition>
    ): Map<EntityType, Set<ModConflict>> {
        return modDefinitions.entries.fold(ConflictTracking()) { tracking, (modName, def) ->
            processModConflicts(modName, def, tracking)
        }.conflicts
    }

    private fun processModConflicts(
        modName: String,
        def: ModDefinition,
        tracking: ConflictTracking
    ): ConflictTracking {
        EntityType.entries.forEach { type ->
            val owners = tracking.idOwners.getOrPut(type) { mutableMapOf() }
            val conflictingIds = def.getDefinition(type).definedIds.filter { id ->
                owners[id] != null
            }.toSet()

            if (conflictingIds.isNotEmpty()) {
                tracking.conflicts.getOrPut(type) { mutableSetOf() }.add(
                    ModConflict(
                        type = type,
                        conflictingIds = conflictingIds.map(::ModIdentifier).toSet(),
                        firstMod = owners.getValue(conflictingIds.first()),
                        secondMod = modName
                    )
                )
            }

            def.getDefinition(type).definedIds.forEach { id ->
                if (owners[id] == null) owners[id] = modName
            }
        }
        return tracking
    }

    private fun logConflictsIfAny(conflicts: Map<EntityType, Set<ModConflict>>) {
        if (conflicts.isNotEmpty()) {
            val totalConflicts = conflicts.values.sumOf { it.size }
            debug("Found $totalConflicts ID conflicts between mods:")
            conflicts.forEach { (type, typeConflicts) ->
                debug("$type conflicts:")
                typeConflicts.forEach { conflict ->
                    debug("$conflict")
                }
            }
        }
    }

    private fun logInvalidIdsIfAny(modDefinitions: Map<String, ModDefinition>) {
        modDefinitions.forEach { (name, def) ->
            EntityType.entries.forEach { type ->
                val invalidIds = def.getDefinition(type).definedIds.filter { id ->
                    !ModRanges.Validator.isValidModdingId(type, id)
                }
                if (invalidIds.isNotEmpty()) {
                    warn("Mod $name has invalid $type IDs: $invalidIds")
                }
            }
        }
    }

    private fun initializeRanges() = ModRanges.Modding.run {
        mapOf(
            EntityType.WEAPON to (WEAPON_START..WEAPON_END),
            EntityType.ARMOR to (ARMOR_START..ARMOR_END),
            EntityType.MONSTER to (MONSTER_START..MONSTER_END),
            EntityType.SPELL to (SPELL_START..SPELL_END),
            EntityType.ITEM to (ITEM_START..ITEM_END),
            EntityType.SITE to (SITE_START..SITE_END),
            EntityType.NATION to (NATION_START..NATION_END),
            EntityType.NAME_TYPE to (NAMETYPE_START..NAMETYPE_END),
            EntityType.ENCHANTMENT to (ENCHANTMENT_START..ENCHANTMENT_END),
            EntityType.MONTAG to (MONTAG_START..MONTAG_END),
            EntityType.EVENT_CODE to (EVENTCODE_START..EVENTCODE_END),
            EntityType.POPTYPE to (POPTYPE_START..POPTYPE_END),
            EntityType.RESTRICTED_ITEM to (RESTRICTED_ITEM_START..RESTRICTED_ITEM_END)
        )
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
        // MONTAG and RESTRICTED_ITEM use default ranges
    )
}