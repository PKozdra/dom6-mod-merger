package com.dominions.modmerger.core.mapping

import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.infrastructure.Logging

/**
 * Represents a range of valid IDs for an entity type.
 */
data class IdRange(
    val start: Long,
    val end: Long
) {
    fun contains(id: Long) = id in start..end
}

/**
 * Represents the result of attempting to register or remap an ID.
 */
sealed class IdRegistrationResult {
    data class Registered(val id: Long) : IdRegistrationResult()
    data class Remapped(val originalId: Long, val newId: Long) : IdRegistrationResult()
    data class VanillaConflict(val id: Long) : IdRegistrationResult()
    data class Error(val message: String) : IdRegistrationResult()
}

/**
 * Represents an assigned ID and its metadata.
 */
data class IdAssignment(
    val id: Long,
    val source: IdSource,
    val assignmentType: AssignmentType,
    val modName: String? = null
)

enum class IdSource {
    VANILLA,
    ORIGINAL_MOD,
    NEW_ASSIGNMENT
}

enum class AssignmentType {
    EXISTING,
    REMAPPED,
    GENERATED
}

/**
 * Manages ID assignments and remapping for mod entities.
 * Ensures IDs are unique within their ranges and handles conflicts appropriately.
 * Maintains sequential relationships when remapping IDs.
 */
