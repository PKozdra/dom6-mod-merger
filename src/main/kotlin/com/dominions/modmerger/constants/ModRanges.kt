// src/main/kotlin/com/dominions/modmerger/config/ModRanges.kt
package com.dominions.modmerger.constants

import com.dominions.modmerger.domain.EntityType
import kotlin.math.abs

object ModRanges {
    // highest vanilla number of each type in Dominions 6.37, from the id column of the CSVs in gamedata/dom6
    // update these together with the CSVs when a game update adds records
    object Vanilla {
        const val WEAPON_END: Long = 885L    // weapons.csv
        const val ARMOR_END: Long = 296L     // armors.csv
        const val MONSTER_END: Long = 4999L  // vanilla ends at 4136, #newmonster only takes 5000 and up
        const val NAMETYPE_END: Long = 169L
        const val SPELL_END: Long = 1473L    // spells.csv
        const val ENCHANTMENT_END: Long = 199L
        const val ITEM_END: Long = 529L      // BaseI.csv
        const val SITE_END: Long = 1405L     // MagicSites.csv
        const val NATION_END: Long = 134L    // nations.csv
        const val POPTYPE_END: Long = 124L
        const val RESTRICTED_ITEM_END: Long = 0L  // No vanilla restricted items
        const val EVENTCODE_END: Long = 0L  // 0 is default/reset code
        const val MONTAG_END: Long = 0L // 0 is default/reset code
    }

    object Modding {
        // Event codes are negative and from -300 to -5000
        const val EVENTCODE_START: Long = -5000L
        const val EVENTCODE_END: Long = -300L

        // Weapons
        const val WEAPON_START: Long = Vanilla.WEAPON_END + 1
        const val WEAPON_END: Long = 3999L

        // Armor
        const val ARMOR_START: Long = Vanilla.ARMOR_END + 1L
        const val ARMOR_END: Long = 1999L

        // Monsters
        const val MONSTER_START: Long = Vanilla.MONSTER_END + 1L // 5000, the lowest number #newmonster takes
        const val MONSTER_END: Long = 19999

        // Name Types
        const val NAMETYPE_START: Long = Vanilla.NAMETYPE_END + 1L // 170
        const val NAMETYPE_END: Long = 399L

        // Spells
        const val SPELL_START: Long = Vanilla.SPELL_END + 1
        const val SPELL_END: Long = 7999L

        // Enchantments
        const val ENCHANTMENT_START: Long = Vanilla.ENCHANTMENT_END + 1 // 200
        const val ENCHANTMENT_END: Long = 9999L

        // Items
        const val ITEM_START: Long = Vanilla.ITEM_END + 1
        const val ITEM_END: Long = 1999L

        // Sites
        const val SITE_START: Long = Vanilla.SITE_END + 1
        const val SITE_END: Long = 3999L

        // Nations
        const val NATION_START: Long = Vanilla.NATION_END + 1L
        const val NATION_END: Long = 499L

        // Population Types
        const val POPTYPE_START: Long = Vanilla.POPTYPE_END + 1L // 125
        const val POPTYPE_END: Long = 249L

        // MONTAG
        const val MONTAG_START: Long = 1000L
        const val MONTAG_END: Long = 100000L

        // Restricted items from 1 to 10000
        const val RESTRICTED_ITEM_START: Long = 1L
        const val RESTRICTED_ITEM_END: Long = 10000L
    }

    // Utility functions to check ranges
    object Validator {
        fun isVanillaId(type: EntityType, id: Long): Boolean = when (type) {
            EntityType.WEAPON -> id <= Vanilla.WEAPON_END
            EntityType.ARMOR -> id <= Vanilla.ARMOR_END
            EntityType.MONSTER -> id <= Vanilla.MONSTER_END
            EntityType.SPELL -> id <= Vanilla.SPELL_END
            EntityType.ITEM -> id <= Vanilla.ITEM_END
            EntityType.SITE -> id <= Vanilla.SITE_END
            EntityType.NATION -> id <= Vanilla.NATION_END
            EntityType.NAME_TYPE -> id <= Vanilla.NAMETYPE_END
            EntityType.ENCHANTMENT -> id <= Vanilla.ENCHANTMENT_END
            EntityType.POPTYPE -> id <= Vanilla.POPTYPE_END
            EntityType.EVENT_CODE -> id == Vanilla.EVENTCODE_END  // Only 0 is vanilla
            EntityType.MONTAG -> abs(id) < Modding.MONTAG_START
            EntityType.RESTRICTED_ITEM -> false  // No vanilla restricted items
            else -> false
        }

        fun isValidModdingId(type: EntityType, id: Long): Boolean = when (type) {
            EntityType.WEAPON -> id in Modding.WEAPON_START..Modding.WEAPON_END
            EntityType.ARMOR -> id in Modding.ARMOR_START..Modding.ARMOR_END
            EntityType.MONSTER -> id in Modding.MONSTER_START..Modding.MONSTER_END
            EntityType.SPELL -> id in Modding.SPELL_START..Modding.SPELL_END
            EntityType.ITEM -> id in Modding.ITEM_START..Modding.ITEM_END
            EntityType.SITE -> id in Modding.SITE_START..Modding.SITE_END
            EntityType.NATION -> id in Modding.NATION_START..Modding.NATION_END
            EntityType.NAME_TYPE -> id in Modding.NAMETYPE_START..Modding.NAMETYPE_END
            EntityType.ENCHANTMENT -> id in Modding.ENCHANTMENT_START..Modding.ENCHANTMENT_END
            EntityType.MONTAG -> id in Modding.MONTAG_START..Modding.MONTAG_END
            EntityType.EVENT_CODE -> id in Modding.EVENTCODE_START..Modding.EVENTCODE_END
            EntityType.POPTYPE -> id in Modding.POPTYPE_START..Modding.POPTYPE_END
            EntityType.RESTRICTED_ITEM -> id in Modding.RESTRICTED_ITEM_START..Modding.RESTRICTED_ITEM_END
            else -> false
        }
    }
}