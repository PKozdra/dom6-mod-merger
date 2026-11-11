// src/main/kotlin/com/dominions/modmerger/gamedata/AbstractCsvGameDataProvider.kt
package com.dominions.modmerger.gamedata

import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.gamedata.csv.CsvData
import com.dominions.modmerger.gamedata.csv.CsvLoader
import com.dominions.modmerger.gamedata.model.MonsterData
import com.dominions.modmerger.gamedata.model.SpellData
import com.dominions.modmerger.gamedata.model.SpellEffectData
import com.dominions.modmerger.infrastructure.Logging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class AbstractCsvGameDataProvider(
) : GameDataProvider, Logging {

    // Spell Data
    protected val spellsCache = mutableMapOf<Long, SpellData>()
    protected val spellEffectsCache = mutableMapOf<Long, SpellEffectData>()
    protected val spellNameCache = mutableMapOf<String, SpellData>()
    // Monster Data
    protected val monsterCache = mutableMapOf<Long, MonsterData>()
    protected val monsterNameCache = mutableMapOf<String, MonsterData>()
    // lowercase name to id, for the types that are only looked up by name
    private val nameTables = mutableMapOf<EntityType, Map<String, Long>>()

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
            loadMonsterData()
            loadNameTable(EntityType.WEAPON, "weapons.csv")
            loadNameTable(EntityType.ARMOR, "armors.csv")
            loadNameTable(EntityType.ITEM, "BaseI.csv")
            loadNameTable(EntityType.SITE, "MagicSites.csv")
            loadNameTable(EntityType.NATION, "nations.csv")
            isInitialized = true
        }
    }

    private suspend fun loadEffectsData() {
        debug("Loading spell effects data from resources...")
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

            info("Loaded ${spellEffectsCache.size} spell effects.")
        } catch (e: Exception) {
            error("Failed to load spell effects data", e)
            throw GameDataLoadException("Failed to load spell effects data", e)
        }
    }

    private suspend fun loadSpellsData() {
        debug("Loading spells data from resources...")
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
                    // the game takes the last spell with a name, so a later row replaces an earlier one
                    spellNameCache[spell.name] = spell
                } catch (e: Exception) {
                    warn("Failed to parse spell row: $row")
                }
            }

            info("Loaded ${spellsCache.size} spells.")
        } catch (e: Exception) {
            error("Failed to load spells data", e)
            throw GameDataLoadException("Failed to load spells data", e)
        }
    }

    private suspend fun loadMonsterData() {
        debug("Loading monster data from resources...")
        try {
            val csvData = loadCsvResource("BaseU.csv")

            val idIndex = csvData.getColumnIndex("id")
            val nameIndex = csvData.getColumnIndex("name")

            csvData.rows.forEach { row ->
                try {
                    val monster = MonsterData(
                        id = row[idIndex].toLong(),
                        name = row[nameIndex].trim()
                    )
                    monsterCache[monster.id] = monster
                    // the game takes the first monster with a name, so the lowest id stays
                    monsterNameCache.merge(monster.name.lowercase(), monster) { a, b -> if (b.id < a.id) b else a }
                } catch (e: Exception) {
                    warn("Failed to parse monster row: $row")
                }
            }

            info("Loaded ${monsterCache.size} monsters.")
        } catch (e: Exception) {
            error("Failed to load monster data", e)
            throw GameDataLoadException("Failed to load monster data", e)
        }
    }

    // keeps the lowest id for a name, like the game does
    // a missing file only turns off name lookup for that type
    private suspend fun loadNameTable(type: EntityType, filename: String) {
        try {
            val csvData = loadCsvResource(filename)
            val idIndex = csvData.getColumnIndex("id")
            val nameIndex = csvData.getColumnIndex("name")
            val table = mutableMapOf<String, Long>()
            csvData.rows.forEach { row ->
                val id = row.getOrNull(idIndex)?.toLongOrNull() ?: return@forEach
                val name = row.getOrNull(nameIndex)?.trim()?.lowercase() ?: return@forEach
                if (name.isNotEmpty()) table.merge(name, id) { a, b -> minOf(a, b) }
            }
            nameTables[type] = table
            info("Loaded ${table.size} $type names.")
        } catch (e: Exception) {
            warn("No vanilla name table for $type ($filename): ${e.message}")
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

    override fun getMonster(id: Long): MonsterData? {
        check(isInitialized) { "GameDataProvider not initialized" }
        return monsterCache[id]
    }

    override fun getMonsters(ids: Set<Long>): Map<Long, MonsterData> {
        check(isInitialized) { "GameDataProvider not initialized" }
        return ids.mapNotNull { id ->
            monsterCache[id]?.let { id to it }
        }.toMap()
    }

    override fun getMonsterByName(name: String): MonsterData? {
        check(isInitialized) { "GameDataProvider not initialized" }
        return monsterNameCache[name.trim().lowercase()]
    }

    override fun getVanillaMonsterNames(): Map<Long, String> {
        check(isInitialized) { "GameDataProvider not initialized" }
        return monsterCache.mapValues { it.value.name }
    }

    override fun findVanillaIdByName(type: EntityType, name: String): Long? {
        check(isInitialized) { "GameDataProvider not initialized" }
        return when (type) {
            EntityType.MONSTER -> getMonsterByName(name)?.id
            EntityType.SPELL -> getSpellByName(name)?.id
            else -> nameTables[type]?.get(name.trim().lowercase())
        }
    }

    protected suspend fun ensureInitialized() {
        if (!isInitialized) {
            initialize()
        }
    }
}

class GameDataLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
