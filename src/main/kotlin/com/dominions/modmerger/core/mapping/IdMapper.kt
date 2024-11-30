// src/main/kotlin/com/dominions/modmerger/core/mapping/IdMapper.kt
package com.dominions.modmerger.core.mapping

import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.domain.*
import mu.KLogger
import mu.KotlinLogging

/**
 * Handles ID mapping and conflict resolution for mod entities.
 * Ensures unique IDs are assigned when conflicts occur between mods.
 */
class IdMapper(private val logDispatcher: LogDispatcher) {
    private val logger: KLogger = KotlinLogging.logger {}
    private val idGenerators = mutableMapOf<EntityType, IdGenerator>()

    init {
        initializeIdGenerators()
    }

    private fun initializeIdGenerators() {
        ModRanges.Modding.run {
            idGenerators[EntityType.WEAPON] = IdGenerator(WEAPON_START..WEAPON_END, "Weapon")
            idGenerators[EntityType.ARMOR] = IdGenerator(ARMOR_START..ARMOR_END, "Armor")
            idGenerators[EntityType.MONSTER] = IdGenerator(MONSTER_START..MONSTER_END, "Monster")
            idGenerators[EntityType.SPELL] = IdGenerator(SPELL_START..SPELL_END, "Spell")
            idGenerators[EntityType.ITEM] = IdGenerator(ITEM_START..ITEM_END, "Item")
            idGenerators[EntityType.SITE] = IdGenerator(SITE_START..SITE_END, "Site")
            idGenerators[EntityType.NATION] = IdGenerator(NATION_START..NATION_END, "Nation")
            idGenerators[EntityType.NAME_TYPE] = IdGenerator(NAMETYPE_START..NAMETYPE_END, "Name Type")
            idGenerators[EntityType.ENCHANTMENT] = IdGenerator(ENCHANTMENT_START..ENCHANTMENT_END, "Enchantment")
            idGenerators[EntityType.MONTAG] = IdGenerator(MONTAG_START..MONTAG_END, "MonTag")
        }
        log(LogLevel.DEBUG, "Initialized ID generators for: ${idGenerators.keys.joinToString()}")
    }

    fun createMappings(modDefinitions: Map<String, ModDefinition>): Map<String, MappedModDefinition> {
        if (modDefinitions.isEmpty()) {
            log(LogLevel.WARN, "No mod definitions provided for mapping")
            return emptyMap()
        }

        log(LogLevel.INFO, "Starting ID mapping for ${modDefinitions.size} mod(s)")
        val validationIssues = validateAllIds(modDefinitions)
        if (validationIssues.isNotEmpty()) {
            logValidationIssues(validationIssues)
        }

        val conflicts = findAllConflicts(modDefinitions)
        logConflicts(conflicts)

        return modDefinitions.mapValues { (modName, modDef) ->
            createMappedDefinition(modDef, conflicts[modName] ?: emptyMap())
        }
    }

    private fun validateAllIds(modDefinitions: Map<String, ModDefinition>): List<ValidationIssue> {
        log(LogLevel.DEBUG, "Starting ID validation")
        val issues = mutableListOf<ValidationIssue>()

        modDefinitions.forEach { (modName, modDef) ->
            EntityType.entries.forEach { type ->
                val definition = modDef.getDefinition(type)
                val definedIds = definition.definedIds

                if (definedIds.isNotEmpty()) {
                    log(LogLevel.DEBUG, "Validating $type IDs for mod $modName: ${definedIds.size} definitions")
                }

                definedIds.forEach { id ->
                    if (!ModRanges.Validator.isValidModdingId(type, id)) {
                        issues.add(ValidationIssue(modName, type, id, "ID outside valid modding range"))
                    }
                }

                definition.vanillaEditedIds.forEach { id ->
                    if (!ModRanges.Validator.isVanillaId(type, id)) {
                        issues.add(ValidationIssue(modName, type, id, "Invalid vanilla ID modification"))
                    }
                }
            }
        }

        return issues
    }

    private fun logValidationIssues(issues: List<ValidationIssue>) {
        if (issues.isNotEmpty()) {
            log(LogLevel.WARN, "Found ${issues.size} validation issues:")
            issues.forEach { issue ->
                log(LogLevel.WARN, "- ${issue.modName}: ${issue.entityType} ID ${issue.id} - ${issue.message}")
            }
        }
    }

    private fun findAllConflicts(modDefinitions: Map<String, ModDefinition>): Map<String, Map<EntityType, Set<Long>>> {
        val conflictsByMod = mutableMapOf<String, MutableMap<EntityType, MutableSet<Long>>>()
        val mods = modDefinitions.values.toList()

        log(LogLevel.DEBUG, "Analyzing conflicts between ${mods.size} mods")

        for (i in mods.indices) {
            val modA = mods[i]
            for (j in i + 1 until mods.size) {
                val modB = mods[j]
                findConflictsBetweenMods(modA, modB, conflictsByMod)
            }
        }

        return conflictsByMod
    }

