// src/main/kotlin/com/dominions/modmerger/config/GameConstants.kt
package com.dominions.modmerger.constants

object GameConstants {
    const val GAME_ID = "2511500"
    const val MOD_FILE_EXTENSION = "dm"

    object SpellEffects {
        val SUMMONING_EFFECTS: Set<Long> = setOf(
            1, 21, 31, 37, 38, 43, 50, 54, 62, 89, 93, 119, 126, 130, 137,
            10001, 10021, 10031, 10037, 10038, 10043, 10050, 10054, 10062, 10089, 10093, 10119, 10126, 10130, 10137
        )

        val ENCHANTMENT_EFFECTS: Set<Long> = setOf(
            81, 10081, 10082, 10084, 10085, 10086
        )
    }
}