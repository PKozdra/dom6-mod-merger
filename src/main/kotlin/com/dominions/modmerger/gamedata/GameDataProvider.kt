// src/main/kotlin/com/dominions/modmerger/gamedata/GameDataProvider.kt
package com.dominions.modmerger.gamedata

import com.dominions.modmerger.gamedata.model.SpellData
import com.dominions.modmerger.gamedata.model.SpellEffectData
import com.dominions.modmerger.infrastructure.Logging

interface GameDataProvider : Logging {
    suspend fun initialize()

    // spell data
    fun getSpell(id: Long): SpellData?
    fun getSpells(ids: Set<Long>): Map<Long, SpellData>
    fun getSpellByName(name: String): SpellData?
    fun getSpellEffect(spellId: Long): SpellEffectData?
    fun getSpellEffectNumber(spellId: Long): Long?
}