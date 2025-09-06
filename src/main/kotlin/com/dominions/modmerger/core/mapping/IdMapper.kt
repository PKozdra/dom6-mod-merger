package com.dominions.modmerger.core.mapping

import com.dominions.modmerger.domain.*
import com.dominions.modmerger.infrastructure.Logging

/**
 * Maps mod entity IDs while preserving sequential relationships critical for shape-changing commands.
 *
 * Uses first-come-first-served processing: the first mod to claim an ID keeps it, subsequent
 * mods with conflicts get remapped. When remapping occurs, consecutive ID blocks are preserved
 * as units to maintain relative positioning that commands like #growhp and #shrinkhp depend on.
 */
class IdMapper : Logging {

    /**
     * Represents a sequence of consecutive IDs that must be remapped as a unit.
     * Preserves the relative positioning essential for transformation commands.
     */
    private data class ConsecutiveBlock(val ids: List<Long>) {
        val first: Long get() = ids.first()
        val size: Int get() = ids.size

        override fun toString(): String = when (size) {
            1 -> first.toString()
            else -> "$first-${ids.last()}"
        }
    }

    /**
     * Creates ID mappings for all mods while preserving sequential relationships.
     * Mods are processed alphabetically to ensure deterministic results.
     */
    fun createMappings(
        modDefinitions: Map<String, ModDefinition>,
        idManager: IdManager
    ): Map<String, MappedModDefinition> {
        return modDefinitions
            .toSortedMap()
            .mapValues { (modName, modDef) ->
                info("Processing mod: $modName")
                processMod(modDef, modName, idManager)
            }
    }

    /**
     * Processes a single mod's ID assignments.
     * Explicit IDs are handled first to establish the base mapping, then implicit IDs
     * are assigned to fill remaining slots without breaking existing sequences.
     */
    private fun processMod(
        modDef: ModDefinition,
        modName: String,
        idManager: IdManager
    ): MappedModDefinition {
        val mappedDef = MappedModDefinition(modDef.modFile)

        EntityType.entries.forEach { type ->
            processExplicitIds(type, modDef, modName, idManager, mappedDef)
            processImplicitIds(type, modDef, modName, idManager)
        }

        return mappedDef
    }

    /**
     * Processes explicit ID assignments using block-based preservation.
     * Consecutive IDs are grouped into blocks and remapped together when conflicts occur,
     * ensuring that relative positioning is maintained for transformation sequences.
     */
    private fun processExplicitIds(
        type: EntityType,
        modDef: ModDefinition,
        modName: String,
        idManager: IdManager,
        mappedDef: MappedModDefinition
    ) {
        val sortedIds = modDef.getDefinition(type).definedIds.sorted()
        if (sortedIds.isEmpty()) return

        val blocks = groupIntoConsecutiveBlocks(sortedIds)
        debug("Detected ${blocks.size} consecutive blocks in $modName $type: ${blocks.joinToString()}")

        blocks.forEach { block ->
            processBlock(block, type, modName, idManager, mappedDef)
        }
    }

    /**
     * Groups sorted IDs into consecutive sequences.
     * Example: [100, 101, 102, 105, 106] becomes [[100,101,102], [105,106]]
     */
    private fun groupIntoConsecutiveBlocks(sortedIds: List<Long>): List<ConsecutiveBlock> {
        if (sortedIds.isEmpty()) return emptyList()

        return sortedIds.fold(mutableListOf<MutableList<Long>>()) { blocks, id ->
            val lastBlock = blocks.lastOrNull()
            when {
                lastBlock == null || id != lastBlock.last() + 1 -> blocks.apply { add(mutableListOf(id)) }
                else -> blocks.apply { lastBlock.add(id) }
            }
        }.map { ConsecutiveBlock(it) }
    }

