// src/main/kotlin/com/dominions/modmerger/core/parsing/ModParser.kt
package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.config.GameConstants
import com.dominions.modmerger.config.ModRanges
import com.dominions.modmerger.domain.*
import com.dominions.modmerger.utils.ModPatterns
import com.dominions.modmerger.utils.ModUtils
import mu.KLogger
import mu.KotlinLogging
import kotlin.math.abs

class ModParser {
    private val logger: KLogger = KotlinLogging.logger {}

    fun parse(modFile: ModFile): ModDefinition {
        val definition = ModDefinition(modFile)
        val context = ParsingContext()
        val modFileName = modFile.name

        modFile.content.lineSequence().forEachIndexed { lineNumber, line ->
            try {
                parseLine(line.trim(), definition, context, modFileName, lineNumber + 1)
            } catch (e: ModParsingException) {
                logger.error(e) { "Parsing error in $modFileName at line ${lineNumber + 1}: $line" }
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Unexpected error in $modFileName at line ${lineNumber + 1}: $line" }
                throw ModParsingException("Error in $modFileName at line ${lineNumber + 1}: $line", e)
            }
        }

        return definition
    }

    private fun parseLine(
        line: String,
        definition: ModDefinition,
        context: ParsingContext,
        modFileName: String,
        lineNumber: Int
    ) {
        if (line.isBlank() || line.startsWith("--")) return

        logger.trace { "[$modFileName:$lineNumber] Parsing line: $line" }
        val lineType = determineLineType(line, context, modFileName, lineNumber)

        when (lineType) {
            LineType.MOD_NAME -> handleModName(line, definition, modFileName, lineNumber)
            LineType.SPELL_BLOCK_START -> handleSpellBlockStart(line, definition, context, modFileName, lineNumber)
            LineType.BLOCK_END -> handleBlockEnd(context, definition, modFileName, lineNumber)
            LineType.SPELL_BLOCK_CONTENT -> handleSpellBlockLine(line, context.currentSpellBlock, modFileName, lineNumber)
            LineType.ENTITY_DEFINITION -> handleEntityDefinition(line, definition, modFileName, lineNumber)
        }
    }

    private fun determineLineType(
        line: String,
        context: ParsingContext,
        modFileName: String,
        lineNumber: Int
    ): LineType {
        val lineType = when {
            ModPatterns.MOD_NAME.matches(line) -> LineType.MOD_NAME
            ModPatterns.SPELL_BLOCK_START.matches(line) -> LineType.SPELL_BLOCK_START
            ModPatterns.END.matches(line) -> LineType.BLOCK_END
            context.isInSpellBlock -> LineType.SPELL_BLOCK_CONTENT
            else -> LineType.ENTITY_DEFINITION
        }
        logger.trace { "[$modFileName:$lineNumber] Determined line type: $lineType for line: $line" }
        return lineType
    }

    private fun handleModName(
        line: String,
        definition: ModDefinition,
        modFileName: String,
        lineNumber: Int
    ) {
        ModUtils.extractString(line, ModPatterns.MOD_NAME)?.let { modName ->
            definition.name = modName
            logger.debug { "[$modFileName:$lineNumber] Parsed mod name: $modName" }
        }
    }

    private fun handleSpellBlockStart(
        line: String,
        definition: ModDefinition,
        context: ParsingContext,
        modFileName: String,
        lineNumber: Int
    ) {
        context.startSpellBlock()
        logger.trace { "[$modFileName:$lineNumber] Started spell block" }

        val id = ModUtils.extractId(line, ModPatterns.SPELL_SELECT_ID)
        if (id != null) {
            if (id >= ModRanges.Modding.SPELL_START) {
                definition.addDefinedId(EntityType.SPELL, id)
                logger.debug { "[$modFileName:$lineNumber] Defined new spell with ID $id" }
            } else {
                definition.addVanillaEditedId(EntityType.SPELL, id)
                logger.debug { "[$modFileName:$lineNumber] Edited vanilla spell with ID $id" }
            }
            return
        }

        val monsterId = ModUtils.extractId(line, ModPatterns.NEW_NUMBERED_MONSTER)
        if (monsterId != null) {
            definition.addDefinedId(EntityType.MONSTER, monsterId)
            logger.debug { "[$modFileName:$lineNumber] Defined new monster with ID $monsterId" }
            return
        }

        if (line.startsWith("#newspell")) {
            definition.addImplicitDefinition(EntityType.SPELL)
            logger.debug { "[$modFileName:$lineNumber] Defined new implicit spell" }
        }
    }

