package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.constants.*
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.utils.ModUtils

class ModParser(
    private val entityProcessor: EntityProcessor,
    private val enableDetailedLogging: Boolean = false
) : Logging {

    // Tracking of current entity context for things like NAME -> ID mapping
    private data class EntityContext(
        val type: EntityType,
        var currentId: Long?
    )

    private class ParserState(
        val modFile: ModFile,
        val definition: ModDefinition = ModDefinition(modFile),
        var inSpellBlock: Boolean = false,
        var currentSpellEffect: Long? = null,
        var lineNumber: Int = 0,
        var currentEntityContext: EntityContext? = null
    ) {
        fun resetContext() {
            currentEntityContext = null
            inSpellBlock = false
            currentSpellEffect = null
        }
    }

    fun parse(modFile: ModFile): ModDefinition {
        val state = ParserState(modFile)
        debug("Parsing mod file: ${modFile.name}")

        modFile.content.lineSequence()
            .firstNotNullOfOrNull { ModUtils.extractString(it.trim(), ModPatterns.MOD_NAME) }
            ?.let { state.definition.name = it }

        commandLines(modFile.content, modFile.name).forEach { tracked ->
            val line = tracked.content.trim()
            if (line.isNotBlank() && !line.startsWith("--")) {
                state.lineNumber = tracked.originalLineNumber - 1
                processLine(line, state)
            }
        }

        return state.definition
    }

    private fun processLine(line: String, state: ParserState) {
        try {
            when {
                ModPatterns.SPELL_BLOCK_START.containsMatchIn(line) -> {
                    trace("Handling spell block start", useDispatcher = false)
                    state.inSpellBlock = true
                    handleSpellStart(line, state)
                }

                ModPatterns.END.containsMatchIn(line) -> {
                    trace("Handling block end", useDispatcher = false)
                    state.resetContext()
                }

                ModPatterns.ENTITY_NAME.containsMatchIn(line) -> {
                    trace("Handling entity name definition", useDispatcher = false)
                    handleNameDefinition(line, state)
                }

                state.inSpellBlock -> {
                    trace("Handling spell block content", useDispatcher = false)
                    handleSpellBlockContent(line, state)
                }

                else -> {
                    trace("Handling entity line", useDispatcher = false)
                    handleEntityLine(line, state)
                }
            }
        } catch (e: Exception) {
            handleParsingError(state, line, e)
        }
    }

    private fun handleSpellBlockContent(line: String, state: ParserState) {
        ModUtils.extractId(line, ModPatterns.SPELL_EFFECT)?.let { state.currentSpellEffect = it }

        if (state.currentSpellEffect != null) {
            handleSpellContent(line, state)
        }
    }

    private fun handleSpellContent(line: String, state: ParserState) {
        val damage = ModUtils.extractId(line, ModPatterns.SPELL_DAMAGE) ?: return
        when (state.currentSpellEffect) {
            in GameConstants.SpellEffects.SUMMONING_EFFECTS -> {
                handleSummoningEffect(damage, state.definition)
            }
            in GameConstants.SpellEffects.ENCHANTMENT_EFFECTS -> {
                state.definition.addDefinedId(EntityType.ENCHANTMENT, damage)
            }
        }
    }

    private fun handleSummoningEffect(damage: Long, definition: ModDefinition) {
        if (damage > 0) {
            handleIdAddition(damage, EntityType.MONSTER, definition)
        }
        else {
            handleIdAddition(damage, EntityType.MONTAG, definition)
        }
    }

    private fun handleEntityLine(line: String, state: ParserState) {
        entityProcessor.detectEntity(line)?.let { (type, id) ->
            trace("Detected entity: $type with ID: ${id ?: "implicit"}", useDispatcher = false)

            when {
                id != null -> {
                    handleIdAddition(id, type, state.definition)
                    state.currentEntityContext = EntityContext(type, id)
                }
                else -> {
                    val implicitIndex = state.definition.addImplicitDefinition(type)
                    state.currentEntityContext = EntityContext(type, null)
                    trace("Created implicit definition #$implicitIndex for ${type.name}", useDispatcher = false)
                }
            }
        }
    }

    private fun handleNameDefinition(line: String, state: ParserState) {
        val context = state.currentEntityContext ?: return

        ModUtils.extractString(line, ModPatterns.ENTITY_NAME)?.let { name ->
            state.definition.addDefinedName(
                type = context.type,
                name = name,
                id = context.currentId,
                lineNumber = state.lineNumber
            )

            if (context.currentId != null) {
                debug("Associated ${context.type.name} name '$name' with ID ${context.currentId}", useDispatcher = false)
            } else {
                val implicitIndex = state.definition.getDefinition(context.type).getImplicitDefinitionCount() - 1
                debug("Stored ${context.type.name} name '$name' for implicit definition #$implicitIndex", useDispatcher = false)
            }
        }
    }

    private fun handleIdAddition(id: Long?, type: EntityType, definition: ModDefinition) {
        if (id != null) {
            if (ModRanges.Validator.isValidModdingId(type, id)) {
                definition.addDefinedId(type, id)
            } else {
                definition.addVanillaEditedId(type, id)
            }
        }
        else {
            definition.addImplicitDefinition(type)
        }
    }

    private fun handleSpellStart(line: String, state: ParserState) {
        // Try to extract an ID from lines like "#selectspell 123"
        val extractedId = ModUtils.extractId(line, ModPatterns.SPELL_SELECT_ID)
        if (extractedId != null) {
            // We found an explicit ID
            if (extractedId >= ModRanges.Modding.SPELL_START) {
                state.definition.addDefinedId(EntityType.SPELL, extractedId)
            } else {
                state.definition.addVanillaEditedId(EntityType.SPELL, extractedId)
            }

            // IMPORTANT: set the parser context to this ID
            state.currentEntityContext = EntityContext(EntityType.SPELL, extractedId)
        }
        else if (line.startsWith("#newspell")) {
            // We have an implicit ID (i.e. the user didn't specify a numeric ID)
            state.definition.addImplicitDefinition(EntityType.SPELL)

            // We don't know the numeric ID, so store null in the context
            state.currentEntityContext = EntityContext(EntityType.SPELL, null)
        }
        else if (ModPatterns.SPELL_SELECT_NAME.containsMatchIn(line)) {
            // a spell selected by name has no number to file a #name under, its #damage is still scanned
            state.currentEntityContext = null
        }
        else {
            error("Unknown spell block start: $line")
        }
    }

    private fun handleParsingError(state: ParserState, line: String, e: Exception) {
        val errorMsg = buildString {
            append("Error in ${state.modFile.name} at line ${state.lineNumber}: $line")
            if (enableDetailedLogging) {
                append("\nCurrent parser state: ")
                append("inSpellBlock=${state.inSpellBlock}, ")
                append("currentSpellEffect=${state.currentSpellEffect}")
            }
        }
        error(errorMsg, e)
        throw ModParsingException(errorMsg, e)
    }
}