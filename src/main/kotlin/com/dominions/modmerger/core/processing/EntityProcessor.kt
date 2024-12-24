package com.dominions.modmerger.core.processing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.constants.ModPatterns.SPELL_BLOCK_START
import com.dominions.modmerger.constants.RegexWrapper
import com.dominions.modmerger.core.mapping.IdManager
import com.dominions.modmerger.core.mapping.IdRegistrationResult
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.infrastructure.Logging
import com.dominions.modmerger.utils.ModUtils

class EntityProcessor(private val idManager: IdManager) : Logging {
    data class ProcessedEntity(
        val line: String,
        val remapComment: String?
    )

    data class EntityMatch(
        val type: EntityType,
        val id: Long?,         // From extractId
        val name: String?,     // From extractName
        val patternName: String,
        val isUnnumbered: Boolean = false
    )

    companion object {
        // Convert patterns to optimized versions at initialization
        private val USAGE_PATTERNS = mapOf(
            EntityType.MONSTER to listOf(ModPatterns.USE_MONSTER),
            EntityType.WEAPON to listOf(ModPatterns.USE_NUMBERED_WEAPON),
            EntityType.ARMOR to listOf(ModPatterns.USE_NUMBERED_ARMOR),
            EntityType.ITEM to listOf(ModPatterns.USE_NUMBERED_ITEM),
            EntityType.SPELL to listOf(ModPatterns.USE_NUMBERED_SPELL),
            EntityType.SITE to listOf(ModPatterns.USE_NUMBERED_SITE),
            EntityType.NATION to listOf(ModPatterns.USE_NUMBERED_NATION),
            EntityType.MONTAG to listOf(ModPatterns.USE_NUMBERED_MONTAG),
            EntityType.EVENT_CODE to listOf(ModPatterns.USE_NUMBERED_EVENTCODE),
            EntityType.RESTRICTED_ITEM to listOf(ModPatterns.USE_NUMBERED_RESTRICTED_ITEM),
            EntityType.ENCHANTMENT to listOf(ModPatterns.USE_GLOBAL_ENCHANTMENT)
        ).mapValues { (_, patterns) ->
            patterns.map { RegexWrapper.fromRegex(it) }
        }

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
                ModPatterns.SPELL_SELECT_ID
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

        private val UNNUMBERED_PATTERNS = mapOf(
            ModPatterns.NEW_UNNUMBERED_MONSTER to EntityType.MONSTER,
            ModPatterns.NEW_UNNUMBERED_WEAPON to EntityType.WEAPON,
            ModPatterns.NEW_UNNUMBERED_ARMOR to EntityType.ARMOR,
            ModPatterns.NEW_UNNUMBERED_ITEM to EntityType.ITEM,
            ModPatterns.NEW_UNNUMBERED_SITE to EntityType.SITE,
            ModPatterns.NEW_UNNUMBERED_NATION to EntityType.NATION,
            ModPatterns.NEW_UNNUMBERED_SPELL to EntityType.SPELL
        )

        private val REFERENCE_PATTERNS = mapOf(
            EntityType.MONSTER to listOf(ModPatterns.MONSTER_REFERENCES),
            EntityType.WEAPON to listOf(ModPatterns.WEAPON_REFERENCES),
            EntityType.ARMOR to listOf(ModPatterns.ARMOR_REFERENCES),
            EntityType.ITEM to listOf(ModPatterns.ITEM_REFERENCES),
            EntityType.SITE to listOf(ModPatterns.SITE_REFERENCES),
            EntityType.NATION to listOf(ModPatterns.NATION_REFERENCES),
        ).mapValues { (_, patterns) ->
            patterns.map { RegexWrapper.fromRegex(it) }
        }

        private val NUMBERED_PATTERNS = mapOf(
            EntityType.MONSTER to ModPatterns.NEW_NUMBERED_MONSTER,
            EntityType.WEAPON to ModPatterns.NEW_NUMBERED_WEAPON,
            EntityType.ARMOR to ModPatterns.NEW_NUMBERED_ARMOR,
            EntityType.ITEM to ModPatterns.NEW_NUMBERED_ITEM,
            EntityType.SITE to ModPatterns.NEW_NUMBERED_SITE,
            EntityType.NATION to ModPatterns.SELECT_NUMBERED_NATION
        ).mapValues { (_, pattern) -> RegexWrapper.fromRegex(pattern) }

        // Frequently used patterns
        private val SPELL_BLOCK_START = RegexWrapper.fromRegex(ModPatterns.SPELL_BLOCK_START)
        private val END_PATTERN = RegexWrapper.fromRegex(ModPatterns.END)
    }

    private val spellBlockProcessor = SpellBlockProcessor()

