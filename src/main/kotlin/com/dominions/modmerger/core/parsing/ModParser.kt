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

        try {
            processFileContent(modFile, definition, context)
        } catch (e: Exception) {
            handleParsingError(e, modFile.name)
        }

        return definition
    }

    private fun processFileContent(modFile: ModFile, definition: ModDefinition, context: ParsingContext) {
        modFile.content.lineSequence().forEachIndexed { lineNumber, line ->
            try {
                parseLine(line.trim(), definition, context, modFile.name, lineNumber + 1)
            } catch (e: Exception) {
                throw ModParsingException("Error in ${modFile.name} at line ${lineNumber + 1}: $line", e)
            }
        }
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
        if (shouldSkipLine(line)) return

        logger.trace { "[$modFileName:$lineNumber] Parsing line: $line" }

        when (lineTypeDetector.detectLineType(line, context)) {
            LineType.MOD_NAME -> handleModName(line, definition)
            LineType.MOD_INFO -> handleModInfo(line, definition)
            LineType.SPELL_BLOCK_START -> handleSpellBlock(line, definition, context)
            LineType.BLOCK_END -> handleBlockEnd(context, definition)
            LineType.SPELL_BLOCK_CONTENT -> spellBlockParser.handleSpellBlockContent(line, context)
            LineType.EVENT_CODE -> eventParser.handleEventCode(line, definition)
            LineType.ENTITY_DEFINITION -> entityParser.handleEntityDefinition(line, definition)
            else -> handleOtherEntityTypes(line, definition)
        }
    }

    private fun shouldSkipLine(line: String): Boolean =
        line.isBlank() || line.startsWith("--")

    private fun handleModInfo(line: String, definition: ModDefinition) {
        // Handle any mod info processing if needed
    }

    private fun handleSpellBlock(line: String, definition: ModDefinition, context: ParsingContext) {
        spellBlockParser.handleSpellBlockStart(line, definition, context)
    }

    private fun handleOtherEntityTypes(line: String, definition: ModDefinition) {
        when {
            line.matches(ModPatterns.SELECT_NUMBERED_RESTRICTED_ITEM) ->
                entityParser.handleRestrictedItem(line, definition)
            line.matches(ModPatterns.SELECT_NUMBERED_POPTYPE) ->
                entityParser.handlePopType(line, definition)
            line.matches(ModPatterns.SELECT_NUMBERED_MONTAG) ->
                entityParser.handleMontag(line, definition)
        }
    }

    private fun handleParsingError(e: Exception, modFileName: String) {
        val errorMsg = "Failed to parse mod file: $modFileName"
        logger.error(e) { errorMsg }
        throw ModParsingException(errorMsg, e)
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