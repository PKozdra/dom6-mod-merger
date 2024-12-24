// src/main/kotlin/com/dominions/modmerger/domain/EntityDefinition.kt
package com.dominions.modmerger.domain

data class NameDefinitionInfo(
    val id: Long?,                // Can be null for pending assignments
    val lineNumber: Int,
    val implicitIndex: Int? = null // Track which implicit definition this name belongs to
)

data class EntityDefinition(
    val entityType: EntityType,
    private val _definedIds: MutableSet<Long> = mutableSetOf(),
    private val _definedNames: MutableMap<String, NameDefinitionInfo> = mutableMapOf(),
    private val _vanillaEditedIds: MutableSet<Long> = mutableSetOf(),
    private val _implicitIndexAssignments: MutableMap<Int, Long> = mutableMapOf<Int, Long>(),
    private var _implicitDefinitionCount: Int = 0
) {
    private var _cachedDefinedIds: Set<Long>? = null
    private var _cachedVanillaEditedIds: Set<Long>? = null
    private var _cachedNameToIdMap: Map<String, Long>? = null
    private var _cachedImplicitNameMap: Map<Int, Set<String>>? = null
    private var _isFrozen: Boolean = false

    val definedIds: Set<Long>
        get() = _cachedDefinedIds ?: _definedIds.sorted().toSet()

    val vanillaEditedIds: Set<Long>
        get() = _cachedVanillaEditedIds ?: _vanillaEditedIds.sorted().toSet()

    val definedNames: Map<String, NameDefinitionInfo>
        get() = _definedNames.toMap()

    fun getIdForName(name: String): Long? =
        if (_isFrozen) {
            _cachedNameToIdMap?.get(name)
        } else {
            _definedNames[name]?.id
        }

    fun addDefinedId(id: Long) {
        check(!_isFrozen) { "Cannot modify frozen EntityDefinition" }
        _definedIds.add(id)
    }

    fun addDefinedName(name: String, id: Long?, lineNumber: Int) {
        check(!_isFrozen) { "Cannot modify frozen EntityDefinition" }
        val implicitIndex = if (id == null) _implicitDefinitionCount - 1 else null
        _definedNames[name] = NameDefinitionInfo(
            id = id,
            lineNumber = lineNumber,
            implicitIndex = implicitIndex
        )
    }

    fun addVanillaEditedId(id: Long) {
        check(!_isFrozen) { "Cannot modify frozen EntityDefinition" }
        _vanillaEditedIds.add(id)
    }

    fun incrementImplicitDefinitions(): Int {
        check(!_isFrozen) { "Cannot modify frozen EntityDefinition" }
        return _implicitDefinitionCount++
    }

    fun getImplicitDefinitionCount(): Int = _implicitDefinitionCount

    fun updateNameToId(name: String, id: Long) {
        check(!_isFrozen) { "Cannot modify frozen EntityDefinition" }
        _definedNames[name]?.let { info ->
            _definedNames[name] = info.copy(id = id)
        }
    }

    fun setImplicitAssignedId(index: Int, id: Long) {
        check(!_isFrozen) { "Cannot modify frozen EntityDefinition" }
        _implicitIndexAssignments[index] = id
    }

    fun getAssignedIdForImplicitIndex(index: Int): Long? {
        return _implicitIndexAssignments[index]
    }

    fun getImplicitNameMappings(): Map<Int, Set<String>> =
        if (_isFrozen) {
            _cachedImplicitNameMap ?: emptyMap()
        } else {
            _definedNames.entries
                .filter { it.value.id == null && it.value.implicitIndex != null }
                .groupBy { it.value.implicitIndex!! }
                .mapValues { it.value.map { entry -> entry.key }.toSet() }
        }

    fun freeze() {
        if (!_isFrozen) {
            _cachedDefinedIds = _definedIds.sorted().toSet()
            _cachedVanillaEditedIds = _vanillaEditedIds.sorted().toSet()
            _cachedNameToIdMap = _definedNames.mapValues { it.value.id }.filterValues { it != null }
                .mapValues { it.value!! }
            _cachedImplicitNameMap = _definedNames.entries
                .groupBy { it.value.implicitIndex }
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { it.value.map { entry -> entry.key }.toSet() }
            _isFrozen = true
        }
    }

    fun cleanup() {
        _definedIds.clear()
        _definedNames.clear()
        _vanillaEditedIds.clear()
        _cachedDefinedIds = null
        _cachedVanillaEditedIds = null
        _cachedNameToIdMap = null
        _cachedImplicitNameMap = null
        _isFrozen = false
    }
}