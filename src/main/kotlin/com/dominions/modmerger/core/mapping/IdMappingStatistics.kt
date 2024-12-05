// File: IdMappingStatistics.kt
package com.dominions.modmerger.core.mapping

import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.MappedModDefinition
import com.dominions.modmerger.domain.ModConflict
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.infrastructure.Logging

/**
 * Handles statistics collection and reporting for the ID mapping process.
 * Separates statistics logic from core mapping functionality.
 */
class IdMappingStatistics : Logging {
    private data class EntityTypeStatistics(
        val totalVanillaRange: Int,
        val totalModdingRange: Int,
        val usedVanillaIds: Set<Long>,
        val modifiedVanillaIds: Set<Long>,
        val usedModdingIds: Set<Long>,
        val reservedForImplicit: Int,
        val remappedIds: Int,
        val conflictCount: Int
    ) {
        val availableModdingIds: Int = totalModdingRange - usedModdingIds.size - reservedForImplicit
        val vanillaUtilizationPercent: Double = usedVanillaIds.size * 100.0 / totalVanillaRange
        val moddingUtilizationPercent: Double = (usedModdingIds.size + reservedForImplicit) * 100.0 / totalModdingRange
    }

    private data class MappingStatistics(
        val entityStats: Map<EntityType, EntityTypeStatistics>,
        val totalConflictsResolved: Int,
        val totalRemappedIds: Int,
        val totalImplicitDefinitions: Int,
        val modsWithMostConflicts: List<Pair<String, Int>>,
        val mostContestedIds: Map<EntityType, List<Pair<Long, Int>>>,
        val vanillaModificationHotspots: Map<EntityType, List<Pair<Long, Int>>>,
        val processingTimeMs: Long
    )

    fun generateAndLogStatistics(
        modDefinitions: Map<String, ModDefinition>,
        mappedDefinitions: Map<String, MappedModDefinition>,
        conflicts: Map<EntityType, List<ModConflict>>,
        vanillaModifications: Map<EntityType, Map<Long, List<String>>>,
        state: IdMapper.MappingState,
        processingTimeMs: Long
    ) {
        val stats = collectStatistics(
            modDefinitions,
            mappedDefinitions,
            conflicts,
            vanillaModifications,
            state,
            processingTimeMs
        )
        logStatistics(stats)
    }

