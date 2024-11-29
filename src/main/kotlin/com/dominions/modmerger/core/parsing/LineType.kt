// src/main/kotlin/com/dominions/modmerger/core/parsing/com.dominions.modmerger.core.parsing.LineType.kt
package com.dominions.modmerger.core.parsing

enum class LineType {
    MOD_NAME,
    MOD_INFO,
    SPELL_BLOCK_START,
    BLOCK_END,
    SPELL_BLOCK_CONTENT,
    EVENT_CODE,
    RESTRICTED_ITEM,
    POPTYPE,
    MONTAG,
    ENTITY_DEFINITION
}