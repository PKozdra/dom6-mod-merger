package com.dominions.modmerger.core.mapping

import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.domain.*
import mu.KotlinLogging

/**
 * Handles ID mapping and conflict resolution for mod entities.
 * Uses a first-come-first-served strategy where:
 * - First mod to use an ID keeps it
 * - Subsequent mods that try to use the same ID get remapped to the next available ID
 */
class IdMapper(private val logDispatcher: LogDispatcher) {
    private val logger = KotlinLogging.logger {}
    private val ranges = initializeRanges()
    private val usedIds = EntityType.entries.associateWithTo(mutableMapOf()) { mutableSetOf<Long>() }
    private val currentIds = ranges.mapValuesTo(mutableMapOf()) { it.value.first }

    fun createMappings(modDefinitions: Map<String, ModDefinition>): Map<String, MappedModDefinition> {
        if (modDefinitions.isEmpty()) return emptyMap()

        log(LogLevel.INFO, "Starting ID mapping for ${modDefinitions.size} mods")

        val conflicts = findConflicts(modDefinitions)
        logConflictsIfAny(conflicts)
        logInvalidIdsIfAny(modDefinitions)

        return modDefinitions.map { (name, def) ->
            name to createMappingForMod(def)
        }.toMap()
    }

    private fun createMappingForMod(modDef: ModDefinition): MappedModDefinition {
        val mappedDef = MappedModDefinition(modDef.modFile)
        var remappedCount = 0
        var keptCount = 0

        EntityType.entries.forEach { type ->
            modDef.getDefinition(type).definedIds.forEach { id ->
                when {
                    usedIds[type]?.contains(id) == true -> {
                        // Only create mapping when we need to remap
                        val newId = getNextAvailableId(type)
                        mappedDef.addMapping(type, id, newId)
                        usedIds[type]?.add(newId)
                        remappedCount++
                    }
                    else -> {
                        // Don't create mapping when ID stays the same
                        usedIds[type]?.add(id)
                        keptCount++
                    }
                }
            }
        }

        if (remappedCount > 0) {
            log(LogLevel.INFO, "Processed ${modDef.modFile.name}: remapped $remappedCount IDs, kept $keptCount original IDs")
        }

        return mappedDef
    }

    private fun findConflicts(
        modDefinitions: Map<String, ModDefinition>
    ): Map<EntityType, Set<ModConflict>> {
        val idOwners = mutableMapOf<EntityType, MutableMap<Long, String>>()
        val conflicts = mutableMapOf<EntityType, MutableSet<ModConflict>>()

        modDefinitions.forEach { (modName, def) ->
            EntityType.entries.forEach { type ->
                val owners = idOwners.getOrPut(type) { mutableMapOf() }
                val conflictingIds = def.getDefinition(type).definedIds.filter { id ->
                    owners[id] != null
                }.toSet()

                if (conflictingIds.isNotEmpty()) {
                    conflicts.getOrPut(type) { mutableSetOf() }.add(
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
        }

        return conflicts
    }

    private fun getNextAvailableId(type: EntityType): Long {
        val range = ranges[type] ?: throw IllegalStateException("No range defined for $type")
        val used = usedIds[type] ?: throw IllegalStateException("No used IDs tracking for $type")

        // First try to find a gap in the already used range
        for (id in range.first..currentIds.getValue(type)) {
            if (!used.contains(id)) {
                return id
            }
        }

        // If no gaps found, try to get next sequential ID
        val nextId = currentIds.getValue(type)
        if (nextId <= range.last) {
            currentIds[type] = nextId + 1
            return nextId
        }

        // If we get here, we need to do a full range scan as a last resort
        for (id in range.first..range.last) {
            if (!used.contains(id)) {
                return id
            }
        }

        throw IllegalStateException("ID range exhausted for $type (${range.first}..${range.last})")
    }

    private fun logConflictsIfAny(conflicts: Map<EntityType, Set<ModConflict>>) {
        if (conflicts.isNotEmpty()) {
            val totalConflicts = conflicts.values.sumOf { it.size }
            log(LogLevel.DEBUG, "Found $totalConflicts ID conflicts between mods:")
            conflicts.forEach { (type, typeConflicts) ->
                log(LogLevel.DEBUG, "$type conflicts:")
                typeConflicts.forEach { conflict ->
                    log(LogLevel.DEBUG, "$conflict")
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
                    log(LogLevel.WARN, "Mod $name has invalid $type IDs: $invalidIds")
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

    private fun log(level: LogLevel, message: String) {
        logger.info { message }
        logDispatcher.log(level, message)
    }
}