    private fun collectStatistics(
        modDefinitions: Map<String, ModDefinition>,
        mappedDefinitions: Map<String, MappedModDefinition>,
        conflicts: Map<EntityType, List<ModConflict>>,
        vanillaModifications: Map<EntityType, Map<Long, List<String>>>,
        state: IdMapper.MappingState,
        processingTimeMs: Long
    ): MappingStatistics {
        val entityStats = collectEntityStatistics(state, vanillaModifications, mappedDefinitions, conflicts)
        val modConflictCounts = collectModConflictCounts(conflicts)
        val contestedIds = findMostContestedIds(modDefinitions)
        val vanillaHotspots = findVanillaHotspots(vanillaModifications)

        return MappingStatistics(
            entityStats = entityStats,
            totalConflictsResolved = conflicts.values.sumOf { it.size },
            totalRemappedIds = mappedDefinitions.values.sumOf { it.getAllMappings().size },
            totalImplicitDefinitions = state.implicitCounts.values.sum(),
            modsWithMostConflicts = modConflictCounts.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value }
                .filter { it.second > 0 },
            mostContestedIds = contestedIds,
            vanillaModificationHotspots = vanillaHotspots,
            processingTimeMs = processingTimeMs
        )
    }

    private fun collectEntityStatistics(
        state: IdMapper.MappingState,
        vanillaModifications: Map<EntityType, Map<Long, List<String>>>,
        mappedDefinitions: Map<String, MappedModDefinition>,
        conflicts: Map<EntityType, List<ModConflict>>
    ): Map<EntityType, EntityTypeStatistics> {
        return EntityType.entries.associate { type ->
            val vanillaEnd = getVanillaEnd(type)
            val moddingRange = getModdingRange(type)
            val usedIds = state.usedIds[type] ?: emptySet()

            type to EntityTypeStatistics(
                totalVanillaRange = (vanillaEnd + 1).toInt(),
                totalModdingRange = (moddingRange.last - moddingRange.first + 1).toInt(),
                usedVanillaIds = usedIds.filter { it <= vanillaEnd }.toSet(),
                modifiedVanillaIds = vanillaModifications[type]?.keys ?: emptySet(),
                usedModdingIds = usedIds.filter { it > vanillaEnd }.toSet(),
                reservedForImplicit = state.implicitCounts[type] ?: 0,
                remappedIds = mappedDefinitions.values.sumOf { mapped ->
                    mapped.getMappingsByType()[type]?.size ?: 0
                },
                conflictCount = conflicts[type]?.size ?: 0
            )
        }
    }

    private fun getVanillaEnd(type: EntityType): Long = when (type) {
        EntityType.WEAPON -> ModRanges.Vanilla.WEAPON_END
        EntityType.ARMOR -> ModRanges.Vanilla.ARMOR_END
        EntityType.MONSTER -> ModRanges.Vanilla.MONSTER_END
        EntityType.SPELL -> ModRanges.Vanilla.SPELL_END
        EntityType.ITEM -> ModRanges.Vanilla.ITEM_END
        EntityType.SITE -> ModRanges.Vanilla.SITE_END
        EntityType.NATION -> ModRanges.Vanilla.NATION_END
        EntityType.NAME_TYPE -> ModRanges.Vanilla.NAMETYPE_END
        EntityType.ENCHANTMENT -> ModRanges.Vanilla.ENCHANTMENT_END
        EntityType.MONTAG -> 0L
        EntityType.EVENT_CODE -> ModRanges.Vanilla.EVENTCODE_END
        EntityType.POPTYPE -> ModRanges.Vanilla.POPTYPE_END
        EntityType.RESTRICTED_ITEM -> ModRanges.Vanilla.RESTRICTED_ITEM_END
    }

    private fun getModdingRange(type: EntityType): LongRange = ModRanges.Modding.run {
        when (type) {
            EntityType.WEAPON -> WEAPON_START..WEAPON_END
            EntityType.ARMOR -> ARMOR_START..ARMOR_END
            EntityType.MONSTER -> MONSTER_START..MONSTER_END
            EntityType.SPELL -> SPELL_START..SPELL_END
            EntityType.ITEM -> ITEM_START..ITEM_END
            EntityType.SITE -> SITE_START..SITE_END
            EntityType.NATION -> NATION_START..NATION_END
            EntityType.NAME_TYPE -> NAMETYPE_START..NAMETYPE_END
            EntityType.ENCHANTMENT -> ENCHANTMENT_START..ENCHANTMENT_END
            EntityType.MONTAG -> MONTAG_START..MONTAG_END
            EntityType.EVENT_CODE -> EVENTCODE_START..EVENTCODE_END
            EntityType.POPTYPE -> POPTYPE_START..POPTYPE_END
            EntityType.RESTRICTED_ITEM -> RESTRICTED_ITEM_START..RESTRICTED_ITEM_END
        }
    }

    private fun collectModConflictCounts(
        conflicts: Map<EntityType, List<ModConflict>>
    ): Map<String, Int> {
        val conflictCounts = mutableMapOf<String, Int>()

        conflicts.values.flatten().forEach { conflict ->
            conflictCounts.merge(conflict.firstMod, 1, Int::plus)
            conflictCounts.merge(conflict.secondMod, 1, Int::plus)
        }

        return conflictCounts
    }

    private fun findMostContestedIds(
        modDefinitions: Map<String, ModDefinition>
    ): Map<EntityType, List<Pair<Long, Int>>> {
        return EntityType.entries.associateWith { type ->
            modDefinitions.values
                .flatMap { def -> def.getDefinition(type).definedIds }
                .groupBy { it }
                .mapValues { it.value.size }
                .filter { it.value > 1 }
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value }
        }.filterValues { it.isNotEmpty() }
    }

    private fun findVanillaHotspots(
        vanillaModifications: Map<EntityType, Map<Long, List<String>>>
    ): Map<EntityType, List<Pair<Long, Int>>> {
        return vanillaModifications.mapValues { (_, idMods) ->
            idMods.entries
                .sortedByDescending { it.value.size }
                .take(5)
                .map { it.key to it.value.size }
        }
    }

    private fun logStatistics(stats: MappingStatistics) {
        logGeneralStatistics(stats)
        logEntityTypeStatistics(stats.entityStats)
        logConflictStatistics(stats)
        logDetailedStatistics(stats)
    }

    private fun logGeneralStatistics(stats: MappingStatistics) {
        info("=== Mapping Process Statistics ===", useDispatcher = true)
        info("Total processing time: ${stats.processingTimeMs}ms", useDispatcher = true)
        info("Total conflicts resolved: ${stats.totalConflictsResolved}", useDispatcher = true)
        info("Total IDs remapped: ${stats.totalRemappedIds}", useDispatcher = true)
        info("Total implicit definitions reserved: ${stats.totalImplicitDefinitions}", useDispatcher = true)
    }

    private fun logEntityTypeStatistics(entityStats: Map<EntityType, EntityTypeStatistics>) {
        entityStats.forEach { (type, typeStats) ->
            info("", useDispatcher = true)
            info("=== $type Statistics ===", useDispatcher = true)
            info(
                "Vanilla Range: Used ${typeStats.usedVanillaIds.size}/${typeStats.totalVanillaRange} (${
                    String.format(
                        "%.1f",
                        typeStats.vanillaUtilizationPercent
                    )
                }%)", useDispatcher = true
            )
            if (typeStats.modifiedVanillaIds.isNotEmpty()) {
                info("Modified Vanilla IDs: ${typeStats.modifiedVanillaIds.size}", useDispatcher = true)
            }
            info(
                "Modding Range: Used ${typeStats.usedModdingIds.size + typeStats.reservedForImplicit}/${typeStats.totalModdingRange} (${
                    String.format(
                        "%.1f",
                        typeStats.moddingUtilizationPercent
                    )
                }%)", useDispatcher = true
            )
            info("  - Explicitly Used: ${typeStats.usedModdingIds.size}", useDispatcher = true)
            info("  - Reserved for Implicit: ${typeStats.reservedForImplicit}", useDispatcher = true)
            info("  - Available: ${typeStats.availableModdingIds}", useDispatcher = true)
            if (typeStats.remappedIds > 0) {
                info("Remapped IDs: ${typeStats.remappedIds}", useDispatcher = true)
            }
            if (typeStats.conflictCount > 0) {
                info("Conflicts Resolved: ${typeStats.conflictCount}", useDispatcher = true)
            }
        }
    }

    private fun logConflictStatistics(stats: MappingStatistics) {
        if (stats.modsWithMostConflicts.isNotEmpty()) {
            info("", useDispatcher = true)
            info("=== Mods with Most Conflicts ===", useDispatcher = true)
            stats.modsWithMostConflicts.forEach { (modName, count) ->
                info("$modName: $count conflicts", useDispatcher = true)
            }
        }

        if (stats.mostContestedIds.isNotEmpty()) {
            info("", useDispatcher = true)
            info("=== Most Contested IDs ===", useDispatcher = true)
            stats.mostContestedIds.forEach { (type, contested) ->
                info("$type: ${contested.joinToString { "${it.first} (${it.second} mods)" }}", useDispatcher = true)
            }
        }

        if (stats.vanillaModificationHotspots.isNotEmpty()) {
            info("", useDispatcher = true)
            info("=== Vanilla Modification Hotspots ===", useDispatcher = true)
            stats.vanillaModificationHotspots.forEach { (type, hotspots) ->
                if (hotspots.isNotEmpty()) {
                    info("$type: ${hotspots.joinToString { "${it.first} (${it.second} mods)" }}", useDispatcher = true)
                }
            }
        }
    }

    private fun logDetailedStatistics(stats: MappingStatistics) {
        debug("", useDispatcher = false)
        debug("=== Detailed Processing Statistics ===", useDispatcher = false)
        stats.entityStats.forEach { (type, typeStats) ->
            debug("$type:", useDispatcher = false)
            debug("  Vanilla IDs: ${typeStats.usedVanillaIds.sorted()}", useDispatcher = false)
            debug("  Modified Vanilla: ${typeStats.modifiedVanillaIds.sorted()}", useDispatcher = false)
            debug("  Modding IDs: ${typeStats.usedModdingIds.sorted()}", useDispatcher = false)
        }
    }
}