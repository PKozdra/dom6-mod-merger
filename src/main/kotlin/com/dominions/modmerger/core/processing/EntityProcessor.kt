package com.dominions.modmerger.core.processing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.constants.RegexWrapper
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.gamedata.GameDataProvider
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.utils.ModUtils

/**
 * EntityProcessor handles non-spell-block ID mappings:
 *  - Unnumbered definitions (#newmonster without ID => auto-assign, etc.).
 *  - Name references (#monster "Troll" => #monster 123).
 *  - Usage patterns (#restricted 407 => #restricted 1001, #weapon, #armor, etc.).
 *
 * Spell-block commands (e.g. #newspell, #damage, #effect, #selectspell)
 * are handled elsewhere (e.g. SpellBlockProcessor).
 */
class EntityProcessor(
    private val implicitIdProcessor: ImplicitIdProcessor = ImplicitIdProcessor(),
    private val gameDataProvider: GameDataProvider
) : Logging {

    data class ProcessedEntity(
        val line: String,
        val remapComment: String?
    )

    data class EntityMatch(
        val type: EntityType,
        val id: Long?,         // extracted from the command
        val name: String?,     // for referencing by name
        val patternName: String,
        val isUnnumbered: Boolean = false
    )

    enum class ProcessingContext {
        MERCENARY_BLOCK,
    }

    // Track active contexts
    private val activeContexts = mutableSetOf<ProcessingContext>()

    // Block starter regexes matched to contexts
    private val contextStarters = mapOf(
        ModPatterns.NEWMERC_PATTERN to ProcessingContext.MERCENARY_BLOCK,
    )

    /**
     * Check if a specific context is currently active
     */
    private fun isInContext(context: ProcessingContext): Boolean {
        return context in activeContexts
    }

    companion object {
        // Patterns for usage references (#restricted <id>, #monster <id>, #weapon <id>, etc.)
        private val USAGE_PATTERNS = mapOf(
            EntityType.MONSTER         to listOf(ModPatterns.USE_MONSTER),
            EntityType.WEAPON          to listOf(ModPatterns.USE_NUMBERED_WEAPON),
            EntityType.ARMOR           to listOf(ModPatterns.USE_NUMBERED_ARMOR),
            EntityType.ITEM            to listOf(ModPatterns.USE_NUMBERED_ITEM),
            EntityType.SPELL           to listOf(ModPatterns.USE_NUMBERED_SPELL),
            EntityType.SITE            to listOf(ModPatterns.USE_NUMBERED_SITE),
            EntityType.NATION          to listOf(ModPatterns.USE_NUMBERED_NATION),
            EntityType.MONTAG          to listOf(ModPatterns.USE_NUMBERED_MONTAG),
            EntityType.EVENT_CODE      to listOf(ModPatterns.USE_NUMBERED_EVENTCODE),
            EntityType.RESTRICTED_ITEM to listOf(ModPatterns.USE_NUMBERED_RESTRICTED_ITEM),
            EntityType.ENCHANTMENT     to listOf(ModPatterns.USE_GLOBAL_ENCHANTMENT),
            EntityType.NAME_TYPE       to listOf(ModPatterns.USE_NAMETYPE),
            EntityType.POPTYPE         to listOf(ModPatterns.USE_NUMBERED_POPTYPE)
        ).mapValues { (_, patterns) ->
            patterns.map { RegexWrapper.fromRegex(it) }
        }

        // Patterns for new/select entity definitions
        private val DEFINITION_PATTERNS = mapOf(
            EntityType.MONSTER to listOf(
                ModPatterns.NEW_NUMBERED_MONSTER,
                ModPatterns.NEW_UNNUMBERED_MONSTER,
                ModPatterns.SELECT_NUMBERED_MONSTER
            ),
            EntityType.WEAPON to listOf(
                ModPatterns.NEW_NUMBERED_WEAPON,
                ModPatterns.NEW_UNNUMBERED_WEAPON,
                ModPatterns.SELECT_NUMBERED_WEAPON
            ),
            EntityType.ARMOR to listOf(
                ModPatterns.NEW_NUMBERED_ARMOR,
                ModPatterns.NEW_UNNUMBERED_ARMOR,
                ModPatterns.SELECT_NUMBERED_ARMOR
            ),
            EntityType.ITEM to listOf(
                ModPatterns.NEW_NUMBERED_ITEM,
                ModPatterns.NEW_UNNUMBERED_ITEM,
                ModPatterns.SELECT_NUMBERED_ITEM
            ),
            EntityType.SPELL to listOf(
                ModPatterns.SPELL_SELECT_ID,
                ModPatterns.NEW_UNNUMBERED_SPELL
            ),
            EntityType.SITE to listOf(
                ModPatterns.NEW_NUMBERED_SITE,
                ModPatterns.NEW_UNNUMBERED_SITE,
                ModPatterns.SELECT_NUMBERED_SITE
            ),
            EntityType.NATION to listOf(
                ModPatterns.SELECT_NUMBERED_NATION,
                ModPatterns.NEW_UNNUMBERED_NATION,
            ),
            EntityType.NAME_TYPE to listOf(
                ModPatterns.SELECT_NUMBERED_NAMETYPE
            ),
            EntityType.MONTAG to listOf(
                ModPatterns.SELECT_NUMBERED_MONTAG
            ),
            EntityType.EVENT_CODE to listOf(
                ModPatterns.SELECT_NUMBERED_EVENTCODE
            ),
            EntityType.RESTRICTED_ITEM to listOf(
                ModPatterns.SELECT_NUMBERED_RESTRICTED_ITEM
            ),
            EntityType.POPTYPE to listOf(
                ModPatterns.SELECT_NUMBERED_POPTYPE
            )
        ).mapValues { (_, patterns) ->
            patterns.map { RegexWrapper.fromRegex(it) }
        }

        // Patterns for unnumbered definitions (#newmonster, #newweapon, #newspell with no ID, etc.)
        private val UNNUMBERED_PATTERNS = mapOf(
            ModPatterns.NEW_UNNUMBERED_MONSTER to EntityType.MONSTER,
            ModPatterns.NEW_UNNUMBERED_WEAPON  to EntityType.WEAPON,
            ModPatterns.NEW_UNNUMBERED_ARMOR   to EntityType.ARMOR,
            ModPatterns.NEW_UNNUMBERED_ITEM    to EntityType.ITEM,
            ModPatterns.NEW_UNNUMBERED_SITE    to EntityType.SITE,
            ModPatterns.NEW_UNNUMBERED_NATION  to EntityType.NATION,
            ModPatterns.NEW_UNNUMBERED_SPELL   to EntityType.SPELL
        )

        // Patterns for name references (#monster "Foo" => #monster <id>)
        private val REFERENCE_PATTERNS = mapOf(
            EntityType.MONSTER          to listOf(ModPatterns.MONSTER_REFERENCES),
            EntityType.WEAPON           to listOf(ModPatterns.WEAPON_REFERENCES),
            EntityType.ARMOR            to listOf(ModPatterns.ARMOR_REFERENCES),
            EntityType.ITEM             to listOf(ModPatterns.ITEM_REFERENCES),
            EntityType.SITE             to listOf(ModPatterns.SITE_REFERENCES),
            EntityType.NATION           to listOf(ModPatterns.NATION_REFERENCES),
            EntityType.SPELL            to listOf(ModPatterns.SPELL_REFERENCES),
            EntityType.RESTRICTED_ITEM  to listOf(ModPatterns.RESTRICTED_ITEM_REFERENCES),
        ).mapValues { (_, patterns) ->
            patterns.map { RegexWrapper.fromRegex(it) }
        }
    }

    /**
     * If you ever need to reset the counters for implicit ID assignment (#newmonster etc.).
     */
    fun cleanImplicitIdIterators() {
        implicitIdProcessor.cleanIndices()
    }

    /**
     * Main function for processing lines that are *not* in a spell block:
     *  - Checks if line is a comment or doesn't start with '#'; returns as-is.
     *  - Otherwise, tries references, usage patterns, unnumbered definitions, etc.
     */
    fun processEntity(
        line: String,
        mappedDef: MappedModDefinition,
        remapCommentWriter: (EntityType, Long, Long) -> String,
        modDef: ModDefinition,
    ): ProcessedEntity {
        val trimmedLine = line.trimStart()

        // 1) If it's a comment or doesn't start with '#', pass through
        if (trimmedLine.startsWith("--") || !trimmedLine.startsWith("#")) {
            return ProcessedEntity(line, null)
        }

        // Update context tracking
        updateProcessingContext(line)

        // 2) Do normal entity processing
        return processEntityLine(line, mappedDef, remapCommentWriter, modDef)
    }

    /**
     * Updates the active contexts based on the current line
     */
    private fun updateProcessingContext(line: String) {
        val trimmedLine = line.trim()

        if (trimmedLine.startsWith("#new")) {
            activeContexts.clear()
        }

        // Check for context starters using regex
        for ((pattern, context) in contextStarters) {
            if (pattern.matches(trimmedLine)) {
                activeContexts.add(context)
                break
            }
        }

        // Check for block end - clears all contexts
        if (ModPatterns.END.matches(trimmedLine)) {
            activeContexts.clear()
        }
    }

    /**
     * This is where we do:
     *  - detectEntity => see if it's a new or select entity command
     *  - unnumbered => assign ID if needed
     *  - usage patterns => #restricted <id>, #monster <id>, etc.
     *  - name references => #monster "Troll" => #monster 123
     *  - special fix-ups (like "Craft Keledones" => "Craft Keledone")
     */
    private fun processEntityLine(
        line: String,
        mappedDef: MappedModDefinition,
        remapCommentWriter: (EntityType, Long, Long) -> String,
        modDef: ModDefinition
    ): ProcessedEntity {

        // 1) Possibly handle name references (#monster "Foo" => #monster <id>)
        processNameReference(line, mappedDef, modDef)?.let { return it }

        // 2) Detect if it's a new or select entity => handle unnumbered definitions
        detectEntity(line)?.let { match ->
            if (match.isUnnumbered) {
                // e.g. #newmonster without an ID => assign one
                val processedLine = implicitIdProcessor.processImplicitDefinition(
                    line,
                    match.type,
                    modDef,
                    mappedDef
                ) ?: return ProcessedEntity(line, null)

                return ProcessedEntity(
                    processedLine.newLine,
                    processedLine.remapComment ?: remapCommentWriter(match.type, -1, processedLine.newId)
                )
            }

            // If it has a numeric ID => map it
            match.id?.let { oldId ->
                val newId = mappedDef.getMapping(match.type, oldId)
                if (oldId != newId) {
                    return ProcessedEntity(
                        ModUtils.replaceId(line, oldId, newId),
                        remapCommentWriter(match.type, oldId, newId)
                    )
                }
            }
        }

        // 3) If no definition patterns matched, check usage patterns (#restricted <id>, #armor <id>, etc.)
        processUsagePatterns(line, mappedDef, remapCommentWriter)?.let { return it }

        // 4) Lastly, handle special cases
        if (line.contains("Keledones")) {
            val newLine = if (line.trim().startsWith("#name")) {
                line.replace(""""Craft Keledones"""", """"Craft Keledone"""")
            } else {
                line.replace("Craft Keledones", "Craft Keledone")
            }
            if (newLine != line) {
                return ProcessedEntity(
                    newLine,
                    "-- MOD MERGER: Temporary fix - replaced 'Craft Keledones' with 'Craft Keledone'."
                )
            }
        }

        // If nothing changed, return as-is
        return ProcessedEntity(line, null)
    }

    /**
     * Looks for lines like:
     *   #monster "Troll"
     *   #weapon "Sword of Ice"
     *
     * Then replaces them with #monster 123 if it finds a known ID mapping for that name
     * (either in the mod, or in vanilla data)
     */
    private fun processNameReference(
        line: String,
        mappedDef: MappedModDefinition,
        modDef: ModDefinition
    ): ProcessedEntity? {
        // For instance, we don't want to convert #newmonster "Troll" => #monster <id> accidentally,
        // so we skip if line starts with #new
        if (line.trimStart().startsWith("#new")) {
            return null
        }

        // Skip mercenary blocks
        if (isInContext(ProcessingContext.MERCENARY_BLOCK) && ModPatterns.MERC_UNIT_PATTERNS.matches(line.trim())) {
            return null
        }

        REFERENCE_PATTERNS.forEach { (type, patterns) ->
            for (pattern in patterns) {
                pattern.toRegex().find(line)?.let {
                    val nameMatch = Regex(""""([^"]+)"""").find(line) ?: return@let
                    val name = nameMatch.groupValues[1]

                    // Try vanilla data
                    val vanillaId = when(type) {
                        EntityType.MONSTER -> gameDataProvider.getMonsterByName(name)?.id
                        EntityType.SPELL -> gameDataProvider.getSpellByName(name)?.id
                        else -> null
                    }

                    if (vanillaId != null) {
                        // For vanilla IDs, we use the same ID (no mapping needed)
                        return createProcessedEntity(line, nameMatch, type, name, vanillaId, isVanilla = true)
                    }

                    // Try mod definitions
                    modDef.getDefinition(type).getIdForName(name)?.let { oldId ->
                        val newId = mappedDef.getMapping(type, oldId)
                        return createProcessedEntity(line, nameMatch, type, name, newId)
                    }
                }
            }
        }
        return null
    }

    private fun createProcessedEntity(
        line: String,
        nameMatch: MatchResult,
        type: EntityType,
        name: String,
        newId: Long,
        isVanilla: Boolean = false
    ): ProcessedEntity {
        val prefix = line.substring(0, nameMatch.range.first)
        val suffix = line.substring(nameMatch.range.last + 1)
        val newLine = prefix + newId + suffix

        val comment = if (isVanilla) {
            "-- MOD MERGER: Replaced ${type.name} name '$name' with vanilla ID $newId"
        } else {
            "-- MOD MERGER: Replaced ${type.name} name '$name' with ID $newId"
        }

        return ProcessedEntity(newLine, comment)
    }

    /**
     * Looks for usage patterns (#monster <id>, #restricted <id>, #armor <id>, etc.)
     * If found, we do mappedDef.getMapping(...) and replace old ID => new ID.
     */
    private fun processUsagePatterns(
        line: String,
        mappedDef: MappedModDefinition,
        remapCommentWriter: (EntityType, Long, Long) -> String
    ): ProcessedEntity? {
        // Quick check: if no digits => skip
        if (!line.any { it.isDigit() }) return null

        USAGE_PATTERNS.forEach { (entityType, patterns) ->
            for (pattern in patterns) {
                ModUtils.extractId(line, pattern.toRegex())?.let { oldId ->
                    val newId = mappedDef.getMapping(entityType, oldId)
                    if (oldId != newId) {
                        val comment = remapCommentWriter(entityType, oldId, newId)
                        val replaced = ModUtils.replaceId(line, oldId, newId)
                        return ProcessedEntity(replaced, comment)
                    }
                }
            }
        }

        return null
    }

    /**
     * detectEntity is used to see if the line matches a "new" or "select" entity command,
     * so we know how to handle unnumbered definitions. E.g. #newmonster, #selectmonster, etc.
     */
    fun detectEntity(line: String): EntityMatch? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("#")) return null

        DEFINITION_PATTERNS.forEach { (type, patterns) ->
            for (pattern in patterns) {
                if (pattern.matches(trimmed)) {
                    val patName = pattern.getPatternName()
                    // For unnumbered patterns, we shouldn't try to extract ID
                    val isUnnum = UNNUMBERED_PATTERNS.keys.any {
                        it.pattern == pattern.toRegex().pattern
                    }
                    // Only try to extract id if it's not an unnumbered pattern
                    val extractedId = if (!isUnnum) {
                        ModUtils.extractId(trimmed, pattern.toRegex())
                    } else null
                    val extractedName = ModUtils.extractName(trimmed, pattern.toRegex())

                    return EntityMatch(
                        type = type,
                        id = extractedId,
                        name = extractedName,
                        patternName = patName,
                        isUnnumbered = isUnnum
                    )
                }
            }
        }
        return null
    }
}
