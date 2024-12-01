package com.dominions.modmerger.core.writing

import com.dominions.modmerger.MergeWarning
import com.dominions.modmerger.constants.GameConstants.SpellEffects.ENCHANTMENT_EFFECTS
import com.dominions.modmerger.constants.GameConstants.SpellEffects.SUMMONING_EFFECTS
import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.LogDispatcher
import com.dominions.modmerger.domain.LogLevel
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.utils.ModUtils
import mu.KotlinLogging
import java.util.*
import kotlin.math.abs

/**
 * Handles processing and writing of mod content, including entity remapping and special blocks.
 */
class ModContentWriter(
    private val entityProcessor: EntityProcessor,
    private val logDispatcher: LogDispatcher
) {
    private val logger = KotlinLogging.logger {}
    private val warnings = mutableListOf<MergeWarning>()

    /**
     * Processes the content of the mapped mod definitions and writes to the provided writer.
     *
     * @param mappedDefinitions Map of mod names to their mapped definitions
     * @param writer Writer to output the processed content
     * @return List of warnings generated during content processing
     */
    fun processModContent(
        mappedDefinitions: Map<String, MappedModDefinition>,
        writer: java.io.Writer
    ): List<MergeWarning> {
        warnings.clear()
        try {
            mappedDefinitions.forEach { (name, mappedDef) ->
                log(LogLevel.DEBUG, "Processing mod: $name")
                writer.write("\n-- Begin content from mod: $name\n")
                processModFile(mappedDef, writer)
            }

            writer.write("\n-- End merged content\n")
            log(LogLevel.DEBUG, "Successfully wrote mod content")

        } catch (e: Exception) {
            val errorMsg = "Failed to process mod content: ${e.message}"
            log(LogLevel.ERROR, errorMsg)
            warnings.add(
                MergeWarning.GeneralWarning(
                    message = errorMsg,
                    modFile = null
                )
            )
        }

        return warnings
    }

    private fun processModFile(mappedDef: MappedModDefinition, writer: java.io.Writer) {
        val lines = mappedDef.modFile.content.lines()
        val context = ModProcessingContext()

        var index = 0
        while (index < lines.size) {
            index = processNextLine(lines, index, context, mappedDef, writer)
        }
    }

    private fun processNextLine(
        lines: List<String>,
        currentIndex: Int,
        context: ModProcessingContext,
        mappedDef: MappedModDefinition,
        writer: java.io.Writer
    ): Int {
        val line = lines[currentIndex]
        val trimmedLine = line.trim()

        return when {
            shouldSkipLine(trimmedLine, currentIndex, context) -> currentIndex + 1
            ModPatterns.SPELL_BLOCK_START.matches(trimmedLine) ->
                processSpellBlock(lines, currentIndex, mappedDef, writer)
            else -> {
                val processed = entityProcessor.processEntity(line, mappedDef) { type, oldId, newId ->
                    "-- MOD MERGER: Remapped ${type.name.lowercase().replaceFirstChar { it.titlecase() }} $oldId -> $newId"
                }

                processed.remapComment?.let { writer.write("$it\n") }
                writer.write("${processed.line}\n")
                currentIndex + 1
            }
        }
    }

    private fun shouldSkipLine(line: String, index: Int, context: ModProcessingContext): Boolean {
        return when {
            line.isEmpty() && index == 0 -> true
            context.inMultilineDescription -> {
                if (line.contains("\"")) context.inMultilineDescription = false
                true
            }
            isDescriptionStart(line) -> {
                context.inMultilineDescription = !line.endsWith("\"")
                true
            }
            isModInfoLine(line) -> true
            else -> false
        }
    }

    private fun processSpellBlock(
        lines: List<String>,
        startIndex: Int,
        mappedDef: MappedModDefinition,
        writer: java.io.Writer
    ): Int {
        val blockInfo = collectSpellBlockInfo(lines, startIndex)

        blockInfo.lines.forEach { line ->
            val trimmedLine = line.trim()
            when {
                ModPatterns.SPELL_SELECT_ID.matches(trimmedLine) -> {
                    writeSpellSelect(line, mappedDef, writer)
                }
                ModPatterns.SPELL_DAMAGE.matches(trimmedLine) && blockInfo.effect != null -> {
                    writeSpellDamage(line, blockInfo.effect, mappedDef, writer)
                }
                else -> writer.write("$line\n")
            }
        }

        return blockInfo.endIndex
    }

    private data class SpellBlockInfo(
        val lines: List<String>,
        val effect: Long?,
        val endIndex: Int
    )

    private fun collectSpellBlockInfo(lines: List<String>, startIndex: Int): SpellBlockInfo {
        val spellLines = mutableListOf<String>()
        var effect: Long? = null
        var index = startIndex

        while (index < lines.size && !lines[index].trim().startsWith("#end")) {
            val currentLine = lines[index]
            spellLines.add(currentLine)

            ModUtils.extractId(currentLine.trim(), ModPatterns.SPELL_EFFECT)?.let {
                effect = it
            }

            index++
        }

        if (index < lines.size) {
            spellLines.add(lines[index])
            index++
        }

        return SpellBlockInfo(spellLines, effect, index)
    }

    private fun writeSpellSelect(line: String, mappedDef: MappedModDefinition, writer: java.io.Writer) {
        val oldId = ModUtils.extractId(line.trim(), ModPatterns.SPELL_SELECT_ID)
        val newId = oldId?.let { mappedDef.getMapping(EntityType.SPELL, it) }

        if (oldId != null && newId != null && oldId != newId) {
            writeRemapComment("Spell", oldId, newId, writer)
        }

        val processedLine = if (oldId != null && newId != null) {
            ModUtils.replaceId(line, oldId, newId)
        } else {
            line
        }

        writer.write("$processedLine\n")
    }

    private fun writeSpellDamage(line: String, effect: Long, mappedDef: MappedModDefinition, writer: java.io.Writer) {
        val damage = ModUtils.extractId(line.trim(), ModPatterns.SPELL_DAMAGE) ?: return writer.write("$line\n")

        val (entityType, newId) = when (effect) {
            in SUMMONING_EFFECTS -> {
                val type = if (damage > 0) EntityType.MONSTER else EntityType.MONTAG
                val oldId = abs(damage)
                val remappedId = mappedDef.getMapping(type, oldId)?.let {
                    if (damage > 0) it else -it
                } ?: damage
                type to remappedId
            }
            in ENCHANTMENT_EFFECTS -> {
                val remappedId = mappedDef.getMapping(EntityType.ENCHANTMENT, damage) ?: damage
                EntityType.ENCHANTMENT to remappedId
            }
            else -> null to damage
        }

        if (entityType != null && damage != newId) {
            writeRemapComment(entityType.name, abs(damage), abs(newId), writer)
        }

        writer.write("${ModUtils.replaceId(line, damage, newId)}\n")
    }

    private fun writeRemapComment(entityType: String, oldId: Long, newId: Long, writer: java.io.Writer) {
        val typeName = entityType.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }
        writer.write("-- MOD MERGER: Remapped $typeName $oldId -> $newId\n")
    }

    private fun isDescriptionStart(line: String): Boolean =
        ModPatterns.MOD_DESCRIPTION_START.matches(line) && !ModPatterns.MOD_DESCRIPTION_LINE.matches(line)

    private fun isModInfoLine(line: String): Boolean =
        line.matches(ModPatterns.MOD_NAME) ||
                line.matches(ModPatterns.MOD_DESCRIPTION_LINE) ||
                line.matches(ModPatterns.MOD_ICON_LINE) ||
                line.matches(ModPatterns.MOD_VERSION_LINE) ||
                line.matches(ModPatterns.MOD_DOMVERSION_LINE)

    private fun log(level: LogLevel, message: String) {
        logger.info { message }
        logDispatcher.log(level, message)
    }
}