    fun detectEntity(line: String): EntityMatch? {
        val trimmedLine = line.trim()

        // Fast path check - if line doesn't start with #, it can't be an entity
        if (!trimmedLine.startsWith("#")) return null

        trace("Detecting entity in line: $trimmedLine", useDispatcher = false)

        DEFINITION_PATTERNS.forEach { (type, patterns) ->
            patterns.forEach { pattern ->
                if (pattern.matches(trimmedLine)) {
                    val patternName = pattern.getPatternName()
                    val id = ModUtils.extractId(trimmedLine, pattern.toRegex())
                    val name = ModUtils.extractName(trimmedLine, pattern.toRegex())
                    val isUnnumbered = UNNUMBERED_PATTERNS.keys.any {
                        it.pattern == pattern.toRegex().pattern
                    }

                    trace("Extracted ID: $id, Name: $name (unnumbered: $isUnnumbered) [Pattern: $patternName, Type: $type]", useDispatcher = false)

                    return EntityMatch(
                        type = type,
                        id = id,
                        name = name,
                        patternName = patternName,
                        isUnnumbered = isUnnumbered
                    )
                }
            }
        }
        return null
    }

    private fun replaceInPattern(pattern: String, replacements: Map<String, String>): String {
        var result = pattern
        replacements.forEach { (placeholder, value) ->
            result = result.replace(Regex(Regex.escape(placeholder)), value)
        }
        return result
    }

    private data class IdAssignment(
        val entityType: EntityType,
        val ids: MutableList<Long> = mutableListOf()
    )

    private val idAssignments = mutableMapOf<String, MutableMap<EntityType, IdAssignment>>()

    private val nextImplicitDefinitionIndex = mutableMapOf<Pair<EntityType, String>, Int>()

    fun cleanImplicitIdIterators() {
        nextImplicitDefinitionIndex.clear()
    }

    private fun assignIdToUnnumbered(
        line: String,
        type: EntityType,
        modDef: ModDefinition,
        mappedDef: MappedModDefinition
    ): Pair<String, Long>? {
        val unnumberedPattern = UNNUMBERED_PATTERNS.entries.find { (pattern, _) ->
            RegexWrapper.fromRegex(pattern).matches(line)
        } ?: return null

        // Each time we see an unnumbered entity for (type, modDef.name),
        // we increment the implicit index and fetch the assigned ID.
        val key = type to modDef.name
        val index = nextImplicitDefinitionIndex.getOrDefault(key, 0)
        nextImplicitDefinitionIndex[key] = index + 1

        // Now retrieve the final ID that was assigned for this implicit index
        val entityDef = modDef.getDefinition(type)
        val assignedId = entityDef.getAssignedIdForImplicitIndex(index)
            ?: run {
                debug("No assigned ID found for $type implicit index=$index in mod=${modDef.name}")
                return null
            }

        // 'mappedDef.getMapping' handles the case where that assignedId got remapped
        // (but typically it's the same if your IdMapper is setting final IDs).
        val newId = mappedDef.getMapping(type, assignedId)

        // Insert the ID into the #newmonster line
        val commandEnd = line.indexOf(' ', line.indexOf('#'))
        val newLine = if (commandEnd != -1) {
            line.substring(0, commandEnd) + " " + newId + line.substring(commandEnd)
        } else {
            "$line $newId"
        }

        return newLine to newId
    }

    // Add a function to log assignments for a mod
    fun logIdAssignmentsForMod(modName: String) {
        idAssignments[modName]?.forEach { (_, assignment) ->
            val ranges = assignment.ids.sorted().fold(mutableListOf<Pair<Long, Long>>()) { acc, id ->
                if (acc.isEmpty()) {
                    acc.add(id to id)
                } else {
                    val (start, end) = acc.last()
                    if (id == end + 1) {
                        acc[acc.lastIndex] = start to id
                    } else {
                        acc.add(id to id)
                    }
                }
                acc
            }

            val rangeStr = ranges.joinToString(", ") { (start, end) ->
                if (start == end) "$start" else "$start-$end"
            }

            if (assignment.ids.isNotEmpty()) {
                warn("Assigned ${assignment.ids.size} IDs for unnumbered entity ${assignment.entityType.name} in mod $modName: $rangeStr")
            }
        }
        idAssignments.remove(modName)
    }

