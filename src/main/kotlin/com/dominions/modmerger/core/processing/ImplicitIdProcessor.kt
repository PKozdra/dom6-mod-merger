package com.dominions.modmerger.core.processing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.infrastructure.Logging

class ImplicitIdProcessor : Logging {
    private val nextImplicitIndex = mutableMapOf<Pair<EntityType, String>, Int>()  // (type, modName) -> next index

    data class ProcessedLine(
        val newLine: String,
        val remapComment: String?,
        val newId: Long
    )

    fun cleanIndices() {
        nextImplicitIndex.clear()
    }

    fun processImplicitDefinition(
        line: String,
        type: EntityType,
        modDef: ModDefinition,
        mappedDef: MappedModDefinition
    ): ProcessedLine? {
        // Get next implicit index for this (type, mod) pair
        val key = type to modDef.name
        val index = nextImplicitIndex.getOrDefault(key, 0)
        nextImplicitIndex[key] = index + 1

        // Get assigned ID for this implicit index
        val entityDef = modDef.getDefinition(type)
        val assignedId = entityDef.getAssignedIdForImplicitIndex(index) ?: run {
            debug("No assigned ID found for $type implicit index=$index in mod=${modDef.name}")
            return null
        }

        // Get potentially remapped ID
        val newId = mappedDef.getMapping(type, assignedId)

        // Process based on entity type
        val (newLine, comment) = when (type) {
            EntityType.SPELL -> processSpellLine(line, newId)
            else -> processRegularEntity(line, newId, type)
        }

        return ProcessedLine(
            newLine = newLine,
            remapComment = comment,
            newId = newId
        )
    }

    private fun processSpellLine(line: String, newId: Long): Pair<String, String> {
        val matchResult = ModPatterns.NEW_UNNUMBERED_SPELL.find(line.trim())
        val remainingContent = matchResult?.groupValues?.get(1) ?: ""

        return Pair(
            "#selectspell $newId$remainingContent",
            "-- MOD MERGER: Converted #newspell to #selectspell with assigned ID $newId"
        )
    }

    private fun processRegularEntity(line: String, newId: Long, type: EntityType): Pair<String, String> {
        val commandEnd = line.indexOf(' ', line.indexOf('#'))
        val newLine = if (commandEnd != -1) {
            line.substring(0, commandEnd) + " " + newId + line.substring(commandEnd)
        } else {
            "$line $newId"
        }

        return Pair(
            newLine,
            "-- MOD MERGER: Assigned new ID $newId" +
                    " to implicit $type definition"
        )
    }
}