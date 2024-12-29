package com.dominions.modmerger.core.processing

import com.dominions.modmerger.constants.GameConstants
import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.gamedata.GameDataProvider
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.utils.ModUtils

/**
 * This SpellBlockProcessor buffers an entire spell block so we can:
 *   1) Replace #newspell with #selectspell <assignedId> in final output.
 *   2) Map #damage only after we see #effect (summoning or enchantment).
 *   3) Remove extra blank lines at flush time.
 *   4) Pass other lines (#restricted, #descr, etc.) to EntityProcessor for normal references.
 */
class SpellBlockProcessor(
    private val gameDataProvider: GameDataProvider
) : Logging {

    /**
     * Summoning / Enchantment / None classification for #effect IDs.
     */
    sealed class SpellEffect {
        object None : SpellEffect()
        object Summoning : SpellEffect()
        object Enchantment : SpellEffect()

        companion object {
            fun fromId(effectId: Long?): SpellEffect {
                if (effectId == null) return None
                return when (effectId) {
                    in GameConstants.SpellEffects.SUMMONING_EFFECTS -> Summoning
                    in GameConstants.SpellEffects.ENCHANTMENT_EFFECTS -> Enchantment
                    else -> None
                }
            }
        }
    }

    /**
     * Represents a single spell block.
     * We'll store lines in [bufferedLines] and only transform them at flush (#end).
     *
     * @param assignedSpellId  The ID we want to use if we are forcibly converting
     *                         "#newspell" -> "#selectspell <assignedSpellId>".
     */
    data class SpellBlock(
        val assignedSpellId: Long? = null,
        var effectId: Long? = null,
        var effectType: SpellEffect = SpellEffect.None,
        val bufferedLines: MutableList<String> = mutableListOf()
    )

    /** The currently open spell block, or null if we're not in one. */
    private var currentBlock: SpellBlock? = null

    /** True if we have an active spell block. */
    fun isInSpellBlock(): Boolean = (currentBlock != null)

    /**
     * Called by ModContentWriter when we see #newspell or #selectspell (start of a block).
     * If we have assigned an ID (e.g. mapping an old ID to a new one),
     * pass it in [assignedSpellId] so we can replace #newspell with #selectspell at flush time.
     */
    fun startBlock(startLine: String, assignedSpellId: Long? = null) {
        // If there's an existing block, forcibly end it or discard it:
        currentBlock?.let {
            warn("Started a new spell block while another was open. Forcing end of old block.")
            endBlock(forceFlush = false)
        }
        currentBlock = SpellBlock(assignedSpellId = assignedSpellId).also { block ->
            block.bufferedLines.add(startLine)
        }
        //debug("SpellBlockProcessor: started new block with line='$startLine", useDispatcher = false)
    }

    /**
     * Forcibly ends the current block without seeing #end in the file.
     * If forceFlush=true, we might flush partially. If false, we discard.
     */
    private fun endBlock(forceFlush: Boolean) {
        val block = currentBlock
        if (block != null && forceFlush) {
            // Possibly flush partial content. Or just discard.
            warn("Forcing flush on block with ${block.bufferedLines.size} lines.")
        } else {
            block?.let { warn("Discarding block with ${it.bufferedLines.size} lines.") }
        }
        currentBlock = null
    }

    /**
     * Called line-by-line once we're inside a block,
     * until we see #end, at which point we flush the entire block.
     *
     * We buffer lines in [currentBlock], not returning them immediately.
     * On #end, we transform them and return as a single multi-line string.
     *
     * @return Pair of (processedLine, comment).
     *         Usually ("", null) except at #end, where we return the entire final block string.
     */
    fun handleSpellLine(
        line: String,
        mappedDef: MappedModDefinition,
        modDef: ModDefinition,
        entityProcessor: EntityProcessor
    ): Pair<String, String?> {

        val block = currentBlock ?: throw IllegalStateException("No active spell block.")
        val trimmed = line.trim()

        // If #end => flush the block
        if (trimmed.startsWith("#end")) {
            block.bufferedLines.add(line) // store the #end line
            val finalLines = flushBlock(block, mappedDef, modDef, entityProcessor)
            currentBlock = null
            // Return them as one multi-line chunk. No comment.
            return finalLines.joinToString("\n") to null
        }

        // If #effect => store effect ID, but no transformation yet
        if (trimmed.startsWith("#effect")) {
            val effectId = parseNumericArg(line)
            block.effectId = effectId
            block.effectType = SpellEffect.fromId(effectId)
            block.bufferedLines.add(line)
            return "" to null // skip immediate output
        }

        // If #copyspell => lookup and replace effect
        if (trimmed.startsWith("#copyspell")) {
            val spellId = ModUtils.extractId(line, ModPatterns.SPELL_COPY_ID)
            if (spellId != null) {
                gameDataProvider.getSpellEffect(spellId)?.let { effect ->
                    block.effectId = effect.recordId
                    block.effectType = SpellEffect.fromId(effect.effectNumber)
                    trace("Set effect from copied spell $spellId: effect=${effect.effectNumber}")
                }
            } else {
                ModUtils.extractString(line, ModPatterns.SPELL_COPY_NAME)?.let { name ->
                    gameDataProvider.getSpellByName(name)?.let { spell ->
                        gameDataProvider.getSpellEffect(spell.id)?.let { effect ->
                            block.effectId = effect.recordId
                            block.effectType = SpellEffect.fromId(effect.effectNumber)
                            trace("Set effect from copied spell '$name': effect=${effect.effectNumber}")
                        }
                    }
                }
            }
        }

        // Otherwise, store the line. #damage lines are also stored unmodified for now.
        block.bufferedLines.add(line)
        return "" to null
    }

    /**
     * Called at #end to transform the buffered lines.
     *  - We can replace "#newspell" with "#selectspell <assignedSpellId>" if assignedSpellId != null.
     *  - We can now map #damage lines if effectType is Summoning or Enchantment.
     *  - We pass other lines to entityProcessor for normal references.
     *  - We remove extra blank lines at the end to keep it tidy.
     */
    private fun flushBlock(
        block: SpellBlock,
        mappedDef: MappedModDefinition,
        modDef: ModDefinition,
        entityProcessor: EntityProcessor
    ): List<String> {

        val outputLines = mutableListOf<String>()

        for (rawLine in block.bufferedLines) {
            val trimmed = rawLine.trim()

            // A) If we see "#newspell" and have assignedSpellId => skip it, produce replaced lines
            if (trimmed.startsWith("#newspell") && block.assignedSpellId != null) {
                val assigned = block.assignedSpellId
                outputLines.add("-- MOD MERGER: Converted #newspell to #selectspell with assigned ID $assigned")
                outputLines.add("#selectspell $assigned")
                continue
            }

            // B) If #damage => map now that we know the effect
            if (trimmed.startsWith("#damage")) {
                val (mappedLine, comment) = mapDamageLine(rawLine, block.effectType, mappedDef)
                comment?.let { outputLines.add(it) }
                outputLines.add(mappedLine)
                continue
            }

            // C) #effect => we can keep as-is or do more if you want
            //    #end => keep as-is
            //    or pass the line to entityProcessor
            val processed = entityProcessor.processEntity(
                line = rawLine,
                mappedDef = mappedDef,
                remapCommentWriter = { type, oldId, newId ->
                    "-- MOD MERGER: Remapped ${type.name} $oldId -> $newId"
                },
                modDef = modDef
            )
            processed.remapComment?.let { outputLines.add(it) }
            outputLines.add(processed.line)
        }

        // After collecting all lines, remove consecutive blank lines
        return removeExtraBlanks(outputLines)
    }

    /**
     * If effect = Summoning => treat #damage as monster or montag
     * If effect = Enchantment => treat #damage as enchantment
     * Otherwise => do nothing
     */
    private fun mapDamageLine(
        line: String,
        effect: SpellEffect,
        mappedDef: MappedModDefinition
    ): Pair<String, String?> {
        val oldId = parseNumericArg(line) ?: return line to null
        return when (effect) {
            is SpellEffect.Summoning -> mapSummoningDamage(line, oldId, mappedDef)
            is SpellEffect.Enchantment -> mapEnchantmentDamage(line, oldId, mappedDef)
            else -> line to null
        }
    }

    private fun mapSummoningDamage(
        originalLine: String,
        oldId: Long,
        mappedDef: MappedModDefinition
    ): Pair<String, String?> {
        val isMontag = (oldId < 0)
        val absId = kotlin.math.abs(oldId)
        val newId = if (isMontag) {
            mappedDef.getMapping(EntityType.MONTAG, absId)
        } else {
            mappedDef.getMapping(EntityType.MONSTER, absId)
        }
        if (newId == absId) {
            // no change
            return originalLine to null
        }
        val replaced = if (isMontag) {
            ModUtils.replaceId(originalLine, oldId, -newId)
        } else {
            ModUtils.replaceId(originalLine, oldId, newId)
        }
        val comment = if (isMontag) {
            "-- MOD MERGER: Summoning => Remapped Montag $absId -> $newId"
        } else {
            "-- MOD MERGER: Summoning => Remapped Monster $absId -> $newId"
        }
        return replaced to comment
    }

    private fun mapEnchantmentDamage(
        originalLine: String,
        oldId: Long,
        mappedDef: MappedModDefinition
    ): Pair<String, String?> {
        val newId = mappedDef.getMapping(EntityType.ENCHANTMENT, oldId)
        if (newId == oldId) {
            return originalLine to null
        }
        val replaced = ModUtils.replaceId(originalLine, oldId, newId)
        val comment = "-- MOD MERGER: Enchantment => Remapped $oldId -> $newId"
        return replaced to comment
    }

    /**
     * Utility: parse the numeric argument from lines like "#damage 2202" or "#effect 17".
     */
    private fun parseNumericArg(line: String): Long? {
        val tokens = line.split("\\s+".toRegex())
        if (tokens.size < 2) return null
        return tokens[1].toLongOrNull()
    }

    /**
     * Utility: remove consecutive blank lines from the final output.
     */
    private fun removeExtraBlanks(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var lastBlank = false
        for (l in lines) {
            val isBlankLine = l.isBlank()
            if (isBlankLine && lastBlank) {
                // skip repeated blank
                continue
            }
            result.add(l)
            lastBlank = isBlankLine
        }
        return result
    }

    // So we can detect truly blank lines with no characters
    private fun String.isBlank(): Boolean = all { it.isWhitespace() }
}
