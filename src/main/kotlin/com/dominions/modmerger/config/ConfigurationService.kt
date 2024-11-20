// src/main/kotlin/com/dominions/modmerger/config/ConfigurationService.kt
package com.dominions.modmerger.config

import com.dominions.modmerger.domain.EntityType
import java.util.Properties
import java.io.FileInputStream

class ConfigurationService {
    private val config: Properties = Properties()

    init {
        loadDefaultConfig()
        loadUserConfig()
    }

    fun getStartId(type: EntityType): Int = when(type) {
        EntityType.WEAPON -> config.getProperty("range.weapon.start", "1000").toInt()
        EntityType.ARMOR -> config.getProperty("range.armor.start", "400").toInt()
        EntityType.MONSTER -> config.getProperty("range.monster.start", "5000").toInt()
        EntityType.SPELL -> config.getProperty("range.spell.start", "2000").toInt()
        EntityType.ITEM -> config.getProperty("range.item.start", "700").toInt()
        EntityType.SITE -> config.getProperty("range.site.start", "1700").toInt()
        EntityType.NATION -> config.getProperty("range.nation.start", "150").toInt()
        EntityType.NAME_TYPE -> config.getProperty("range.nametype.start", "170").toInt()
        EntityType.ENCHANTMENT -> config.getProperty("range.enchantment.start", "200").toInt()
        else -> throw IllegalArgumentException("No range defined for type $type")
    }

    fun getEndId(type: EntityType): Int = when(type) {
        EntityType.WEAPON -> config.getProperty("range.weapon.end", "3999").toInt()
        EntityType.ARMOR -> config.getProperty("range.armor.end", "1999").toInt()
        EntityType.MONSTER -> config.getProperty("range.monster.end", "19999").toInt()
        EntityType.SPELL -> config.getProperty("range.spell.end", "7999").toInt()
        EntityType.ITEM -> config.getProperty("range.item.end", "1999").toInt()
        EntityType.SITE -> config.getProperty("range.site.end", "3999").toInt()
        EntityType.NATION -> config.getProperty("range.nation.end", "499").toInt()
        EntityType.NAME_TYPE -> config.getProperty("range.nametype.end", "399").toInt()
        EntityType.ENCHANTMENT -> config.getProperty("range.enchantment.end", "9999").toInt()
        else -> throw IllegalArgumentException("No range defined for type $type")
    }

    private fun loadDefaultConfig() {
        config.apply {
            // Load default values
            setProperty("range.weapon.start", "1000")
            setProperty("range.weapon.end", "3999")
            // ... add other defaults
        }
    }

    private fun loadUserConfig() {
        val configFile = java.io.File("modmerger.properties")
        if (configFile.exists()) {
            FileInputStream(configFile).use {
                config.load(it)
            }
        }
    }
}