    /**
     * Processes a single consecutive block of IDs.
     * The entire block is remapped together if any ID within it conflicts, preserving
     * the sequential relationships that transformation commands require.
     */
    private fun processBlock(
        block: ConsecutiveBlock,
        type: EntityType,
        modName: String,
        idManager: IdManager,
        mappedDef: MappedModDefinition
    ) {
        val conflictResults = block.ids.map { id ->
            id to idManager.registerOrRemapId(type, id, modName)
        }

        val hasRealConflicts = conflictResults.any { (_, result) ->
            result is IdRegistrationResult.Remapped
        }

        // Handle vanilla conflicts separately - they generate warnings but no remapping
        /*
        conflictResults
            .filter { (_, result) -> result is IdRegistrationResult.VanillaConflict }
            .forEach { (id, _) -> warn("Possible conflict, vanilla ID processed for $modName: $type $id") }
         */

        if (hasRealConflicts) {
            debug("Block $block in $modName $type: conflicts detected, remapping entire block")
            remapBlock(block, type, modName, idManager, mappedDef)
        } else {
            trace("Block $block in $modName $type: no conflicts, keeping original IDs")
        }
    }

    /**
     * Remaps an entire block while maintaining sequential relationships.
     * The first ID is assigned by the IdManager, subsequent IDs maintain their relative offsets
     * to preserve the spacing that transformation commands depend on.
     */
    private fun remapBlock(
        block: ConsecutiveBlock,
        type: EntityType,
        modName: String,
        idManager: IdManager,
        mappedDef: MappedModDefinition
    ) {
        var baseNewId: Long? = null

        block.ids.forEachIndexed { index, originalId ->
            val requestedId = baseNewId?.let { base ->
                val offset = originalId - block.first
                base + offset
            }

            when (val result = idManager.registerOrRemapId(type, originalId, modName, requestedId)) {
                is IdRegistrationResult.Remapped -> {
                    if (index == 0) baseNewId = result.newId
                    mappedDef.addMapping(type, originalId, result.newId)
                    trace("Block remapping: $modName $type $originalId -> ${result.newId}")
                }
                is IdRegistrationResult.Registered -> {
                    if (index == 0) baseNewId = originalId
                    trace("Block remapping: $modName $type $originalId -> $originalId (kept original)")
                }
                is IdRegistrationResult.VanillaConflict -> {
                    warn("Unexpected vanilla conflict in block remapping for $modName $type $originalId")
                    if (index == 0) baseNewId = originalId
                }
                is IdRegistrationResult.Error -> {
                    throw IdMappingException("Failed to remap block for $type in $modName at ID $originalId: ${result.message}")
                }
            }
        }

        debug("Remapped block $block in $modName $type to base ID $baseNewId")
    }

    /**
     * Assigns IDs to implicit definitions (those without explicit ID numbers).
     * These are processed after explicit IDs to avoid fragmenting existing sequences.
     */
    private fun processImplicitIds(
        type: EntityType,
        modDef: ModDefinition,
        modName: String,
        idManager: IdManager
    ) {
        val entityDef = modDef.getDefinition(type)
        val implicitCount = entityDef.getImplicitDefinitionCount()

        if (implicitCount == 0) return

        trace("Processing $implicitCount implicit definitions for $type in $modName")

        val results = idManager.registerImplicitIds(type, implicitCount, modName)
        val nameMappings = entityDef.getImplicitNameMappings()

        results.forEachIndexed { index, result ->
            when (result) {
                is IdRegistrationResult.Registered -> {
                    entityDef.setImplicitAssignedId(index, result.id)
                    nameMappings[index]?.forEach { name ->
                        entityDef.updateNameToId(name, result.id)
                    }
                }
                is IdRegistrationResult.Error -> {
                    throw IdMappingException("Failed to assign ID for implicit $type in $modName: ${result.message}")
                }
                else -> {
                    throw IdMappingException("Unexpected result type for implicit ID assignment: $result")
                }
            }
        }
    }
}

class IdMappingException(message: String) : Exception(message)