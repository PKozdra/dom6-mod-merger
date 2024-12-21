package com.dominions.modmerger.core.processing

import com.dominions.modmerger.constants.GameConstants
import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.utils.ModUtils

class SpellBlockProcessor : Logging {
    data class SpellBlock(
        val type: SpellBlockType,
        val id: Long?, // For selectspell
        val lines: MutableList<String> = mutableListOf(),
        var effect: Long? = null,
        var damage: Long? = null,
        var copyspell: String? = null,
        var selectspell: String? = null,
        var damageMapping: Pair<Long, Long>? = null
    ) {
        fun hasSummoningEffect(): Boolean =
            effect?.let { it in GameConstants.SpellEffects.SUMMONING_EFFECTS } ?: false

        fun hasEnchantmentEffect(): Boolean =
            effect?.let { it in GameConstants.SpellEffects.ENCHANTMENT_EFFECTS } ?: false

        fun reset() {
            lines.clear()
            effect = null
            damage = null
            copyspell = null
            selectspell = null
            damageMapping = null
        }
    }

    enum class SpellBlockType {
        NEW_SPELL,
        SELECT_SPELL
    }

    var currentBlock: SpellBlock? = null
        internal set

    fun startNewBlock(line: String) {
        currentBlock?.let {
            warn("Starting new spell block while previous block was not closed. Previous block type: ${it.type}")
            it.reset()
        }

        val trimmedLine = line.trim()
        val type = when {
            trimmedLine.startsWith("#newspell") -> SpellBlockType.NEW_SPELL
            trimmedLine.startsWith("#selectspell") -> SpellBlockType.SELECT_SPELL
            else -> throw IllegalArgumentException("Invalid spell block start: $line")
        }

        val id = if (type == SpellBlockType.SELECT_SPELL) {
            ModUtils.extractId(line, ModPatterns.SPELL_SELECT_ID)
        } else null

        currentBlock = SpellBlock(type = type, id = id)
        debug("Started new ${type.name} block${id?.let { " with ID $it" } ?: ""}", useDispatcher = false)
    }

    fun processSpellLine(line: String, mappedDef: MappedModDefinition): SpellBlock {
        val block = currentBlock ?: throw IllegalStateException("No active spell block")
        block.lines.add(line)

        val trimmedLine = line.trim()

        // Add complete line debugging
        debug("RAW LINE: '$line'", useDispatcher = false)
        debug("TRIMMED LINE: '$trimmedLine'", useDispatcher = false)
        debug("Does line match SPELL_EFFECT? ${ModPatterns.SPELL_EFFECT.matches(trimmedLine)}", useDispatcher = false)
        debug("Does line match SPELL_DAMAGE? ${ModPatterns.SPELL_DAMAGE.matches(trimmedLine)}", useDispatcher = false)

        try {
            debug("Processing line: $trimmedLine", useDispatcher = false)
            when {
                ModPatterns.SPELL_EFFECT.matches(trimmedLine) -> {
                    debug("Matched SPELL_EFFECT pattern", useDispatcher = false)
                    handleEffectLine(line, block, mappedDef)
                }
                ModPatterns.SPELL_DAMAGE.matches(trimmedLine) -> {
                    handleDamageLine(line, block, mappedDef)
                }
                ModPatterns.SPELL_COPY_ID.matches(trimmedLine) ||
                        ModPatterns.SPELL_COPY_NAME.matches(trimmedLine) -> {
                    block.copyspell = line
                    // If we have damage but no effect, check if this makes it a known summoning spell
                    if (block.damage != null && block.effect == null && isKnownSummoningSpell(block)) {
                        processDamageLine(block.damage!!, block, mappedDef)
                    }
                }
                ModPatterns.SPELL_SELECT_ID.matches(trimmedLine) ||
                        ModPatterns.SPELL_SELECT_NAME.matches(trimmedLine) -> {
                    block.selectspell = line
                    // Same check for selectspell
                    if (block.damage != null && block.effect == null && isKnownSummoningSpell(block)) {
                        processDamageLine(block.damage!!, block, mappedDef)
                    }
                }
            }
        } catch (e: Exception) {
            error("Error processing spell line: $line", e)
            throw SpellProcessingException("Failed to process spell line: ${e.message}", e)
        }

        return block
    }

