// ModParser.kt
package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.constants.GameConstants
import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.utils.ModUtils
import kotlin.math.abs

class ModParser(private val entityProcessor: EntityProcessor = EntityProcessor()) : Logging {

    fun parse(modFile: ModFile): ModDefinition {
        val definition = ModDefinition(modFile)
        var inMultilineDescription = false
        var inSpellBlock = false
        var currentSpellEffect: Long? = null

        modFile.content.lineSequence().forEachIndexed { lineNumber, rawLine ->
            val line = rawLine.trim()
            try {
                when {
                    // Skip empty lines and comments
                    line.isBlank() || line.startsWith("--") -> {}

                    // Handle mod info
                    isModInfoLine(line) -> handleModInfo(line, definition)

                    // Handle multiline description
                    inMultilineDescription -> {
                        if (line.contains("\"")) inMultilineDescription = false
                    }

                    isDescriptionStart(line) -> {
                        inMultilineDescription = !line.endsWith("\"")
                    }

                    // Handle spell blocks
                    line.matches(ModPatterns.SPELL_BLOCK_START) -> {
                        inSpellBlock = true
                        handleSpellStart(line, definition)
                    }

                    line.matches(ModPatterns.END) -> {
                        inSpellBlock = false
                        currentSpellEffect = null
                    }

                    inSpellBlock -> {
                        handleSpellContent(line, currentSpellEffect, definition)
                        // Update effect if found
                        ModPatterns.SPELL_EFFECT.find(line)?.let {
                            currentSpellEffect = it.groupValues[1].toLong()
                        }
                    }

                    // Handle all other entities
                    else -> handleEntityLine(line, definition)
                }
            } catch (e: Exception) {
                error("Error parsing line $lineNumber in ${modFile.name}: $line)", e)
                throw ModParsingException("Error in ${modFile.name} at line $lineNumber: $line", e)
            }
        }

        return definition
    }

    private fun handleEntityLine(line: String, definition: ModDefinition) {
        entityProcessor.detectEntity(line)?.let { match ->
            val (type, _, id) = match
            when {
                line.startsWith("#new") -> definition.addDefinedId(type, id)
                line.startsWith("#select") -> {
                    if (ModRanges.Validator.isValidModdingId(type, id)) {
                        definition.addDefinedId(type, id)
                    } else {
                        definition.addVanillaEditedId(type, id)
                    }
                }

                else -> definition.addImplicitDefinition(type)
            }
        }
    }

    private fun handleSpellStart(line: String, definition: ModDefinition) {
        ModUtils.extractId(line, ModPatterns.SPELL_SELECT_ID)?.let { id ->
            if (id >= ModRanges.Modding.SPELL_START) {
                definition.addDefinedId(EntityType.SPELL, id)
            } else {
                definition.addVanillaEditedId(EntityType.SPELL, id)
            }
        } ?: if (line.startsWith("#newspell")) {
            definition.addImplicitDefinition(EntityType.SPELL)
        } else {
            error("Unknown spell block start: $line")
        }
    }

    private fun handleSpellContent(line: String, effect: Long?, definition: ModDefinition) {
        if (effect != null) {
            ModUtils.extractId(line, ModPatterns.SPELL_DAMAGE)?.let { damage ->
                when {
                    effect in GameConstants.SpellEffects.SUMMONING_EFFECTS -> {
                        if (damage > 0) {
                            if (ModRanges.Validator.isVanillaId(EntityType.MONSTER, damage)) {
                                definition.addVanillaEditedId(EntityType.MONSTER, damage)
                            } else {
                                definition.addDefinedId(EntityType.MONSTER, damage)
                            }
                        } else {
                            definition.addDefinedId(EntityType.MONTAG, abs(damage))
                        }
                    }

                    effect in GameConstants.SpellEffects.ENCHANTMENT_EFFECTS -> {
                        definition.addDefinedId(EntityType.ENCHANTMENT, damage)
                    }
                }
            }
        }
    }

    private fun isModInfoLine(line: String) = line.matches(ModPatterns.MOD_NAME) ||
            line.matches(ModPatterns.MOD_DESCRIPTION_LINE) ||
            line.matches(ModPatterns.MOD_ICON_LINE) ||
            line.matches(ModPatterns.MOD_VERSION_LINE) ||
            line.matches(ModPatterns.MOD_DOMVERSION_LINE)

    private fun isDescriptionStart(line: String) =
        line.matches(ModPatterns.MOD_DESCRIPTION_START) && !line.matches(ModPatterns.MOD_DESCRIPTION_LINE)

    private fun handleModInfo(line: String, definition: ModDefinition) {
        ModUtils.extractString(line, ModPatterns.MOD_NAME)?.let { modName ->
            definition.name = modName
            debug("Set mod name: $modName", useDispatcher = false)
        }
    }
}