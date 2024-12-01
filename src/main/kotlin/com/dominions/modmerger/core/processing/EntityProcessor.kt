package com.dominions.modmerger.core.processing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.utils.ModUtils

class EntityProcessor {

    data class ProcessedEntity(
        val line: String,
        val remapComment: String?
    )

    data class EntityMatch(
        val type: EntityType,
        val pattern: Regex,
        val oldId: Long
    )


    companion object {
        private val USAGE_PATTERNS = mapOf(
            EntityType.MONSTER to listOf(ModPatterns.USE_MONSTER),
            EntityType.WEAPON to listOf(ModPatterns.USE_NUMBERED_WEAPON),
            EntityType.ARMOR to listOf(ModPatterns.USE_NUMBERED_ARMOR),
            EntityType.ITEM to listOf(ModPatterns.USE_NUMBERED_ITEM),
            EntityType.SPELL to listOf(ModPatterns.USE_NUMBERED_SPELL),
            EntityType.SITE to listOf(ModPatterns.USE_NUMBERED_SITE),
            EntityType.NATION to listOf(ModPatterns.USE_NUMBERED_NATION),
            EntityType.MONTAG to listOf(ModPatterns.USE_NUMBERED_MONTAG),
            EntityType.EVENT_CODE to listOf(ModPatterns.USE_NUMBERED_EVENTCODE),
            EntityType.RESTRICTED_ITEM to listOf(ModPatterns.USE_NUMBERED_RESTRICTED_ITEM),
            EntityType.ENCHANTMENT to listOf(ModPatterns.USE_GLOBAL_ENCHANTMENT)
        )

        private val DEFINITION_PATTERNS = mapOf(
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
            EntityType.NATION to listOf(
                ModPatterns.SELECT_NUMBERED_NATION
            ),
            EntityType.MONTAG to listOf(
                ModPatterns.SELECT_NUMBERED_MONTAG
            ),
            EntityType.EVENT_CODE to listOf(
                ModPatterns.SELECT_NUMBERED_EVENTCODE
            ),
            EntityType.RESTRICTED_ITEM to listOf(
                ModPatterns.SELECT_NUMBERED_RESTRICTED_ITEM
            ),
            EntityType.POPTYPE to listOf(
                ModPatterns.SELECT_NUMBERED_POPTYPE
            )
        )
    }

    fun detectEntity(line: String): EntityMatch? {
        val trimmedLine = line.trim()

        DEFINITION_PATTERNS.forEach { (type, patterns) ->
            patterns.forEach { pattern ->
                ModUtils.extractId(trimmedLine, pattern)?.let { id ->
                    return EntityMatch(type, pattern, id)
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
        // First check all usage patterns
        USAGE_PATTERNS.forEach { (entityType, patterns) ->
            for (pattern in patterns) {
                ModUtils.extractId(line, pattern)?.let { oldId ->
                    val newId = mappedDef.getMapping(entityType, oldId)
                    if (newId != null && oldId != newId) {
                        val comment = remapCommentWriter(entityType, oldId, newId)
                        val newLine = ModUtils.replaceId(line, oldId, newId)
                        return ProcessedEntity(newLine, comment)
                    }
                }
            }
        }

        // Then check for definitions if no usages were found
        detectEntity(line)?.let { match ->
            val newId = mappedDef.getMapping(match.type, match.oldId)
            if (newId != null && match.oldId != newId) {
                val comment = remapCommentWriter(match.type, match.oldId, newId)
                val newLine = ModUtils.replaceId(line, match.oldId, newId)
                return ProcessedEntity(newLine, comment)
            }
        }

        return ProcessedEntity(line, null)
    }
}