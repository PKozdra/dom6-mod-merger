// src/main/kotlin/com/dominions/modmerger/core/writing/ModWriter.kt
package com.dominions.modmerger.core.writing


import com.dominions.modmerger.MergeWarning
import com.dominions.modmerger.constants.GameConstants.SpellEffects.ENCHANTMENT_EFFECTS
import com.dominions.modmerger.constants.GameConstants.SpellEffects.SUMMONING_EFFECTS
import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.infrastructure.FileSystem
import com.dominions.modmerger.utils.ModUtils
import mu.KLogger
import mu.KotlinLogging
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs


class ModWriter(
    private val fileSystem: FileSystem // Inject FileSystem for output management
) {
    private val logger: KLogger = KotlinLogging.logger {}
    private val warnings = mutableListOf<MergeWarning>()

    /**
     * Writes the merged mod to the configured file.
     */
    fun writeMergedMod(mappedDefinitions: Map<String, MappedModDefinition>): List<MergeWarning> {
        val outputFile = fileSystem.getOutputFile()
        logger.info { "Writing merged mod to: ${outputFile.absolutePath}" }

        outputFile.bufferedWriter().use { writer ->
            writeHeader(writer, mappedDefinitions.keys)

            // Process each mod file
            mappedDefinitions.forEach { (_, mappedDef) ->
                processModFile(mappedDef, writer)
            }
        }

        return warnings
    }

    private fun writeHeader(writer: java.io.Writer, modNames: Collection<String>) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        writer.write(
            """
            #modname "Mod Merger Output"
            #description "Merged mod containing:${modNames.joinToString("\n- ", prefix = "\n- ")}

            Generated on: $timestamp"

            -- Begin merged content

            """.trimIndent()
        )
        writer.write("\n\n")
    }

    private fun processModFile(mappedDef: MappedModDefinition, writer: java.io.Writer) {
        val content = mappedDef.modFile.content
        val lines = content.lines()
        var index = 0

        while (index < lines.size) {
            val line = lines[index].trim()
            if (line.isEmpty() || line.startsWith("--")) {
                writer.write(lines[index])
                writer.write("\n")
                index++
                continue
            }

            when {
                line.startsWith("#modname") ||
                        line.startsWith("#description") ||
                        line.startsWith("#version") -> {
                    // Skip mod metadata
                    index++
                }

                ModPatterns.SPELL_BLOCK_START.matches(line) -> {
                    index = processSpellBlock(lines, index, mappedDef, writer)
                }

                else -> {
                    processLine(lines[index], mappedDef, writer)
                    index++
                }
            }
        }
    }

    private fun processSpellBlock(
        lines: List<String>,
        startIndex: Int,
        mappedDef: MappedModDefinition,
        writer: java.io.Writer
    ): Int {
        var index = startIndex
        val spellLines = mutableListOf<String>()

        // Collect all lines in the spell block
        while (index < lines.size && !lines[index].trim().startsWith("#end")) {
            spellLines.add(lines[index])
            index++
        }

        // Add the #end line if found
        if (index < lines.size) {
            spellLines.add(lines[index])
            index++
        }

        // Process and write the spell block
        writeProcessedSpellBlock(spellLines, mappedDef, writer)

        return index
    }

    private fun writeProcessedSpellBlock(
        spellLines: List<String>,
        mappedDef: MappedModDefinition,
        writer: java.io.Writer
    ) {
        var damage: Long? = null
        var effect: Long? = null

        // First pass - collect spell information
        spellLines.forEach { line ->
            ModUtils.extractId(line, ModPatterns.SPELL_DAMAGE)?.let { dmg ->
                damage = dmg
            }
            ModUtils.extractId(line, ModPatterns.SPELL_EFFECT)?.let { eff ->
                effect = eff
            }
        }

        // Second pass - write transformed lines
        spellLines.forEach { line ->
            val processedLine = when {
                ModPatterns.SPELL_SELECT_ID.matches(line) -> {
                    remapSpellSelectLine(line, mappedDef)
                }

                ModPatterns.SPELL_DAMAGE.matches(line) && effect != null -> {
                    // Special handling for damage lines in enchantment and summoning effects
                    remapSpellDamageLine(line, effect!!, mappedDef)
                }

                else -> line
            }
            writer.write(processedLine)
            writer.write("\n")
        }
    }

    private fun processLine(line: String, mappedDef: MappedModDefinition, writer: java.io.Writer) {
        val processedLine = when {
            ModPatterns.NEW_NUMBERED_MONSTER.matches(line) ->
                remapEntityLine(line, EntityType.MONSTER, mappedDef)

            ModPatterns.NEW_NUMBERED_WEAPON.matches(line) ->
                remapEntityLine(line, EntityType.WEAPON, mappedDef)

            ModPatterns.NEW_NUMBERED_ARMOR.matches(line) ->
                remapEntityLine(line, EntityType.ARMOR, mappedDef)

            else -> line
        }

        writer.write(processedLine)
        writer.write("\n")
    }

    private fun remapEntityLine(line: String, type: EntityType, mappedDef: MappedModDefinition): String {
        val pattern = when (type) {
            EntityType.MONSTER -> ModPatterns.NEW_NUMBERED_MONSTER
            EntityType.WEAPON -> ModPatterns.NEW_NUMBERED_WEAPON
            EntityType.ARMOR -> ModPatterns.NEW_NUMBERED_ARMOR
            else -> return line
        }

        val oldId = ModUtils.extractId(line, pattern)
        val newId = oldId?.let { mappedDef.getMapping(type, it) }
        return if (oldId != null && newId != null) {
            ModUtils.replaceId(line, oldId, newId)
        } else {
            line
        }
    }

    private fun remapSpellSelectLine(line: String, mappedDef: MappedModDefinition): String {
        val oldId = ModUtils.extractId(line, ModPatterns.SPELL_SELECT_ID)
        val newId = oldId?.let { mappedDef.getMapping(EntityType.SPELL, it) }
        return if (oldId != null && newId != null) {
            ModUtils.replaceId(line, oldId, newId)
        } else {
            line
        }
    }

    private fun remapSpellDamageLine(line: String, effect: Long, mappedDef: MappedModDefinition): String {
        val damage = ModUtils.extractId(line, ModPatterns.SPELL_DAMAGE) ?: return line

        // Handle based on effect type
        return when {
            effect in SUMMONING_EFFECTS -> {
                val type = if (damage > 0) EntityType.MONSTER else EntityType.MONTAG
                val oldId = abs(damage)
                val newId = mappedDef.getMapping(type, oldId)?.let {
                    if (damage > 0) it else -it
                } ?: damage
                ModUtils.replaceId(line, damage, newId)
            }

            effect in ENCHANTMENT_EFFECTS -> {
                val newId = mappedDef.getMapping(EntityType.ENCHANTMENT, damage) ?: damage
                ModUtils.replaceId(line, damage, newId)
            }

            else -> line
        }
    }
}