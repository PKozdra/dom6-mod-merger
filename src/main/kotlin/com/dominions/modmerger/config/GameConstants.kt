// src/main/kotlin/com/dominions/modmerger/config/GameConstants.kt
package com.dominions.modmerger.config

object GameConstants {
    object SpellEffects {
        val SUMMONING_EFFECTS: Set<Long> = setOf(
            1, 21, 31, 37, 38, 43, 50, 54, 62, 89, 93, 119, 126, 130, 137,
            10001, 10021, 10031, 10037, 10038, 10043, 10050, 10054, 10062, 10089, 10093, 10119, 10126, 10130, 10137
        )

        val ENCHANTMENT_EFFECTS: Set<Long> = setOf(
            81, 10081, 10082, 10084, 10085, 10086
        )

        val KNOWN_SUMMON_SPELL_IDS: Set<Long> = setOf(
            721, 724, 733, 795, 805, 813, 818, 847, 875, 893, 900, 920, 1091
        )

        val KNOWN_SUMMON_SPELL_NAMES: Set<String> = setOf(
            "animate skeleton",
            "horde of skeletons",
            "raise skeletons",
            "reanimation",
            "pale riders",
            "revive lictor",
            "living mercury",
            "king of elemental earth",
            "summon fire elemental",
            "pack of wolves",
            "contact forest giant",
            "infernal disease",
            "hannya pact",
            "swarm",
            "creeping doom"
        ).map { it.lowercase() }.toSet()
    }
}