    @Throws(IllegalStateException::class)
    fun processEntity(
        line: String,
        mappedDef: MappedModDefinition,
        remapCommentWriter: (EntityType, Long, Long) -> String,
        modDef: ModDefinition
    ): ProcessedEntity {
        trace("ProcessEntity for line: $line", useDispatcher = false)

        try {
            // Handle spell blocks if active
            spellBlockProcessor.currentBlock?.let { currentBlock ->
                trace("ProcessEntity spell block line: $line", useDispatcher = false)
                return handleSpellBlock(line, mappedDef, remapCommentWriter)
            }

            // Fast path - if line doesn't start with #, no need for regex matching
            if (!line.startsWith("#")) {
                trace("ProcessEntity returning, haven't found #", useDispatcher = false)
                return ProcessedEntity(line, null)
            }

            // Check if this is the start of a new spell block
            if (SPELL_BLOCK_START.matches(line)) {
                trace("ProcessEntity starting new spell block", useDispatcher = false)
                spellBlockProcessor.startNewBlock(line)
                return ProcessedEntity(line, null)
            }

            // Handle cases where we have name references
            processNameReference(line, mappedDef, modDef)?.let { return it }

            // Detect entity and handle unnumbered definitions
            detectEntity(line)?.let { match ->
                if (match.isUnnumbered) {
                    val (newLine, newId) = assignIdToUnnumbered(line, match.type, modDef, mappedDef)
                        ?: return ProcessedEntity(line, null)

                    return ProcessedEntity (
                        newLine,
                        remapCommentWriter(match.type, -1, newId)
                    )
                }

                // Handle normal numbered entities
                match.id?.let { id ->
                    val newId = mappedDef.getMapping(match.type, id)
                    if (id != newId) {
                        return ProcessedEntity(
                            ModUtils.replaceId(line, id, newId),
                            remapCommentWriter(match.type, id, newId)
                        )
                    }
                }
            }

            // Process usage patterns if no definition patterns matched
            processUsagePatterns(line, mappedDef, remapCommentWriter)?.let {
                return it
            }

            // Handle special cases before returning unmodified line
            if (line.contains("Keledones")) {
                val newLine = when {
                    line.trim().startsWith("#name") -> {
                        line.replace(""""Craft Keledones"""", """"Craft Keledone"""")
                    }
                    else -> line.replace("Craft Keledones", "Craft Keledone")
                }

                if (newLine != line) {
                    return ProcessedEntity(
                        newLine,
                        "-- MOD MERGER: Temporary fix - replaced 'Craft Keledones' with 'Craft Keledone'. TODO: Implement better alternative"
                    )
                }
            }

            return ProcessedEntity(line, null)
        } catch (e: Exception) {
            error("Error processing entity line: $line", e)
            throw IllegalStateException("Failed to process entity: ${e.message}", e)
        }
    }

    private fun processNameReference(
        line: String,
        mappedDef: MappedModDefinition,
        modDef: ModDefinition
    ): ProcessedEntity? {
        // Skip definition commands
        if (line.trimStart().startsWith("#new")) {
            return null
        }

        REFERENCE_PATTERNS.forEach { (type, patterns) ->
            for (pattern in patterns) {
                pattern.toRegex().find(line)?.let { match ->
                    // Extract the name maintaining original spacing
                    val nameMatch = Regex(""""([^"]+)"""").find(line) ?: return@let
                    val name = nameMatch.groupValues[1]

                    modDef.getDefinition(type).getIdForName(name)?.let { id ->
                        val newId = mappedDef.getMapping(type, id)
                        //debug("Replacing $type name reference '$name' with ID $id -> $newId in command: ${line.substringBefore(" ")}")

                        // Replace the quoted name with ID while preserving spacing
                        val prefix = line.substring(0, nameMatch.range.first)
                        val suffix = line.substring(nameMatch.range.last + 1)
                        val newLine = prefix + newId + suffix

                        return ProcessedEntity(
                            newLine,
                            "-- MOD MERGER: Replaced ${type.name} name reference '$name' with ID $newId"
                        )
                    }
                }
            }
        }
        return null
    }

    private fun handleSpellBlock(
        line: String,
        mappedDef: MappedModDefinition,
        remapCommentWriter: (EntityType, Long, Long) -> String
    ): ProcessedEntity {
        val processedSpell = spellBlockProcessor.processSpellLine(line, mappedDef)

        // Check if this is the end of the spell block
        if (END_PATTERN.matches(line)) {
            spellBlockProcessor.currentBlock = null

            // Apply any deferred damage line mapping
            processedSpell.damageMapping?.let { (oldId, newId) ->
                val entityType = if (oldId > 0) EntityType.MONSTER else EntityType.MONTAG
                return ProcessedEntity(
                    ModUtils.replaceId(line, oldId, newId),
                    remapCommentWriter(entityType, oldId, newId)
                )
            }
        }

        return ProcessedEntity(line, null)
    }

    private fun processUsagePatterns(
        line: String,
        mappedDef: MappedModDefinition,
        remapCommentWriter: (EntityType, Long, Long) -> String
    ): ProcessedEntity? {
        // Fast path - check if line contains a number before regex matching
        if (!line.any { it.isDigit() }) return null

        USAGE_PATTERNS.forEach { (entityType, patterns) ->
            for (pattern in patterns) {
                ModUtils.extractId(line, pattern.toRegex())?.let { oldId ->
                    val newId = mappedDef.getMapping(entityType, oldId)
                    if (oldId != newId) {
                        val comment = remapCommentWriter(entityType, oldId, newId)
                        val newLine = ModUtils.replaceId(line, oldId, newId)
                        return ProcessedEntity(newLine, comment)
                    }
                }
            }
        }
        return null
    }
}