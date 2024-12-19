package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.constants.*
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.domain.ModFile
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.utils.ModUtils
import jdk.internal.net.http.common.Log.logTrace
import kotlin.math.abs

class ModParser(
    private val entityProcessor: EntityProcessor,
    private val enableDetailedLogging: Boolean = false
) : Logging {

    private object CompiledPatterns {
        val modName = RegexWrapper.fromRegex(ModPatterns.MOD_NAME)
        val descriptionLine = RegexWrapper.fromRegex(ModPatterns.MOD_DESCRIPTION_LINE)
        val iconLine = RegexWrapper.fromRegex(ModPatterns.MOD_ICON_LINE)
        val versionLine = RegexWrapper.fromRegex(ModPatterns.MOD_VERSION_LINE)
        val domversionLine = RegexWrapper.fromRegex(ModPatterns.MOD_DOMVERSION_LINE)
        val descriptionStart = RegexWrapper.fromRegex(ModPatterns.MOD_DESCRIPTION_START)
        val spellBlockStart = RegexWrapper.fromRegex(ModPatterns.SPELL_BLOCK_START)
        val end = RegexWrapper.fromRegex(ModPatterns.END)
        val spellEffect = RegexWrapper.fromRegex(ModPatterns.SPELL_EFFECT)
        val spellDamage = RegexWrapper.fromRegex(ModPatterns.SPELL_DAMAGE)
        val spellSelectId = RegexWrapper.fromRegex(ModPatterns.SPELL_SELECT_ID)

        // Thread-local matchers for hot paths
        private val threadLocalMatchers = ThreadLocal.withInitial {
            mapOf(
                "modInfo" to ModPatterns.MOD_NAME.toPattern().matcher(""),
                "spellEffect" to ModPatterns.SPELL_EFFECT.toPattern().matcher(""),
                "spellDamage" to ModPatterns.SPELL_DAMAGE.toPattern().matcher("")
            )
        }

        fun getMatcher(type: String, input: String) =
            threadLocalMatchers.get()[type]?.reset(input)
                ?: throw IllegalArgumentException("Unknown matcher type: $type")
    }

    private class ParserState(
        val modFile: ModFile,
        val definition: ModDefinition = ModDefinition(modFile),
        var inMultilineDescription: Boolean = false,
        var inSpellBlock: Boolean = false,
        var currentSpellEffect: Long? = null,
        var lineNumber: Int = 0
    ) {
        fun resetSpellBlock() {
            inSpellBlock = false
            currentSpellEffect = null
        }
    }

    fun parse(modFile: ModFile): ModDefinition {
        val state = ParserState(modFile)
        debug("Parsing mod file: ${modFile.name}")

        modFile.content
            .lineSequence()
            .asSequence()
            .mapIndexed { index, line ->
                state.lineNumber = index
                line.trim()
            }
            .filter { it.isNotBlank() && !it.startsWith("--") }
            .forEach { line ->
                processLine(line, state)
            }

        return state.definition
    }

    private fun processLine(line: String, state: ParserState) {
        try {
            when {
                isModInfoLine(line) -> {
                    trace("Handling mod info line", useDispatcher = false)
                    handleModInfo(line, state)
                }

                state.inMultilineDescription -> {
                    trace("Handling multiline description", useDispatcher = false)
                    if (line.contains("\"")) state.inMultilineDescription = false
                }

                isDescriptionStart(line) -> {
                    trace("Handling description start", useDispatcher = false)
                    state.inMultilineDescription = !line.endsWith("\"")
                }

                CompiledPatterns.spellBlockStart.matches(line) -> {
                    trace("Handling spell block start", useDispatcher = false)
                    state.inSpellBlock = true
                    handleSpellStart(line, state)
                }

                CompiledPatterns.end.matches(line) -> {
                    trace("Handling block end", useDispatcher = false)
                    state.resetSpellBlock()
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
        CompiledPatterns.getMatcher("spellEffect", line).let { matcher ->
            if (matcher.find()) {
                state.currentSpellEffect = matcher.group(1).toLong()
            }
        }

        if (state.currentSpellEffect != null) {
            handleSpellContent(line, state)
        }
    }

    private fun handleSpellContent(line: String, state: ParserState) {
        CompiledPatterns.getMatcher("spellDamage", line).let { matcher ->
            if (matcher.find()) {
                val damage = matcher.group(1).toLong()
                when (state.currentSpellEffect) {
                    in GameConstants.SpellEffects.SUMMONING_EFFECTS -> {
                        handleSummoningEffect(damage, state.definition)
                    }
                    in GameConstants.SpellEffects.ENCHANTMENT_EFFECTS -> {
                        state.definition.addDefinedId(EntityType.ENCHANTMENT, damage)
                    }
                }
            }
        }
    }

    private fun handleSummoningEffect(damage: Long, definition: ModDefinition) {
        if (damage > 0) {
            handleIdAddition(damage, null, EntityType.MONSTER, definition)
        }
        else {
            handleIdAddition(damage, null, EntityType.MONTAG, definition)
        }
    }

    private fun handleEntityLine(line: String, state: ParserState) {
        entityProcessor.detectEntity(line)?.let { (type, id, name) ->
            trace("Detected entity: $type with ID: ${id ?: name ?: "implicit"}", useDispatcher = false)
            handleIdAddition(id, name, type, state.definition)
        }
    }

    private fun handleIdAddition(id: Long?, name: String?, type: EntityType, definition: ModDefinition) {
        if (id != null) {
            if (ModRanges.Validator.isValidModdingId(type, id)) {
                definition.addDefinedId(type, id)
            } else {
                definition.addVanillaEditedId(type, id)
            }
        }
        else if (name != null) {
            definition.addDefinedName(type, name)
        }
        else {
            definition.addImplicitDefinition(type)
        }
    }

    private fun handleSpellStart(line: String, state: ParserState) {
        ModUtils.extractId(line, CompiledPatterns.spellSelectId.toRegex())?.let { id ->
            if (id >= ModRanges.Modding.SPELL_START) {
                state.definition.addDefinedId(EntityType.SPELL, id)
            } else {
                state.definition.addVanillaEditedId(EntityType.SPELL, id)
            }
        } ?: if (line.startsWith("#newspell")) {
            state.definition.addImplicitDefinition(EntityType.SPELL)
        } else {
            error("Unknown spell block start: $line")
        }
    }

    private fun isModInfoLine(line: String): Boolean {
        return CompiledPatterns.getMatcher("modInfo", line).matches() ||
                CompiledPatterns.descriptionLine.matches(line) ||
                CompiledPatterns.iconLine.matches(line) ||
                CompiledPatterns.versionLine.matches(line) ||
                CompiledPatterns.domversionLine.matches(line)
    }

    private fun isDescriptionStart(line: String): Boolean {
        return CompiledPatterns.descriptionStart.matches(line) &&
                !CompiledPatterns.descriptionLine.matches(line)
    }

    private fun handleModInfo(line: String, state: ParserState) {
        ModUtils.extractString(line, CompiledPatterns.modName.toRegex())?.let { modName ->
            state.definition.name = modName
            debug("Set mod name: $modName", useDispatcher = false)
        }
    }

    private fun handleParsingError(state: ParserState, line: String, e: Exception) {
        val errorMsg = buildString {
            append("Error in ${state.modFile.name} at line ${state.lineNumber}: $line")
            if (enableDetailedLogging) {
                append("\nCurrent parser state: ")
                append("inMultilineDescription=${state.inMultilineDescription}, ")
                append("inSpellBlock=${state.inSpellBlock}, ")
                append("currentSpellEffect=${state.currentSpellEffect}")
            }
        }
        error(errorMsg, e)
        throw ModParsingException(errorMsg, e)
    }
}