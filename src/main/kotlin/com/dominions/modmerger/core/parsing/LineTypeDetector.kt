package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.domain.EntityType

class LineTypeDetector(
    private val entityProcessor: EntityProcessor
) {
    fun detectLineType(line: String, context: ParsingContext): LineType = when {
        line.matches(ModPatterns.MOD_NAME) -> LineType.MOD_NAME
        line.matches(ModPatterns.MOD_DESCRIPTION_LINE) -> LineType.MOD_INFO
        line.matches(ModPatterns.MOD_ICON_LINE) -> LineType.MOD_INFO
        line.matches(ModPatterns.MOD_VERSION_LINE) -> LineType.MOD_INFO
        line.matches(ModPatterns.MOD_DOMVERSION_LINE) -> LineType.MOD_INFO
        line.matches(ModPatterns.SPELL_BLOCK_START) -> LineType.SPELL_BLOCK_START
        line.matches(ModPatterns.END) -> LineType.BLOCK_END
        context.isInSpellBlock -> LineType.SPELL_BLOCK_CONTENT
        line.startsWith("#code") || line.startsWith("#code2") -> LineType.EVENT_CODE
        entityProcessor.detectEntity(line)?.let { match ->
            when (match.type) {
                EntityType.RESTRICTED_ITEM -> LineType.RESTRICTED_ITEM
                EntityType.POPTYPE -> LineType.POPTYPE
                EntityType.MONTAG -> LineType.MONTAG
                else -> LineType.ENTITY_DEFINITION
            }
        } != null -> LineType.ENTITY_DEFINITION
        else -> LineType.ENTITY_DEFINITION
    }
}