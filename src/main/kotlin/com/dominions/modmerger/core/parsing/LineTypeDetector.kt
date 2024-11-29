//src/main/kotlin/com/dominions/modmerger/core/parsing/com.dominions.modmerger.core.parsing.LineTypeDetector.kt
package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.constants.ModPatterns

class LineTypeDetector {
    fun detectLineType(line: String, context: ParsingContext): LineType = when {
        ModPatterns.MOD_NAME.matches(line) -> LineType.MOD_NAME
        ModPatterns.MOD_DESCRIPTION_LINE.matches(line) -> LineType.MOD_INFO
        ModPatterns.MOD_ICON_LINE.matches(line) -> LineType.MOD_INFO
        ModPatterns.MOD_VERSION_LINE.matches(line) -> LineType.MOD_INFO
        ModPatterns.MOD_DOMVERSION_LINE.matches(line) -> LineType.MOD_INFO
        ModPatterns.SPELL_BLOCK_START.matches(line) -> LineType.SPELL_BLOCK_START
        ModPatterns.END.matches(line) -> LineType.BLOCK_END
        context.isInSpellBlock -> LineType.SPELL_BLOCK_CONTENT
        line.startsWith("#code") || line.startsWith("#code2") -> LineType.EVENT_CODE
        line.startsWith("#restricteditem") -> LineType.RESTRICTED_ITEM
        line.startsWith("#poptype") -> LineType.POPTYPE
        line.startsWith("#montag") -> LineType.MONTAG
        else -> LineType.ENTITY_DEFINITION
    }
}