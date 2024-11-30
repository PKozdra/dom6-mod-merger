package com.dominions.modmerger.core.processing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.utils.ModUtils

class EntityProcessor {
    data class EntityMatch(
        val type: EntityType,
        val pattern: Regex,
        val oldId: Long,
        val newId: Long?
    )

    fun detectEntity(line: String): EntityMatch? {
        val trimmedLine = line.trim()

        // Check for new entities
        ENTITY_PATTERNS.forEach { (type, patterns) ->
            patterns.forEach { pattern ->
                ModUtils.extractId(trimmedLine, pattern)?.let { id ->
                    return EntityMatch(type, pattern, id, null)
                }
            }
        }
        return null
    }

    fun processEntity(
        line: String,
        mappedDef: MappedModDefinition,
        remapCommentWriter: (EntityType, Long, Long) -> String
    ): ProcessedEntity {
        val entityMatch = detectEntity(line) ?: return ProcessedEntity(line, null)

        val newId = mappedDef.getMapping(entityMatch.type, entityMatch.oldId)

        if (newId != null && newId != entityMatch.oldId) {
            val comment = remapCommentWriter(entityMatch.type, entityMatch.oldId, newId)
            val remappedLine = ModUtils.replaceId(line, entityMatch.oldId, newId)
            return ProcessedEntity(remappedLine, comment)
        }

        return ProcessedEntity(line, null)
    }

    data class ProcessedEntity(
        val line: String,
        val remapComment: String?
    )

    companion object {
        private val ENTITY_PATTERNS = mapOf(
            EntityType.MONSTER to listOf(
                ModPatterns.NEW_NUMBERED_MONSTER,
                ModPatterns.SELECT_NUMBERED_MONSTER
            ),
            EntityType.WEAPON to listOf(
                ModPatterns.NEW_NUMBERED_WEAPON,
                ModPatterns.SELECT_NUMBERED_WEAPON
            ),
            EntityType.ARMOR to listOf(
                ModPatterns.NEW_NUMBERED_ARMOR,
                ModPatterns.SELECT_NUMBERED_ARMOR
            ),
            EntityType.ITEM to listOf(
                ModPatterns.NEW_NUMBERED_ITEM,
                ModPatterns.SELECT_NUMBERED_ITEM
            ),
            EntityType.SITE to listOf(
                ModPatterns.NEW_NUMBERED_SITE,
                ModPatterns.SELECT_NUMBERED_SITE
            ),
            EntityType.RESTRICTED_ITEM to listOf(
                ModPatterns.SELECT_NUMBERED_RESTRICTED_ITEM
            ),
            EntityType.MONTAG to listOf(
                ModPatterns.SELECT_NUMBERED_MONTAG
            ),
            EntityType.EVENT_CODE to listOf(
                ModPatterns.SELECT_NUMBERED_EVENTCODE
            ),
            EntityType.POPTYPE to listOf(
                ModPatterns.SELECT_NUMBERED_POPTYPE
            )
        )
    }
}