// src/main/kotlin/com/dominions/modmerger/core/parsing/ParsingContext.kt
package com.dominions.modmerger.core.parsing

data class SpellBlock(
    var damage: Long? = null,
    var effect: Long? = null,
    var copyspell: String? = null,
    var selectspell: String? = null,
    var copyspellId: Long? = null,
    var selectspellId: Long? = null
)

class ParsingContext {
    private var currentBlock: BlockType? = null
    var currentSpellBlock = SpellBlock()
        private set

    val isInSpellBlock: Boolean
        get() = currentBlock == BlockType.SPELL

    fun startSpellBlock() {
        currentBlock = BlockType.SPELL
        currentSpellBlock = SpellBlock()
    }

    fun endCurrentBlock() {
        currentBlock = null
        currentSpellBlock = SpellBlock()
    }

    private enum class BlockType {
        SPELL
        // Add other block types as needed
    }
}