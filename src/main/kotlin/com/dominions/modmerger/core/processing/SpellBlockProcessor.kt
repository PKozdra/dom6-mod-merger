package com.dominions.modmerger.core.processing

import com.dominions.modmerger.constants.GameConstants
import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.utils.ModUtils


class SpellBlockProcessor : Logging {
    data class SpellBlock(
        val startLine: String,
        val lines: MutableList<String> = mutableListOf(),
        var effect: Long? = null,
        var damage: Long? = null,
        var copyspell: String? = null,
        var selectspell: String? = null,
        var damageMapping: Pair<Long, Long>? = null
    )

    var currentBlock: SpellBlock? = null
        internal set

    fun startNewBlock(startLine: String) {
        if (currentBlock != null) {
            warn("Starting new spell block while previous block was not closed", useDispatcher = false)
        }
        currentBlock = SpellBlock(startLine)
    }

    fun processSpellLine(line: String, mappedDef: MappedModDefinition): SpellBlock {
        val block = currentBlock ?: throw IllegalStateException("No active spell block")
        block.lines.add(line)

        // Extract relevant information from the line
        when {
            ModPatterns.SPELL_EFFECT.matches(line) -> {
                block.effect = ModUtils.extractId(line, ModPatterns.SPELL_EFFECT)
            }

            ModPatterns.SPELL_DAMAGE.matches(line) -> {
                processDamageLine(line, block, mappedDef)
            }

            ModPatterns.SPELL_COPY_ID.matches(line) || ModPatterns.SPELL_COPY_NAME.matches(line) -> {
                block.copyspell = line
            }

            ModPatterns.SPELL_SELECT_ID.matches(line) || ModPatterns.SPELL_SELECT_NAME.matches(line) -> {
                block.selectspell = line
            }
        }

        return block
    }

    private fun processDamageLine(line: String, block: SpellBlock, mappedDef: MappedModDefinition) {
        val damageId = ModUtils.extractId(line, ModPatterns.SPELL_DAMAGE) ?: return

        when {
            // Handle enchantment effects
            block.effect in GameConstants.SpellEffects.ENCHANTMENT_EFFECTS -> {
                val newId = mappedDef.getMapping(EntityType.ENCHANTMENT, damageId)
                if (damageId != newId) {
                    block.damageMapping = damageId to newId
                }
            }
            // Handle summoning effects
            block.effect in GameConstants.SpellEffects.SUMMONING_EFFECTS -> {
                processSummoningDamage(damageId, block, mappedDef)
            }
            // Handle known summoning spells
            isKnownSummoningSpell(block) -> {
                processSummoningDamage(damageId, block, mappedDef)
            }
        }
    }

    private fun processSummoningDamage(damageId: Long, block: SpellBlock, mappedDef: MappedModDefinition) {
        if (damageId > 0) {
            val newId = mappedDef.getMapping(EntityType.MONSTER, damageId)
            if (damageId != newId) {
                block.damageMapping = damageId to newId
                debug("Mapped monster in summoning spell: $damageId -> $newId", useDispatcher = false)
            }
        } else {
            val montagId = -damageId
            val newMontagId = mappedDef.getMapping(EntityType.MONTAG, montagId)
            if (montagId != newMontagId) {
                block.damageMapping = damageId to -newMontagId
                debug("Mapped montag in summoning spell: $montagId -> $newMontagId", useDispatcher = false)
            }
        }
    }

    private fun isKnownSummoningSpell(block: SpellBlock): Boolean {
        block.copyspell?.let { copyspell ->
            val spellId = ModUtils.extractId(copyspell, ModPatterns.SPELL_COPY_ID)
            val spellName = ModUtils.extractName(copyspell, ModPatterns.SPELL_COPY_NAME)?.lowercase()

            return spellId in GameConstants.SpellEffects.KNOWN_SUMMON_SPELL_IDS ||
                    spellName in GameConstants.SpellEffects.KNOWN_SUMMON_SPELL_NAMES
        }

        block.selectspell?.let { selectspell ->
            val spellId = ModUtils.extractId(selectspell, ModPatterns.SPELL_SELECT_ID)
            val spellName = ModUtils.extractName(selectspell, ModPatterns.SPELL_SELECT_NAME)?.lowercase()

            return spellId in GameConstants.SpellEffects.KNOWN_SUMMON_SPELL_IDS ||
                    spellName in GameConstants.SpellEffects.KNOWN_SUMMON_SPELL_NAMES
        }

        return false
    }
}