package com.dominions.modmerger.core.mapping

import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.domain.*
import com.dominions.modmerger.infrastructure.Logging

/**
 * Handles ID mapping for mod entities using a FIFO strategy.
 * First mod to use an ID keeps it, subsequent mods get remapped to new IDs.
 * Throws IdMappingException if runs out of available IDs in any range.
 */
class IdMapper : Logging {
    private val ranges = initializeRanges()
    private val preferredStarts = initializePreferredStarts()
    private val nextAvailableId = mutableMapOf<EntityType, Long>()

    init {
        ranges.forEach { (type, range) ->
            nextAvailableId[type] = preferredStarts[type] ?: range.first
        }
    }

    @Throws(IdMappingException::class)
    fun createMappings(modDefinitions: Map<String, ModDefinition>): Map<String, MappedModDefinition> {
        if (modDefinitions.isEmpty()) return emptyMap()

        val globalUsedIds = EntityType.entries.associateWith { mutableSetOf<Long>() }
        val result = mutableMapOf<String, MappedModDefinition>()
        val processedMods = mutableListOf<String>()

        try {
            modDefinitions.entries
                .sortedBy { it.key }
                .forEach { (modName, modDef) ->
                    info("Processing mod: $modName")
                    result[modName] = createMappingsForMod(modDef, globalUsedIds)
                    processedMods.add(modName)
                }
        } catch (e: IdMappingException) {
            throw e
        }

        return result
    }

    private fun createMappingsForMod(
        modDef: ModDefinition,
        globalUsedIds: Map<EntityType, MutableSet<Long>>
    ): MappedModDefinition {
        val mappedDef = MappedModDefinition(modDef.modFile)
        var remappedCount = 0

        EntityType.entries.forEach { type ->
            val usedIds = globalUsedIds.getValue(type)
            val entityDef = modDef.getDefinition(type)

            entityDef.definedIds.forEach { originalId ->
                when {
                    ModRanges.Validator.isVanillaId(type, originalId) -> {
                        usedIds.add(originalId)
                    }
                    usedIds.contains(originalId) -> {
                        val newId = findAvailableId(type, usedIds) ?: throw IdMappingException(buildErrorMessage(
                            type = type,
                            range = ranges.getValue(type),
                            usedIds = usedIds,
                            modName = modDef.modFile.name,
                            originalId = originalId
                        ))

                        mappedDef.addMapping(type, originalId, newId)
                        usedIds.add(newId)
                        remappedCount++

                        trace("Remapped ID for ${modDef.modFile.name}: $type $originalId -> $newId")
                    }
                    else -> {
                        usedIds.add(originalId)
                    }
                }
            }
        }

        if (remappedCount > 0) {
            debug("Remapped $remappedCount IDs for mod: ${modDef.modFile.name}")
        }

        return mappedDef
    }

    private fun findAvailableId(type: EntityType, usedIds: Set<Long>): Long? {
        val range = ranges.getValue(type)
        val preferredStart = preferredStarts[type]

        // Try preferred start first
        if (preferredStart != null && preferredStart in range && !usedIds.contains(preferredStart)) {
            return preferredStart
        }

        // Get current position
        var currentId = nextAvailableId.getValue(type)

        // Search forward from current position
        while (currentId <= range.last) {
            if (currentId in range && !usedIds.contains(currentId)) {
                nextAvailableId[type] = currentId + 1
                return currentId
            }
            currentId++
        }

        // Search from start if needed
        currentId = range.first
        val maxSearch = nextAvailableId.getValue(type)
        while (currentId < maxSearch) {
            if (!usedIds.contains(currentId)) {
                nextAvailableId[type] = currentId + 1
                return currentId
            }
            currentId++
        }

        return null
    }

    private fun buildErrorMessage(
        type: EntityType,
        range: LongRange,
        usedIds: Set<Long>,
        modName: String,
        originalId: Long
    ): String = buildString {
        append("Failed to map IDs:\n")
        append("No available IDs remaining in range for $type\n")
        append("Failed mod: $modName\n")
        append("Original ID: $originalId\n")
        append("Range: ${range.first}..${range.last}\n")
        append("Total range size: ${range.last - range.first + 1}\n")
        append("Used IDs count: ${usedIds.size}")
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
    )
}

class IdMappingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)