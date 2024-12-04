package com.dominions.modmerger.core.processing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.constants.RegexWrapper
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.utils.ModUtils

class EntityProcessor : Logging {
    data class ProcessedEntity(
        val line: String,
        val remapComment: String?
    )

    data class EntityMatch(
        val type: EntityType,
        val id: Long?,         // From extractId
        val name: String?,     // From extractName
        val patternName: String,
    )

    companion object {
        // Convert patterns to optimized versions at initialization
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
        ).mapValues { (_, patterns) ->
            patterns.map { RegexWrapper.fromRegex(it) }
        }

        private val DEFINITION_PATTERNS = mapOf(
            EntityType.MONSTER to listOf(
                ModPatterns.NEW_NUMBERED_MONSTER,
                ModPatterns.NEW_UNNUMBERED_MONSTER,
                ModPatterns.SELECT_NUMBERED_MONSTER
            ),
            EntityType.WEAPON to listOf(
                ModPatterns.NEW_NUMBERED_WEAPON,
                ModPatterns.NEW_UNNUMBERED_WEAPON,
                ModPatterns.SELECT_NUMBERED_WEAPON
            ),
            EntityType.ARMOR to listOf(
                ModPatterns.NEW_NUMBERED_ARMOR,
                ModPatterns.NEW_UNNUMBERED_ARMOR,
                ModPatterns.SELECT_NUMBERED_ARMOR
            ),
            EntityType.ITEM to listOf(
                ModPatterns.NEW_NUMBERED_ITEM,
                ModPatterns.NEW_UNNUMBERED_ITEM,
                ModPatterns.SELECT_NUMBERED_ITEM
            ),
            EntityType.SPELL to listOf(
                ModPatterns.SPELL_SELECT_ID
            ),
            EntityType.SITE to listOf(
                ModPatterns.NEW_NUMBERED_SITE,
                ModPatterns.NEW_UNNUMBERED_SITE,
                ModPatterns.SELECT_NUMBERED_SITE
            ),
            EntityType.NATION to listOf(
                ModPatterns.SELECT_NUMBERED_NATION,
                ModPatterns.NEW_UNNUMBERED_NATION,
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
        ).mapValues { (_, patterns) ->
            patterns.map { RegexWrapper.fromRegex(it) }
        }

        // Frequently used patterns
        private val SPELL_BLOCK_START = RegexWrapper.fromRegex(ModPatterns.SPELL_BLOCK_START)
        private val END_PATTERN = RegexWrapper.fromRegex(ModPatterns.END)
    }

    private val spellBlockProcessor = SpellBlockProcessor()

    // Reusable StringBuilder for trimming
    private val trimBuilder = StringBuilder(256)

    fun detectEntity(line: String): EntityMatch? {
        val trimmedLine = line.trim()

        // Fast path check - if line doesn't start with #, it can't be an entity
        if (!trimmedLine.startsWith("#")) return null

        trace("Detecting entity in line: $trimmedLine", useDispatcher = false)

        DEFINITION_PATTERNS.forEach { (type, patterns) ->
            patterns.forEach { pattern ->
                // First check if pattern matches at all
                if (pattern.matches(trimmedLine)) {
                    val patternName = pattern.getPatternName()
                    trace("Found matching pattern: $patternName", useDispatcher = false)

                    // Only try extractions if we have a match
                    val id = ModUtils.extractId(trimmedLine, pattern.toRegex())
                    val name = ModUtils.extractName(trimmedLine, pattern.toRegex())

                    return EntityMatch(type=type, id=id, name=name, patternName=patternName)
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
        trace("ProcessEntity for line: $line", useDispatcher = false)
        try {
            // Handle spell blocks if active
            spellBlockProcessor.currentBlock?.let { currentBlock ->
                trace("ProcessEntity spell block line: $line", useDispatcher = false)
                return handleSpellBlock(line, currentBlock, mappedDef, remapCommentWriter)
            }

            // Fast path - if line doesn't start with #, no need for regex matching
            if (!line.startsWith("#")) {
                trace("ProcessEntity returning, haven't found #", useDispatcher = false)
                return ProcessedEntity(line, null)
            }

            // Check if this is the start of a new spell block
            if (SPELL_BLOCK_START.matches(line)) {
                trace("ProcessEntity starting new spell block", useDispatcher = false)
                spellBlockProcessor.startNewBlock(line)
                return ProcessedEntity(line, null)
            }

            // Process normal entity usage patterns
            trace("ProcessEntity trying to process usage patterns", useDispatcher = false)
            processUsagePatterns(line, mappedDef, remapCommentWriter)?.let {
                return it
            }

            // Process entity definitions if no usages were found
            trace("ProcessEntity trying to process definition patterns", useDispatcher = false)
            return processDefinitionPatterns(line, mappedDef, remapCommentWriter)
        } catch (e: Exception) {
            error("Error processing entity line: $line", e)
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

        // Check if this is the end of the spell block using cached pattern
        if (END_PATTERN.matches(line)) {
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
        // Fast path - check if line contains a number before regex matching
        if (!line.any { it.isDigit() }) return null

        USAGE_PATTERNS.forEach { (entityType, patterns) ->
            for (pattern in patterns) {
                ModUtils.extractId(line, pattern.toRegex())?.let { oldId ->
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
            match.id?.let { id ->  // Only process if we have an ID
                val newId = mappedDef.getMapping(match.type, id)
                if (id != newId) {
                    val comment = remapCommentWriter(match.type, id, newId)
                    val newLine = ModUtils.replaceId(line, id, newId)
                    return ProcessedEntity(newLine, comment)
                }
            }
        }
        return ProcessedEntity(line, null)
    }
}