    private fun handleEffectLine(line: String, block: SpellBlock, mappedDef: MappedModDefinition) {
        debug("Attempting to handle effect line: $line", useDispatcher = false)
        debug("SPELL_EFFECT pattern matches? ${ModPatterns.SPELL_EFFECT.matches(line.trim())}", useDispatcher = false)

        val effectId = ModUtils.extractId(line, ModPatterns.SPELL_EFFECT)
        debug("Extracted effect ID: $effectId", useDispatcher = false)

        block.effect = effectId
        debug("Captured effect $effectId in spell block", useDispatcher = false)

        // If we have pending damage and now have enough context to process it
        if (block.damage != null) {
            debug("We have pending damage ${block.damage}", useDispatcher = false)
            debug("hasSummoningEffect: ${block.hasSummoningEffect()}", useDispatcher = false)
            debug("hasEnchantmentEffect: ${block.hasEnchantmentEffect()}", useDispatcher = false)

            if (block.hasSummoningEffect() || block.hasEnchantmentEffect()) {
                debug("Processing previously deferred damage now that we have effect ${block.effect}", useDispatcher = false)
                processDamageLine(block.damage!!, block, mappedDef)
            }
        }
    }

    private fun handleDamageLine(line: String, block: SpellBlock, mappedDef: MappedModDefinition) {
        val damageId = ModUtils.extractId(line, ModPatterns.SPELL_DAMAGE) ?: return
        block.damage = damageId
        debug("Found damage ID $damageId", useDispatcher = false)

        // Process immediately if we have enough context
        if (block.effect != null || isKnownSummoningSpell(block)) {
            debug("Processing damage immediately with effect ${block.effect}", useDispatcher = false)
            processDamageLine(damageId, block, mappedDef)
        } else {
            debug("Deferring damage processing until effect or spell type is known", useDispatcher = false)
        }
    }

    private fun processDamageLine(damageId: Long, block: SpellBlock, mappedDef: MappedModDefinition) {
        debug("Processing damage line with id $damageId and effect ${block.effect}", useDispatcher = false)

        when {
            block.hasEnchantmentEffect() -> {
                debug("Processing as enchantment effect", useDispatcher = false)
                processEnchantmentDamage(damageId, block, mappedDef)
            }
            block.hasSummoningEffect() || isKnownSummoningSpell(block) -> {
                debug("Processing as summoning effect", useDispatcher = false)
                processSummoningDamage(damageId, block, mappedDef)
            }
        }
    }

    private fun processEnchantmentDamage(damageId: Long, block: SpellBlock, mappedDef: MappedModDefinition) {
        val newId = mappedDef.getMapping(EntityType.ENCHANTMENT, damageId)
        if (damageId != newId) {
            block.damageMapping = damageId to newId
            debug("Mapped enchantment in spell: $damageId -> $newId", useDispatcher = false)
        }
    }

    private fun processSummoningDamage(damageId: Long, block: SpellBlock, mappedDef: MappedModDefinition) {
        debug("Current montag mappings: ${mappedDef.getMappingsByType()[EntityType.MONTAG]}", useDispatcher = false)

        if (damageId > 0) {
            processMonsterDamage(damageId, block, mappedDef)
        } else {
            processMontagDamage(damageId, block, mappedDef)
        }
    }

    private fun processMonsterDamage(damageId: Long, block: SpellBlock, mappedDef: MappedModDefinition) {
        val newId = mappedDef.getMapping(EntityType.MONSTER, damageId)
        if (damageId != newId) {
            block.damageMapping = damageId to newId
            debug("Mapped monster in summoning spell: $damageId -> $newId", useDispatcher = false)
        }
    }

    private fun processMontagDamage(damageId: Long, block: SpellBlock, mappedDef: MappedModDefinition) {
        val montagId = -damageId
        val newMontagId = mappedDef.getMapping(EntityType.MONTAG, montagId)
        if (montagId != newMontagId) {
            block.damageMapping = damageId to -newMontagId
            debug("Mapped montag in summoning spell: $montagId -> $newMontagId", useDispatcher = false)
        }
    }

    private fun isKnownSummoningSpell(block: SpellBlock): Boolean {
        block.copyspell?.let { copyspell ->
            return isKnownSummoningSpellByLine(copyspell)
        }

        block.selectspell?.let { selectspell ->
            return isKnownSummoningSpellByLine(selectspell)
        }

        return false
    }

    private fun isKnownSummoningSpellByLine(line: String): Boolean {
        val spellId = ModUtils.extractId(line, ModPatterns.SPELL_COPY_ID) ?:
        ModUtils.extractId(line, ModPatterns.SPELL_SELECT_ID)

        val spellName = ModUtils.extractName(line, ModPatterns.SPELL_COPY_NAME) ?:
        ModUtils.extractName(line, ModPatterns.SPELL_SELECT_NAME)

        return spellId in GameConstants.SpellEffects.KNOWN_SUMMON_SPELL_IDS ||
                spellName?.lowercase() in GameConstants.SpellEffects.KNOWN_SUMMON_SPELL_NAMES
    }
}

class SpellProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)