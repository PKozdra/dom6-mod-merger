// src/main/kotlin/com/dominions/modmerger/core/parsing/EntityParser.kt
package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.utils.ModUtils
import mu.KLogger
import mu.KotlinLogging


class EntityParser {
    private val logger: KLogger = KotlinLogging.logger {}

    fun handleEntityDefinition(line: String, definition: ModDefinition) {
        // Try each entity type in sequence
        if (handleWeapon(line, definition)) return
        if (handleArmor(line, definition)) return
        if (handleMonster(line, definition)) return
        if (handleSite(line, definition)) return
        if (handleItem(line, definition)) return
        if (handleNation(line, definition)) return
        if (handleNameType(line, definition)) return
    }

    private fun handleWeapon(line: String, definition: ModDefinition): Boolean {
        when {
            ModUtils.extractId(line, ModPatterns.NEW_NUMBERED_WEAPON)?.let { id ->
                definition.addDefinedId(EntityType.WEAPON, id)
                logger.trace { "Added numbered weapon: $id" }
                true
            } == true -> return true

            line.matches(ModPatterns.NEW_UNNUMBERED_WEAPON) -> {
                definition.addImplicitDefinition(EntityType.WEAPON)
                logger.trace { "Added implicit weapon" }
                return true
            }

            ModUtils.extractId(line, ModPatterns.SELECT_NUMBERED_WEAPON)?.let { id ->
                if (id >= ModRanges.Modding.WEAPON_START) {
                    definition.addDefinedId(EntityType.WEAPON, id)
                } else {
                    definition.addVanillaEditedId(EntityType.WEAPON, id)
                }
                logger.trace { "Selected weapon: $id" }
                true
            } == true -> return true
        }
        return false
    }

    private fun handleArmor(line: String, definition: ModDefinition): Boolean {
        when {
            ModUtils.extractId(line, ModPatterns.NEW_NUMBERED_ARMOR)?.let { id ->
                definition.addDefinedId(EntityType.ARMOR, id)
                logger.trace { "Added numbered armor: $id" }
                true
            } == true -> return true

            line.matches(ModPatterns.NEW_UNNUMBERED_ARMOR) -> {
                definition.addImplicitDefinition(EntityType.ARMOR)
                logger.trace { "Added implicit armor" }
                return true
            }

            ModUtils.extractString(line, ModPatterns.NEW_NAMED_ARMOR)?.let {
                definition.addImplicitDefinition(EntityType.ARMOR)
                logger.trace { "Added named armor: $it" }
                true
            } == true -> return true

            ModUtils.extractId(line, ModPatterns.SELECT_NUMBERED_ARMOR)?.let { id ->
                if (id >= ModRanges.Modding.ARMOR_START) {
                    definition.addDefinedId(EntityType.ARMOR, id)
                } else {
                    definition.addVanillaEditedId(EntityType.ARMOR, id)
                }
                logger.trace { "Selected armor: $id" }
                true
            } == true -> return true
        }
        return false
    }

    private fun handleMonster(line: String, definition: ModDefinition): Boolean {
        when {
            ModUtils.extractId(line, ModPatterns.NEW_NUMBERED_MONSTER)?.let { id ->
                definition.addDefinedId(EntityType.MONSTER, id)
                logger.trace { "Added numbered monster: $id" }
                true
            } == true -> return true

            line.matches(ModPatterns.NEW_UNNUMBERED_MONSTER) -> {
                definition.addImplicitDefinition(EntityType.MONSTER)
                logger.trace { "Added implicit monster" }
                return true
            }

            ModUtils.extractId(line, ModPatterns.SELECT_NUMBERED_MONSTER)?.let { id ->
                if (id >= ModRanges.Modding.MONSTER_START) {
                    definition.addDefinedId(EntityType.MONSTER, id)
                } else {
                    definition.addVanillaEditedId(EntityType.MONSTER, id)
                }
                logger.trace { "Selected monster: $id" }
                true
            } == true -> return true
        }
        return false
    }

