// src/main/kotlin/com/dominions/modmerger/core/mapping/IdMapper.kt
package com.dominions.modmerger.core.mapping

import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.ModDefinition
import mu.KLogger
import mu.KotlinLogging

class IdMapper(private val modDefinitions: Map<String, ModDefinition>) {
    private val logger: KLogger = KotlinLogging.logger { }
    private val idGenerators = mutableMapOf<EntityType, IdGenerator>()

    init {
        initializeIdGenerators()
    }

    private fun initializeIdGenerators() {
        with(ModRanges.Modding) {
            idGenerators[EntityType.WEAPON] = IdGenerator(WEAPON_START, WEAPON_END)
            idGenerators[EntityType.ARMOR] = IdGenerator(ARMOR_START, ARMOR_END)
            idGenerators[EntityType.MONSTER] = IdGenerator(MONSTER_START, MONSTER_END)
            idGenerators[EntityType.SPELL] = IdGenerator(SPELL_START, SPELL_END)
            idGenerators[EntityType.ITEM] = IdGenerator(ITEM_START, ITEM_END)
            idGenerators[EntityType.SITE] = IdGenerator(SITE_START, SITE_END)
            idGenerators[EntityType.NATION] = IdGenerator(NATION_START, NATION_END)
            idGenerators[EntityType.NAME_TYPE] = IdGenerator(NAMETYPE_START, NAMETYPE_END)
            idGenerators[EntityType.ENCHANTMENT] = IdGenerator(ENCHANTMENT_START, ENCHANTMENT_END)
            idGenerators[EntityType.MONTAG] = IdGenerator(MONTAG_START, MONTAG_END)
        }
        logger.debug { "Initialized ID Generators for entity types: ${idGenerators.keys}" }
    }

    fun createMappings(modDefinitions: Map<String, ModDefinition>): Map<String, MappedModDefinition> {
        logger.info { "Starting ID mapping for ${modDefinitions.size} mod(s)" }
        validateAllIds(modDefinitions)
        val conflicts = findAllConflicts(modDefinitions)

        conflicts.forEach { (modName, typeConflicts) ->
            typeConflicts.forEach { (type, ids) ->
                logger.info { "Found conflicts in mod $modName for $type: $ids" }
            }
        }

        return modDefinitions.mapValues { (modName, modDef) ->
            logger.debug { "Creating mapped definition for mod: $modName" }
            val mappedDef = createMappedDefinition(modDef, conflicts[modName] ?: emptyMap())
            logger.debug { "Finished creating mapped definition for mod: $modName" }
            mappedDef
        }
    }

    private fun createMappedDefinition(
        modDef: ModDefinition,
        conflicts: Map<EntityType, Set<Long>>
    ): MappedModDefinition {
        val mappedDef = MappedModDefinition(modDef.modFile)
        val modName = modDef.modFile.name

        EntityType.entries.forEach { type ->
            val definition = modDef.getDefinition(type)
            val conflictIds = conflicts[type] ?: emptySet()

            definition.definedIds.forEach { originalId ->
                if (originalId in conflictIds) {
                    val newId = idGenerators[type]?.nextId()
                        ?: throw IllegalStateException("No ID generator for type $type")
                    mappedDef.addMapping(type, originalId, newId)
                    logger.debug { "Remapped conflicting $type ID $originalId to $newId for mod: $modName" }
                } else {
                    // No conflict, keep original ID
                    mappedDef.addMapping(type, originalId, originalId)
                    logger.trace { "Kept original $type ID $originalId for mod: $modName" }
                }
            }
        }

        return mappedDef
    }

    private fun validateAllIds(modDefinitions: Map<String, ModDefinition>) {
        modDefinitions.forEach { (modName, modDef) ->
            logger.debug { "Validating IDs for mod: $modName" }
            EntityType.entries.forEach { type ->
                val definition = modDef.getDefinition(type)
                definition.definedIds.forEach { id ->
                    if (!ModRanges.Validator.isValidModdingId(type, id)) {
                        logger.warn("Invalid modding ID in $modName: ${type.name} ID $id is outside valid range")
                    }
                }
                definition.vanillaEditedIds.forEach { id ->
                    if (!ModRanges.Validator.isVanillaId(type, id)) {
                        logger.warn("Invalid vanilla ID in $modName: ${type.name} ID $id is outside vanilla range")
                    }
                }
            }
        }
    }

    private fun findAllConflicts(modDefinitions: Map<String, ModDefinition>): Map<String, Map<EntityType, Set<Long>>> {
        val conflictsByMod = mutableMapOf<String, MutableMap<EntityType, MutableSet<Long>>>()

        // Add more detailed logging
        logger.debug { "Starting conflict detection for mods: ${modDefinitions.keys}" }

        val mods = modDefinitions.values.toList()

        for (i in mods.indices) {
            val modA = mods[i]
            for (j in i + 1 until mods.size) {
                val modB = mods[j]
                logger.debug { "Checking conflicts between ${modA.modFile.name} and ${modB.modFile.name}" }

                val modConflicts = modA.findConflicts(modB)

                // Add more detailed logging for conflicts found
                if (modConflicts.isNotEmpty()) {
                    logger.debug { "Found ${modConflicts.size} conflicts between ${modA.modFile.name} and ${modB.modFile.name}" }
                }

                modConflicts.forEach { conflict ->
                    val type = conflict.type
                    val conflictingIds = conflict.conflictingIds.map { it.value }.toSet()

                    // Add conflicts to modA
                    conflictsByMod.getOrPut(modA.modFile.name) { mutableMapOf() }
                        .getOrPut(type) { mutableSetOf() }
                        .addAll(conflictingIds)

                    // Add conflicts to modB
                    conflictsByMod.getOrPut(modB.modFile.name) { mutableMapOf() }
                        .getOrPut(type) { mutableSetOf() }
                        .addAll(conflictingIds)
                }
            }
        }

        logger.info { "Total conflicts detected by mod: ${conflictsByMod.mapValues { it.value.mapValues { it.value.size } }}" }
        return conflictsByMod
    }

    private inner class IdGenerator(
        private var currentId: Long,
        private val maxId: Long
    ) {
        fun nextId(): Long {
            if (currentId > maxId) {
                throw IdRangeExhaustedException("No more IDs available in range $currentId..$maxId")
            }
            val id = currentId++
            logger.trace { "Generated new ID $id" }
            return id
        }
    }

    class IdRangeExhaustedException(message: String) : RuntimeException(message)
}