class IdManager private constructor(
    private val moddingRanges: Map<EntityType, IdRange>,
    private val preferredStarts: Map<EntityType, Long>,
) : Logging {
    // Keep track of assigned IDs and next available ID per type
    private val assignments = mutableMapOf<EntityType, MutableMap<Long, IdAssignment>>()
    private val nextAvailableId = mutableMapOf<EntityType, Long>()

    init {
        EntityType.entries.forEach { type ->
            assignments[type] = mutableMapOf()
            nextAvailableId[type] = preferredStarts[type] ?: moddingRanges.getValue(type).start
        }
    }

    /**
     * Registers an ID for use or remaps it if necessary.
     * Handles vanilla conflicts and range validation.
     *
     * @param type The entity type
     * @param id The requested ID
     * @param modName The name of the mod requesting the ID
     * @param preferredNewId Optional preferred ID to use when remapping (for maintaining sequences)
     */
    fun registerOrRemapId(
        type: EntityType,
        id: Long,
        modName: String,
        preferredNewId: Long? = null
    ): IdRegistrationResult {
        val typeAssignments = assignments.getValue(type)

        // Handle existing assignments
        typeAssignments[id]?.let { existing ->
            return when (existing.source) {
                IdSource.VANILLA -> IdRegistrationResult.VanillaConflict(id)
                else -> createRemapping(type, id, modName, preferredNewId)
            }
        }

        // Handle new assignments
        return when {
            ModRanges.Validator.isVanillaId(type, id) -> {
                assignId(type, id, IdSource.VANILLA, AssignmentType.EXISTING)
                IdRegistrationResult.VanillaConflict(id)
            }
            ModRanges.Validator.isValidModdingId(type, id) -> {
                assignId(type, id, IdSource.ORIGINAL_MOD, AssignmentType.EXISTING, modName)
                IdRegistrationResult.Registered(id)
            }
            else -> createRemapping(type, id, modName, preferredNewId)
        }
    }

    /**
     * Creates a new ID assignment.
     */
    private fun assignId(
        type: EntityType,
        id: Long,
        source: IdSource,
        assignmentType: AssignmentType,
        modName: String? = null
    ) {
        assignments.getValue(type)[id] = IdAssignment(id, source, assignmentType, modName)
    }

    /**
     * Creates a remapping for an ID that cannot be used as-is.
     * Attempts to use preferred ID if provided, otherwise finds next available.
     */
    private fun createRemapping(
        type: EntityType,
        originalId: Long,
        modName: String,
        preferredNewId: Long? = null
    ): IdRegistrationResult {
        // Try preferred ID first if provided
        preferredNewId?.let { preferred ->
            if (isIdAvailable(type, preferred)) {
                assignId(type, preferred, IdSource.NEW_ASSIGNMENT, AssignmentType.REMAPPED, modName)
                return IdRegistrationResult.Remapped(originalId, preferred)
            }
        }

        // Find next available ID
        val newId = findNextAvailableId(type) ?: return IdRegistrationResult.Error(
            "No available IDs for type $type (trying to remap $originalId)"
        )

        assignId(type, newId, IdSource.NEW_ASSIGNMENT, AssignmentType.REMAPPED, modName)
        return IdRegistrationResult.Remapped(originalId, newId)
    }

    /**
     * Registers a new ID for an unnumbered entity.
     * Used when processing entities that don't have explicit IDs assigned.
     *
     * @param type The entity type
     * @param modName The name of the mod requesting the ID
     * @return Registration result containing the new ID or error
     */
    fun registerNewId(type: EntityType, modName: String): IdRegistrationResult {
        val newId = findNextAvailableId(type) ?: return IdRegistrationResult.Error(
            "No available IDs for type $type"
        )

        assignId(
            type = type,
            id = newId,
            source = IdSource.NEW_ASSIGNMENT,
            assignmentType = AssignmentType.GENERATED,
            modName = modName
        )

        return IdRegistrationResult.Registered(newId)
    }

    /**
     * Checks if an ID is available for use.
     */
    private fun isIdAvailable(type: EntityType, id: Long): Boolean {
        val range = moddingRanges.getValue(type)
        return id in range.start..range.end &&
                id !in assignments.getValue(type) &&
                !ModRanges.Validator.isVanillaId(type, id)
    }

    /**
     * Finds the next available ID for a type.
     * Tries preferred start, then sequential search, then range start.
     */
    private fun findNextAvailableId(type: EntityType): Long? {
        val range = moddingRanges.getValue(type)
        val typeAssignments = assignments.getValue(type)

        // Try preferred start
        preferredStarts[type]?.let { preferred ->
            if (isIdAvailable(type, preferred)) {
                nextAvailableId[type] = preferred + 1
                return preferred
            }
        }

        // Search from next available position
        var current = nextAvailableId.getValue(type)
        while (current <= range.end) {
            if (isIdAvailable(type, current)) {
                nextAvailableId[type] = current + 1
                return current
            }
            current++
        }

        // Try from range start
        current = range.start
        val maxSearch = nextAvailableId.getValue(type)
        while (current < maxSearch) {
            if (isIdAvailable(type, current)) {
                nextAvailableId[type] = current + 1
                return current
            }
            current++
        }

        return null
    }

    /**
     * Resets all assignments to initial state.
     */
    fun reset() {
        assignments.values.forEach { it.clear() }
        EntityType.entries.forEach { type ->
            nextAvailableId[type] = preferredStarts[type] ?: moddingRanges.getValue(type).start
        }
    }

    fun getAssignmentInfo(type: EntityType, id: Long): IdAssignment? =
        assignments[type]?.get(id)

    fun isIdUsed(type: EntityType, id: Long): Boolean =
        assignments[type]?.containsKey(id) == true

    companion object {
        fun createFromModRanges(): IdManager {
            val moddingRanges = ModRanges.Modding.run {
                mapOf(
                    EntityType.WEAPON to IdRange(WEAPON_START, WEAPON_END),
                    EntityType.ARMOR to IdRange(ARMOR_START, ARMOR_END),
                    EntityType.MONSTER to IdRange(MONSTER_START, MONSTER_END),
                    EntityType.SPELL to IdRange(SPELL_START, SPELL_END),
                    EntityType.ITEM to IdRange(ITEM_START, ITEM_END),
                    EntityType.SITE to IdRange(SITE_START, SITE_END),
                    EntityType.NATION to IdRange(NATION_START, NATION_END),
                    EntityType.NAME_TYPE to IdRange(NAMETYPE_START, NAMETYPE_END),
                    EntityType.ENCHANTMENT to IdRange(ENCHANTMENT_START, ENCHANTMENT_END),
                    EntityType.MONTAG to IdRange(MONTAG_START, MONTAG_END),
                    EntityType.EVENT_CODE to IdRange(EVENTCODE_START, EVENTCODE_END),
                    EntityType.POPTYPE to IdRange(POPTYPE_START, POPTYPE_END),
                    EntityType.RESTRICTED_ITEM to IdRange(RESTRICTED_ITEM_START, RESTRICTED_ITEM_END)
                )
            }

            val preferredStarts = mapOf(
                EntityType.WEAPON to 2250L,
                EntityType.ARMOR to 1250L,
                EntityType.MONSTER to 13500L,
                EntityType.NAME_TYPE to 250L,
                EntityType.SPELL to 5750L,
                EntityType.ENCHANTMENT to 7750L,
                EntityType.ITEM to 1450L,
                EntityType.SITE to 2150L,
                EntityType.NATION to 330L,
                EntityType.POPTYPE to 205L
            )

            return IdManager(moddingRanges, preferredStarts)
        }
    }
}