    private fun handleSite(line: String, definition: ModDefinition): Boolean {
        when {
            ModUtils.extractId(line, ModPatterns.NEW_NUMBERED_SITE)?.let { id ->
                definition.addDefinedId(EntityType.SITE, id)
                logger.trace { "Added numbered site: $id" }
                true
            } == true -> return true

            line.matches(ModPatterns.NEW_UNNUMBERED_SITE) -> {
                definition.addImplicitDefinition(EntityType.SITE)
                logger.trace { "Added implicit site" }
                return true
            }

            ModUtils.extractId(line, ModPatterns.SELECT_NUMBERED_SITE)?.let { id ->
                if (id >= ModRanges.Modding.SITE_START) {
                    definition.addDefinedId(EntityType.SITE, id)
                } else {
                    definition.addVanillaEditedId(EntityType.SITE, id)
                }
                logger.trace { "Selected site: $id" }
                true
            } == true -> return true
        }
        return false
    }

    private fun handleItem(line: String, definition: ModDefinition): Boolean {
        when {
            ModUtils.extractId(line, ModPatterns.NEW_NUMBERED_ITEM)?.let { id ->
                definition.addDefinedId(EntityType.ITEM, id)
                logger.trace { "Added numbered item: $id" }
                true
            } == true -> return true

            line.matches(ModPatterns.NEW_UNNUMBERED_ITEM) -> {
                definition.addImplicitDefinition(EntityType.ITEM)
                logger.trace { "Added implicit item" }
                return true
            }

            ModUtils.extractId(line, ModPatterns.SELECT_NUMBERED_ITEM)?.let { id ->
                if (id >= ModRanges.Modding.ITEM_START) {
                    definition.addDefinedId(EntityType.ITEM, id)
                } else {
                    definition.addVanillaEditedId(EntityType.ITEM, id)
                }
                logger.trace { "Selected item: $id" }
                true
            } == true -> return true
        }
        return false
    }

    private fun handleNation(line: String, definition: ModDefinition): Boolean {
        when {
            line.matches(ModPatterns.NEW_UNNUMBERED_NATION) -> {
                definition.addImplicitDefinition(EntityType.NATION)
                logger.trace { "Added implicit nation" }
                return true
            }

            ModUtils.extractId(line, ModPatterns.SELECT_NUMBERED_NATION)?.let { id ->
                if (id >= ModRanges.Modding.NATION_START) {
                    definition.addDefinedId(EntityType.NATION, id)
                } else {
                    definition.addVanillaEditedId(EntityType.NATION, id)
                }
                logger.trace { "Selected nation: $id" }
                true
            } == true -> return true
        }
        return false
    }

    private fun handleNameType(line: String, definition: ModDefinition): Boolean {
        ModUtils.extractId(line, ModPatterns.SELECT_NUMBERED_NAMETYPE)?.let { id ->
            if (id >= ModRanges.Modding.NAMETYPE_START) {
                definition.addDefinedId(EntityType.NAME_TYPE, id)
            } else {
                definition.addVanillaEditedId(EntityType.NAME_TYPE, id)
            }
            logger.trace { "Selected name type: $id" }
            return true
        }
        return false
    }

    fun handleRestrictedItem(line: String, definition: ModDefinition) {
        ModUtils.extractId(line, ModPatterns.SELECT_NUMBERED_RESTRICTED_ITEM)?.let { id ->
            definition.addDefinedId(EntityType.RESTRICTED_ITEM, id)
            logger.trace { "Added restricted item: $id" }
        }
    }

    fun handleMontag(line: String, definition: ModDefinition) {
        ModUtils.extractId(line, ModPatterns.SELECT_NUMBERED_MONTAG)?.let { id ->
            definition.addDefinedId(EntityType.MONTAG, id)
            logger.trace { "Added montag: $id" }
        }
    }

    fun handlePopType(line: String, definition: ModDefinition) {
        ModUtils.extractId(line, ModPatterns.SELECT_NUMBERED_POPTYPE)?.let { id ->
            definition.addDefinedId(EntityType.POPTYPE, id)
            logger.debug { "Added population type: $id" }
        }
    }
}