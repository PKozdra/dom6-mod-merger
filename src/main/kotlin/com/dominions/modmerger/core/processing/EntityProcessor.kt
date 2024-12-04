package com.dominions.modmerger.core.processing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.utils.ModUtils
import mu.KotlinLogging

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

    private val logger = KotlinLogging.logger {}

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
            EntityType.SPELL to listOf(
                ModPatterns.SPELL_SELECT_ID
            ),
            EntityType.SITE to listOf(
                ModPatterns.NEW_NUMBERED_SITE,
                ModPatterns.SELECT_NUMBERED_SITE
            ),
            EntityType.NATION to listOf(
                ModPatterns.SELECT_NUMBERED_NATION
            ),
            EntityType.NAME_TYPE to listOf(
                ModPatterns.SELECT_NUMBERED_NAMETYPE
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

    private val spellBlockProcessor = SpellBlockProcessor()

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

    @Throws(IllegalStateException::class)
    fun processEntity(
        line: String,
        mappedDef: MappedModDefinition,
        remapCommentWriter: (EntityType, Long, Long) -> String
    ): ProcessedEntity {
        try {
            // Handle spell blocks if active
            spellBlockProcessor.currentBlock?.let { currentBlock ->
                return handleSpellBlock(line, currentBlock, mappedDef, remapCommentWriter)
            }

            // Check if this is the start of a new spell block
            if (ModPatterns.SPELL_BLOCK_START.matches(line)) {
                spellBlockProcessor.startNewBlock(line)
                return ProcessedEntity(line, null)
            }

            // Process normal entity usage patterns
            processUsagePatterns(line, mappedDef, remapCommentWriter)?.let {
                return it
            }

            // Process entity definitions if no usages were found
            return processDefinitionPatterns(line, mappedDef, remapCommentWriter)
        } catch (e: Exception) {
            logger.error(e) { "Error processing entity line: $line" }
            throw IllegalStateException("Failed to process entity: ${e.message}", e)
        }
    }

    private fun handleSpellBlock(
        line: String,
        currentBlock: SpellBlockProcessor.SpellBlock,
        mappedDef: MappedModDefinition,
        remapCommentWriter: (EntityType, Long, Long) -> String
    ): ProcessedEntity {
        val processedSpell = spellBlockProcessor.processSpellLine(line, mappedDef)

        // Check if this is the end of the spell block
        if (ModPatterns.END.matches(line)) {
            spellBlockProcessor.currentBlock = null

            // Apply any deferred damage line mapping
            processedSpell.damageMapping?.let { (oldId, newId) ->
                val entityType = if (oldId > 0) EntityType.MONSTER else EntityType.MONTAG
                return ProcessedEntity(
                    ModUtils.replaceId(line, oldId, newId),
                    remapCommentWriter(entityType, oldId, newId)
                )
            }
        }

        return ProcessedEntity(line, null)
    }

    private fun processUsagePatterns(
        line: String,
        mappedDef: MappedModDefinition,
        remapCommentWriter: (EntityType, Long, Long) -> String
    ): ProcessedEntity? {
        USAGE_PATTERNS.forEach { (entityType, patterns) ->
            for (pattern in patterns) {
                ModUtils.extractId(line, pattern)?.let { oldId ->
                    val newId = mappedDef.getMapping(entityType, oldId)
                    if (oldId != newId) {
                        val comment = remapCommentWriter(entityType, oldId, newId)
                        val newLine = ModUtils.replaceId(line, oldId, newId)
                        return ProcessedEntity(newLine, comment)
                    }
                }
            }
        }
        return null
    }

    private fun processDefinitionPatterns(
        line: String,
        mappedDef: MappedModDefinition,
        remapCommentWriter: (EntityType, Long, Long) -> String
    ): ProcessedEntity {
        detectEntity(line)?.let { match ->
            val newId = mappedDef.getMapping(match.type, match.oldId)
            if (match.oldId != newId) {
                val comment = remapCommentWriter(match.type, match.oldId, newId)
                val newLine = ModUtils.replaceId(line, match.oldId, newId)
                return ProcessedEntity(newLine, comment)
            }
        }
        return ProcessedEntity(line, null)
    }
}