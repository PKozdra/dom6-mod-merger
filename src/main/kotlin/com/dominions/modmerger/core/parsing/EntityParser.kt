package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.domain.ModDefinition
import mu.KLogger
import mu.KotlinLogging

class EntityParser(
    private val entityProcessor: EntityProcessor = EntityProcessor()
) {
    private val logger: KLogger = KotlinLogging.logger {}

    fun handleEntityDefinition(line: String, definition: ModDefinition) {
        entityProcessor.detectEntity(line)?.let { match ->
            val type = match.type
            val id = match.oldId

            when {
                // Handle new entity definitions
                isNewEntityDefinition(line) -> {
                    definition.addDefinedId(type, id)
                    logger.trace { "Added new ${type.name.lowercase()}: $id" }
                }

                // Handle entity selections (editing existing)
                isEntitySelection(line) -> {
                    if (ModRanges.Validator.isValidModdingId(type, id)) {
                        definition.addDefinedId(type, id)
                    } else {
                        definition.addVanillaEditedId(type, id)
                    }
                    logger.trace { "Selected ${type.name.lowercase()}: $id" }
                }

                // Handle entity usage/reference
                isEntityUsage(line) -> {
                    definition.addImplicitDefinition(type)
                    logger.trace { "Used ${type.name.lowercase()}: $id" }
                }
            }
        }
    }

    private fun isNewEntityDefinition(line: String): Boolean =
        line.matches(ModPatterns.NEW_NUMBERED_MONSTER) ||
                line.matches(ModPatterns.NEW_NUMBERED_WEAPON) ||
                line.matches(ModPatterns.NEW_NUMBERED_ARMOR) ||
                line.matches(ModPatterns.NEW_NUMBERED_SITE) ||
                line.matches(ModPatterns.NEW_NUMBERED_ITEM) ||
                line.matches(ModPatterns.NEW_UNNUMBERED_MONSTER) ||
                line.matches(ModPatterns.NEW_UNNUMBERED_WEAPON) ||
                line.matches(ModPatterns.NEW_UNNUMBERED_ARMOR) ||
                line.matches(ModPatterns.NEW_UNNUMBERED_SITE) ||
                line.matches(ModPatterns.NEW_UNNUMBERED_ITEM)

    private fun isEntitySelection(line: String): Boolean =
        line.matches(ModPatterns.SELECT_NUMBERED_MONSTER) ||
                line.matches(ModPatterns.SELECT_NUMBERED_WEAPON) ||
                line.matches(ModPatterns.SELECT_NUMBERED_ARMOR) ||
                line.matches(ModPatterns.SELECT_NUMBERED_SITE) ||
                line.matches(ModPatterns.SELECT_NUMBERED_ITEM) ||
                line.matches(ModPatterns.SELECT_NUMBERED_NAMETYPE) ||
                line.matches(ModPatterns.SELECT_NUMBERED_MONTAG) ||
                line.matches(ModPatterns.SELECT_NUMBERED_EVENTCODE) ||
                line.matches(ModPatterns.SELECT_NUMBERED_RESTRICTED_ITEM)

    private fun isEntityUsage(line: String): Boolean =
        line.matches(ModPatterns.USE_MONSTER) ||
                line.matches(ModPatterns.USE_NUMBERED_WEAPON) ||
                line.matches(ModPatterns.USE_NUMBERED_ARMOR) ||
                line.matches(ModPatterns.USE_NUMBERED_ITEM) ||
                line.matches(ModPatterns.USE_NUMBERED_SITE) ||
                line.matches(ModPatterns.USE_NUMBERED_NATION) ||
                line.matches(ModPatterns.USE_NAMETYPE) ||
                line.matches(ModPatterns.USE_NUMBERED_MONTAG) ||
                line.matches(ModPatterns.USE_NUMBERED_EVENTCODE) ||
                line.matches(ModPatterns.USE_NUMBERED_RESTRICTED_ITEM) ||
                line.matches(ModPatterns.USE_GLOBAL_ENCHANTMENT)

    // Simplified specialized handlers that delegate to main handler
    fun handleRestrictedItem(line: String, definition: ModDefinition) {
        handleEntityDefinition(line, definition)
    }

    fun handleMontag(line: String, definition: ModDefinition) {
        handleEntityDefinition(line, definition)
    }

    fun handlePopType(line: String, definition: ModDefinition) {
        handleEntityDefinition(line, definition)
    }
}