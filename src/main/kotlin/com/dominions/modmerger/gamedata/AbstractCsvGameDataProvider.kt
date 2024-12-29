// src/main/kotlin/com/dominions/modmerger/gamedata/AbstractCsvGameDataProvider.kt
package com.dominions.modmerger.gamedata

import com.dominions.modmerger.gamedata.csv.CsvData
import com.dominions.modmerger.gamedata.csv.CsvLoader
import com.dominions.modmerger.gamedata.model.SpellData
import com.dominions.modmerger.gamedata.model.SpellEffectData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class AbstractCsvGameDataProvider(
) : GameDataProvider {

    protected val spellsCache = mutableMapOf<Long, SpellData>()
    protected val spellEffectsCache = mutableMapOf<Long, SpellEffectData>()
    // Using case-insensitive map for name lookups
    protected val spellNameCache = mutableMapOf<String, SpellData>()

    private val initializationMutex = Mutex()
    private var isInitialized = false
    private val csvLoader = CsvLoader()

    // Subclasses must implement this
    protected abstract fun getResourcePath(): String

    override suspend fun initialize() {
        initializationMutex.withLock {
            if (isInitialized) return
            loadEffectsData()
            loadSpellsData()
            isInitialized = true
        }
    }

    private suspend fun loadEffectsData() {
        debug("Loading spell effects data from resources")
        try {
            val csvData = loadCsvResource("effects_spells.csv")

            val recordIdIndex = csvData.getColumnIndex("record_id")
            val effectNumberIndex = csvData.getColumnIndex("effect_number")

            csvData.rows.forEach { row ->
                try {
                    val effect = SpellEffectData(
                        recordId = row[recordIdIndex].toLong(),
                        effectNumber = row[effectNumberIndex].toLong()
                    )
                    spellEffectsCache[effect.recordId] = effect
                } catch (e: Exception) {
                    warn("Failed to parse spell effect row: $row")
                }
            }

            info("Loaded ${spellEffectsCache.size} spell effects")
        } catch (e: Exception) {
            error("Failed to load spell effects data", e)
            throw GameDataLoadException("Failed to load spell effects data", e)
        }
    }

    private suspend fun loadSpellsData() {
        debug("Loading spells data from resources")
        try {
            val csvData = loadCsvResource("spells.csv")

            val idIndex = csvData.getColumnIndex("id")
            val nameIndex = csvData.getColumnIndex("name")
            val effectIndex = csvData.getColumnIndex("effect_record_id")

            csvData.rows.forEach { row ->
                try {
                    val spell = SpellData(
                        id = row[idIndex].toLong(),
                        name = row[nameIndex].trim().lowercase(), // Store names in lowercase
                        effectId = row[effectIndex].toLong()
                    )
                    spellsCache[spell.id] = spell
                    spellNameCache[spell.name] = spell
                } catch (e: Exception) {
                    warn("Failed to parse spell row: $row")
                }
            }

            info("Loaded ${spellsCache.size} spells")
        } catch (e: Exception) {
            error("Failed to load spells data", e)
            throw GameDataLoadException("Failed to load spells data", e)
        }
    }

    protected suspend fun loadCsvResource(filename: String): CsvData {
        val path = "${getResourcePath()}/$filename"
        val inputStream = javaClass.classLoader.getResourceAsStream(path)
            ?: throw GameDataLoadException("Could not find $filename in resources")
        val content = inputStream.bufferedReader().use { it.readText() }
        return csvLoader.loadCsvFromString(content)
    }

    override fun getSpell(id: Long): SpellData? {
        check(isInitialized) { "GameDataProvider not initialized" }
        return spellsCache[id]
    }

    override fun getSpells(ids: Set<Long>): Map<Long, SpellData> {
        check(isInitialized) { "GameDataProvider not initialized" }
        return ids.mapNotNull { id ->
            spellsCache[id]?.let { id to it }
        }.toMap()
    }

    override fun getSpellByName(name: String): SpellData? {
        check(isInitialized) { "GameDataProvider not initialized" }
        return spellNameCache[name.trim().lowercase()]
    }

    override fun getSpellEffect(spellId: Long): SpellEffectData? {
        check(isInitialized) { "GameDataProvider not initialized" }
        val spell = spellsCache[spellId] ?: return null
        return spellEffectsCache[spell.effectId]
    }

    override fun getSpellEffectNumber(spellId: Long): Long? {
        check(isInitialized) { "GameDataProvider not initialized" }
        return getSpellEffect(spellId)?.effectNumber
    }

    protected suspend fun ensureInitialized() {
        if (!isInitialized) {
            initialize()
        }
    }
}

class GameDataLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)