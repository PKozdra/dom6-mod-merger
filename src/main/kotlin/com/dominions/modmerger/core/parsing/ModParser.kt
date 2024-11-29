// src/main/kotlin/com/dominions/modmerger/core/parsing/ModParser.kt
package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.utils.ModUtils
import mu.KLogger
import mu.KotlinLogging

class ModParser(
    private val spellBlockParser: SpellBlockParser,
    private val entityParser: EntityParser,
    private val eventParser: EventParser,
    private val lineTypeDetector: LineTypeDetector
) {
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

    private fun isModInfoLine(line: String): Boolean =
        line.matches(ModPatterns.MOD_NAME) ||
                line.matches(ModPatterns.MOD_DESCRIPTION_LINE) ||
                line.matches(ModPatterns.MOD_ICON_LINE) ||
                line.matches(ModPatterns.MOD_VERSION_LINE) ||
                line.matches(ModPatterns.MOD_DOMVERSION_LINE)

    private fun isDescriptionStart(line: String): Boolean =
        line.matches(ModPatterns.MOD_DESCRIPTION_START) && !line.matches(ModPatterns.MOD_DESCRIPTION_LINE)

    private fun parseLine(
        line: String,
        definition: ModDefinition,
        context: ParsingContext,
        modFileName: String,
        lineNumber: Int
    ) {
        if (line.isBlank() || line.startsWith("--")) return

        logger.trace { "[$modFileName:$lineNumber] Parsing line: $line" }

        when (lineTypeDetector.detectLineType(line, context)) {
            LineType.MOD_NAME -> handleModName(line, definition)
            LineType.MOD_INFO -> { /* Skip mod info lines */
            }  // Added this
            LineType.SPELL_BLOCK_START -> spellBlockParser.handleSpellBlockStart(line, definition, context)
            LineType.BLOCK_END -> handleBlockEnd(context, definition)
            LineType.SPELL_BLOCK_CONTENT -> spellBlockParser.handleSpellBlockContent(line, context)
            LineType.EVENT_CODE -> eventParser.handleEventCode(line, definition)
            LineType.RESTRICTED_ITEM -> entityParser.handleRestrictedItem(line, definition)
            LineType.POPTYPE -> entityParser.handlePopType(line, definition)
            LineType.MONTAG -> entityParser.handleMontag(line, definition)
            LineType.ENTITY_DEFINITION -> entityParser.handleEntityDefinition(line, definition)
        }
    }

    private fun handleModName(line: String, definition: ModDefinition) {
        ModUtils.extractString(line, ModPatterns.MOD_NAME)?.let { modName ->
            definition.name = modName
            logger.debug { "Set mod name: $modName" }
        }
    }

    private fun handleBlockEnd(context: ParsingContext, definition: ModDefinition) {
        if (context.isInSpellBlock) {
            spellBlockParser.processSpellBlock(context.currentSpellBlock, definition)
        }
        context.endCurrentBlock()
    }
}