    private fun findConflictsBetweenMods(
        modA: ModDefinition,
        modB: ModDefinition,
        conflictsByMod: MutableMap<String, MutableMap<EntityType, MutableSet<Long>>>
    ) {
        val conflicts = modA.findConflicts(modB)
        if (conflicts.isNotEmpty()) {
            conflicts.forEach { conflict ->
                val type = conflict.type
                val conflictingIds = conflict.conflictingIds.map { it.value }.toSet()

                // Record conflicts for both mods
                arrayOf(
                    modA.modFile.name to modB.modFile.name,
                    modB.modFile.name to modA.modFile.name
                ).forEach { (mod1, mod2) ->
                    conflictsByMod.getOrPut(mod1) { mutableMapOf() }
                        .getOrPut(type) { mutableSetOf() }
                        .addAll(conflictingIds)

                    log(LogLevel.DEBUG, "Found ${conflictingIds.size} $type conflicts between $mod1 and $mod2")
                }
            }
        }
    }

    private fun logConflicts(conflicts: Map<String, Map<EntityType, Set<Long>>>) {
        if (conflicts.isEmpty()) {
            log(LogLevel.INFO, "No ID conflicts found between mods")
            return
        }

        log(LogLevel.INFO, "ID conflicts found:")
        conflicts.forEach { (modName, typeConflicts) ->
            if (typeConflicts.isNotEmpty()) {
                log(LogLevel.INFO, "Conflicts in mod $modName:")
                typeConflicts.forEach { (type, ids) ->
                    log(LogLevel.INFO, "- $type: ${ids.size} conflicts.")
                    log(LogLevel.DEBUG, "$type conflict IDs: ${ids.joinToString()}")
                }
            }
        }
    }

    private fun createMappedDefinition(
        modDef: ModDefinition,
        conflicts: Map<EntityType, Set<Long>>
    ): MappedModDefinition {
        val mappedDef = MappedModDefinition(modDef.modFile)
        val modName = modDef.modFile.name
        val stats = mutableMapOf<EntityType, MappingStats>()

        log(LogLevel.DEBUG, "Creating mapped definition for $modName")

        EntityType.entries.forEach { type ->
            val definition = modDef.getDefinition(type)
            val conflictIds = conflicts[type] ?: emptySet()
            val typeStats = processEntityType(type, definition, conflictIds, mappedDef)
            stats[type] = typeStats
        }

        logMappingStats(modName, stats)
        return mappedDef
    }

    private fun processEntityType(
        type: EntityType,
        definition: EntityDefinition,
        conflictIds: Set<Long>,
        mappedDef: MappedModDefinition
    ): MappingStats {
        val stats = MappingStats()

        definition.definedIds.forEach { originalId ->
            try {
                if (originalId in conflictIds) {
                    remapId(type, originalId, mappedDef, stats)
                } else {
                    keepOriginalId(type, originalId, mappedDef, stats)
                }
            } catch (e: IdRangeExhaustedException) {
                val msg = "Failed to remap ID: ${e.message}"
                log(LogLevel.ERROR, msg)
                throw e
            }
        }

        return stats
    }

    private fun remapId(type: EntityType, originalId: Long, mappedDef: MappedModDefinition, stats: MappingStats) {
        val generator = idGenerators[type] ?: throw IllegalStateException("No ID generator for type $type")
        val newId = generator.nextId()
        mappedDef.addMapping(type, originalId, newId)
        stats.remapped++
        log(LogLevel.DEBUG, "Remapped $type ID $originalId → $newId")
    }

    private fun keepOriginalId(
        type: EntityType,
        originalId: Long,
        mappedDef: MappedModDefinition,
        stats: MappingStats
    ) {
        mappedDef.addMapping(type, originalId, originalId)
        stats.kept++
        logger.debug { "Kept original $type ID $originalId" }
    }

    private fun logMappingStats(modName: String, stats: Map<EntityType, MappingStats>) {
        val remappedTypes = stats.filter { it.value.remapped > 0 }
        if (remappedTypes.isNotEmpty()) {
            log(LogLevel.INFO, "Mapping statistics for $modName:")
            remappedTypes.forEach { (type, stat) ->
                log(LogLevel.INFO, "- $type: ${stat.remapped} remapped, ${stat.kept} kept original IDs")
            }
        }
    }

    private fun log(level: LogLevel, message: String) {
        logger.info(message)  // Keep internal logging for debugging
        logDispatcher.log(level, message)  // Display to user
    }

    private data class ValidationIssue(
        val modName: String,
        val entityType: EntityType,
        val id: Long,
        val message: String
    )

    private data class MappingStats(
        var remapped: Int = 0,
        var kept: Int = 0
    )

    private inner class IdGenerator(
        private val range: LongRange,
        private val entityName: String
    ) {
        private var currentId = range.first
        private var idsGenerated = 0

        fun nextId(): Long {
            if (currentId > range.last) {
                throw IdRangeExhaustedException(
                    """
                    |$entityName ID range exhausted:
                    |- Range: ${range.first}..${range.last}
                    |- Total available: ${range.last - range.first + 1}
                    |- Generated: $idsGenerated
                    |- Last attempted ID: $currentId
                    """.trimMargin()
                )
            }
            idsGenerated++
            return currentId++
        }
    }

    class IdRangeExhaustedException(message: String) : RuntimeException(message)
}