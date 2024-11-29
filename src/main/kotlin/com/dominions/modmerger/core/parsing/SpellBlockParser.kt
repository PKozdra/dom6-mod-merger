// src/main/kotlin/com/dominions/modmerger/core/parsing/com.dominions.modmerger.core.parsing.SpellBlockParser.kt
package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.constants.GameConstants
import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.utils.ModUtils
import mu.KLogger
import mu.KotlinLogging
import kotlin.math.abs


class SpellBlockParser {
    private val logger: KLogger = KotlinLogging.logger {}

    fun handleSpellBlockStart(
        line: String,
        definition: ModDefinition,
        context: ParsingContext,
    ) {
        context.startSpellBlock()
        logger.trace { "Started spell block" }

        val id = ModUtils.extractId(line, ModPatterns.SPELL_SELECT_ID)
        if (id != null) {
            if (id >= ModRanges.Modding.SPELL_START) {
                definition.addDefinedId(EntityType.SPELL, id)
                logger.trace { "Defined new spell with ID $id" }
            } else {
                definition.addVanillaEditedId(EntityType.SPELL, id)
                logger.trace { "Edited vanilla spell with ID $id" }
            }
            return
        }

        if (line.startsWith("#newspell")) {
            definition.addImplicitDefinition(EntityType.SPELL)
            logger.trace { "Defined new implicit spell" }
        }
    }

    fun handleSpellBlockContent(line: String, context: ParsingContext) {
        val spellBlock = context.currentSpellBlock

        when {
            ModUtils.extractId(line, ModPatterns.SPELL_EFFECT)?.let {
                spellBlock.effect = it
                true
            } == true -> logger.trace { "Set spell effect to ${spellBlock.effect}" }

            ModUtils.extractId(line, ModPatterns.SPELL_DAMAGE)?.let {
                spellBlock.damage = it
                true
            } == true -> logger.trace { "Set spell damage to ${spellBlock.damage}" }

            ModUtils.extractId(line, ModPatterns.SPELL_COPY_ID)?.let {
                spellBlock.copyspellId = it
                true
            } == true -> logger.trace { "Set copyspell ID to ${spellBlock.copyspellId}" }

            ModUtils.extractString(line, ModPatterns.SPELL_COPY_NAME)?.let {
                spellBlock.copyspell = it
                true
            } == true -> logger.trace { "Set copyspell name to ${spellBlock.copyspell}" }

            ModUtils.extractId(line, ModPatterns.SPELL_SELECT_ID)?.let {
                spellBlock.selectspellId = it
                true
            } == true -> logger.trace { "Set selectspell ID to ${spellBlock.selectspellId}" }

            ModUtils.extractString(line, ModPatterns.SPELL_SELECT_NAME)?.let {
                spellBlock.selectspell = it
                true
            } == true -> logger.trace { "Set selectspell name to ${spellBlock.selectspell}" }
        }
    }

    fun processSpellBlock(spellBlock: SpellBlock, definition: ModDefinition) {
        processSpellEffectAndDamage(spellBlock, definition)

        val damage = spellBlock.damage
        when {
            spellBlock.copyspellId != null ->
                handleKnownSpellId(spellBlock.copyspellId!!, damage, definition)

            spellBlock.copyspell != null ->
                handleKnownSpellName(spellBlock.copyspell!!, damage, definition)

            spellBlock.selectspellId != null ->
                handleKnownSpellId(spellBlock.selectspellId!!, damage, definition)

            spellBlock.selectspell != null ->
                handleKnownSpellName(spellBlock.selectspell!!, damage, definition)
        }
    }

    private fun processSpellEffectAndDamage(spellBlock: SpellBlock, definition: ModDefinition) {
        val effect = spellBlock.effect
        val damage = spellBlock.damage

        if (effect != null && damage != null) {
            when {
                effect in GameConstants.SpellEffects.SUMMONING_EFFECTS -> {
                    processSummoningSpell(damage, definition)
                }

                effect in GameConstants.SpellEffects.ENCHANTMENT_EFFECTS -> {
                    definition.addDefinedId(EntityType.ENCHANTMENT, damage)
                }
            }
        }
    }

    private fun processSummoningSpell(damage: Long, definition: ModDefinition) {
        if (damage > 0) {
            // If it's a vanilla monster ID, add it as a vanilla reference
            if (ModRanges.Validator.isVanillaId(EntityType.MONSTER, damage)) {
                definition.addVanillaEditedId(EntityType.MONSTER, damage)
            } else {
                // Otherwise treat it as a mod monster ID
                definition.addDefinedId(EntityType.MONSTER, damage)
            }
        } else {
            definition.addDefinedId(EntityType.MONTAG, abs(damage))
        }
    }

    private fun handleKnownSpellId(spellId: Long, damage: Long?, definition: ModDefinition) {
        if (isKnownSummoningSpellId(spellId) && damage != null) {
            processSummoningSpell(damage, definition)
        }
    }

    private fun handleKnownSpellName(spellName: String, damage: Long?, definition: ModDefinition) {
        if (isKnownSummoningSpell(spellName) && damage != null) {
            processSummoningSpell(damage, definition)
        }
    }

    private fun isKnownSummoningSpell(name: String): Boolean =
        GameConstants.SpellEffects.KNOWN_SUMMON_SPELL_NAMES.contains(name.lowercase())

    private fun isKnownSummoningSpellId(id: Long): Boolean =
        GameConstants.SpellEffects.KNOWN_SUMMON_SPELL_IDS.contains(id)
}