    private fun handleSpellBlockLine(
        line: String,
        spellBlock: SpellBlock,
        modFileName: String,
        lineNumber: Int
    ) {
        logger.trace { "[$modFileName:$lineNumber] Handling spell block line: $line" }

        val effect = ModUtils.extractId(line, ModPatterns.SPELL_EFFECT)
        if (effect != null) {
            spellBlock.effect = effect
            logger.debug { "[$modFileName:$lineNumber] Set spell effect to $effect" }
            return
        }

        val damage = ModUtils.extractId(line, ModPatterns.SPELL_DAMAGE)
        if (damage != null) {
            spellBlock.damage = damage
            logger.debug { "[$modFileName:$lineNumber] Set spell damage to $damage" }
            return
        }
    }

    private fun handleBlockEnd(
        context: ParsingContext,
        definition: ModDefinition,
        modFileName: String,
        lineNumber: Int
    ) {
        if (context.isInSpellBlock) {
            processSpellBlock(context.currentSpellBlock, definition, modFileName, lineNumber)
            logger.trace { "[$modFileName:$lineNumber] Processed spell block" }
        }
        context.endCurrentBlock()
        logger.trace { "[$modFileName:$lineNumber] Ended current block" }
    }

    private fun processSpellBlock(
        spellBlock: SpellBlock,
        definition: ModDefinition,
        modFileName: String,
        lineNumber: Int
    ) {
        val effect = spellBlock.effect ?: return
        val damage = spellBlock.damage ?: return

        when {
            effect in GameConstants.SpellEffects.SUMMONING_EFFECTS -> {
                if (damage > 0) {
                    definition.addDefinedId(EntityType.MONSTER, damage)
                    logger.debug { "[$modFileName:$lineNumber] Added monster with ID $damage from spell effect" }
                } else {
                    definition.addDefinedId(EntityType.MONTAG, abs(damage))
                    logger.debug { "[$modFileName:$lineNumber] Added montag with ID ${abs(damage)} from spell effect" }
                }
            }
            effect in GameConstants.SpellEffects.ENCHANTMENT_EFFECTS -> {
                definition.addDefinedId(EntityType.ENCHANTMENT, damage)
                logger.debug { "[$modFileName:$lineNumber] Added enchantment with ID $damage from spell effect" }
            }
        }
    }

    private fun handleEntityDefinition(
        line: String,
        definition: ModDefinition,
        modFileName: String,
        lineNumber: Int
    ) {
        logger.trace { "[$modFileName:$lineNumber] Handling entity definition: $line" }

        if (parseEntityId(line, ModPatterns.NEW_NUMBERED_WEAPON, EntityType.WEAPON, definition, modFileName, lineNumber)) return
        if (parseEntityId(line, ModPatterns.NEW_NUMBERED_ARMOR, EntityType.ARMOR, definition, modFileName, lineNumber)) return
        if (parseEntityId(line, ModPatterns.NEW_NUMBERED_MONSTER, EntityType.MONSTER, definition, modFileName, lineNumber)) return

        when {
            ModPatterns.NEW_UNNUMBERED_WEAPON.matches(line) -> {
                definition.addImplicitDefinition(EntityType.WEAPON)
                logger.debug { "[$modFileName:$lineNumber] Defined new implicit weapon" }
            }
            ModPatterns.NEW_UNNUMBERED_ARMOR.matches(line) -> {
                definition.addImplicitDefinition(EntityType.ARMOR)
                logger.debug { "[$modFileName:$lineNumber] Defined new implicit armor" }
            }
            ModPatterns.NEW_UNNUMBERED_MONSTER.matches(line) -> {
                definition.addImplicitDefinition(EntityType.MONSTER)
                logger.debug { "[$modFileName:$lineNumber] Defined new implicit monster" }
            }
        }
    }

    private fun parseEntityId(
        line: String,
        pattern: Regex,
        entityType: EntityType,
        definition: ModDefinition,
        modFileName: String,
        lineNumber: Int
    ): Boolean {
        val id = ModUtils.extractId(line, pattern)
        if (id != null) {
            definition.addDefinedId(entityType, id)
            logger.debug { "[$modFileName:$lineNumber] Defined ${entityType.name} with ID $id" }
            return true
        }
        logger.trace { "[$modFileName:$lineNumber] No match for ${entityType.name} in line: $line" }
        return false
    }

    private enum class LineType {
        MOD_NAME,
        SPELL_BLOCK_START,
        BLOCK_END,
        SPELL_BLOCK_CONTENT,
        ENTITY_DEFINITION
    }
}