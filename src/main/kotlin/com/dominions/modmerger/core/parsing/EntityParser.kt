package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.core.processing.EntityProcessor
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.ModDefinition
import mu.KLogger
import mu.KotlinLogging

class EntityParser(
    private val entityProcessor: EntityProcessor = EntityProcessor()
) {
    private val logger: KLogger = KotlinLogging.logger {}

    fun handleEntityDefinition(line: String, definition: ModDefinition) {
        entityProcessor.detectEntity(line)?.let { match ->
            when {
                line.matches(ModPatterns.NEW_NUMBERED_MONSTER) ||
                        line.matches(ModPatterns.NEW_NUMBERED_WEAPON) ||
                        line.matches(ModPatterns.NEW_NUMBERED_ARMOR) ||
                        line.matches(ModPatterns.NEW_NUMBERED_SITE) ||
                        line.matches(ModPatterns.NEW_NUMBERED_ITEM) -> {
                    definition.addDefinedId(match.type, match.oldId)
                    logger.trace { "Added numbered ${match.type.name.lowercase()}: ${match.oldId}" }
                }
                line.matches(ModPatterns.SELECT_NUMBERED_MONSTER) ||
                        line.matches(ModPatterns.SELECT_NUMBERED_WEAPON) ||
                        line.matches(ModPatterns.SELECT_NUMBERED_ARMOR) ||
                        line.matches(ModPatterns.SELECT_NUMBERED_SITE) ||
                        line.matches(ModPatterns.SELECT_NUMBERED_ITEM) -> {
                    if (ModRanges.Validator.isValidModdingId(match.type, match.oldId)) {
                        definition.addDefinedId(match.type, match.oldId)
                    } else {
                        definition.addVanillaEditedId(match.type, match.oldId)
                    }
                    logger.trace { "Selected ${match.type.name.lowercase()}: ${match.oldId}" }
                }
            }
        }
    }

    fun handleRestrictedItem(line: String, definition: ModDefinition) {
        entityProcessor.detectEntity(line)?.let { match ->
            if (match.type == EntityType.RESTRICTED_ITEM) {
                definition.addDefinedId(EntityType.RESTRICTED_ITEM, match.oldId)
                logger.trace { "Added restricted item: ${match.oldId}" }
            }
        }
    }

    fun handleMontag(line: String, definition: ModDefinition) {
        entityProcessor.detectEntity(line)?.let { match ->
            if (match.type == EntityType.MONTAG) {
                definition.addDefinedId(EntityType.MONTAG, match.oldId)
                logger.trace { "Added montag: ${match.oldId}" }
            }
        }
    }

    fun handlePopType(line: String, definition: ModDefinition) {
        entityProcessor.detectEntity(line)?.let { match ->
            if (match.type == EntityType.POPTYPE) {
                definition.addDefinedId(EntityType.POPTYPE, match.oldId)
                logger.trace { "Added population type: ${match.oldId}" }
            }
        }
    }
}