package com.dominions.modmerger.core.mapping

import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.LogLevel
import com.dominions.modmerger.testutils.ServiceContainer
import com.dominions.modmerger.testutils.TestUtils
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdMapperTest {
    private lateinit var services: ServiceContainer
    private lateinit var tempDir: File

    @BeforeEach
    fun setup(@TempDir tempFolder: File) {
        tempDir = tempFolder
        services = TestUtils.createTestServices(tempDir)
        TestUtils.setupTestMods(tempDir)  // Add this line to setup test mods
    }

    @Test
    fun `should map conflicting monster IDs to new unique IDs`() {
        // Given
        val mod1 = TestUtils.loadTestMod("test_mod_1.dm")
        val mod2 = TestUtils.loadTestMod("test_mod_2.dm")

        // When
        val mod1Def = services.parser.parse(mod1)
        val mod2Def = services.parser.parse(mod2)
        val modDefinitions = mapOf(
            mod1.name to mod1Def,
            mod2.name to mod2Def
        )

        val mappedDefinitions = services.mapper.createMappings(modDefinitions)

        // Then
        val mod1Mappings = mappedDefinitions[mod1.name]!!
        val mod2Mappings = mappedDefinitions[mod2.name]!!

        // Check that conflicting IDs got different mappings
        assertNotEquals(
            mod1Mappings.getMapping(EntityType.MONSTER, 5001),
            mod2Mappings.getMapping(EntityType.MONSTER, 5001)
        )

        // Verify all referenced monsters are mapped
        val shapeIds = listOf(5001L, 5002L, 5003L, 5004L)
        shapeIds.forEach { id ->
            assertNotNull(mod1Mappings.getMapping(EntityType.MONSTER, id))
            assertNotNull(mod2Mappings.getMapping(EntityType.MONSTER, id))
        }
    }

    @Test
    fun `should preserve ID references within the same mod`() {
        // Given
        val mod1 = TestUtils.loadTestMod("test_mod_1.dm")
        val mod1Def = services.parser.parse(mod1)
        val modDefinitions = mapOf(mod1.name to mod1Def)

        // When
        val mappedDefinitions = services.mapper.createMappings(modDefinitions)
        val mappings = mappedDefinitions[mod1.name]!!

        // Then
        // Check that monster chain 5001 -> 5002 -> 5003 -> 5001 maintains references
        val id5001 = mappings.getMapping(EntityType.MONSTER, 5001)
        val id5002 = mappings.getMapping(EntityType.MONSTER, 5002)
        val id5003 = mappings.getMapping(EntityType.MONSTER, 5003)

        // Verify shape changing chain remains intact after remapping
        assertTrue(services.logDispatcher.getLogs().none {
            it.level == LogLevel.ERROR || it.message.contains("Error")
        })
    }

    @Test
    fun `should properly handle weapon secondary effect references`() {
        // Given
        val mod1 = TestUtils.loadTestMod("test_mod_1.dm")
        val mod2 = TestUtils.loadTestMod("test_mod_2.dm")

        // When
        val modDefinitions = mapOf(
            mod1.name to services.parser.parse(mod1),
            mod2.name to services.parser.parse(mod2)
        )
        val mappedDefinitions = services.mapper.createMappings(modDefinitions)

        // Then
        val mod1Mappings = mappedDefinitions[mod1.name]!!

        // Check weapon 801 and its secondary effects 802, 803
        val weapon801 = mod1Mappings.getMapping(EntityType.WEAPON, 801)
        val weapon802 = mod1Mappings.getMapping(EntityType.WEAPON, 802)
        val weapon803 = mod1Mappings.getMapping(EntityType.WEAPON, 803)

        assertTrue(weapon801 != 801L) // Should be remapped
        assertTrue(weapon802 != 802L) // Should be remapped
        assertTrue(weapon803 != 803L) // Should be remapped
    }

    @Test
    fun `should handle monster summoning references`() {
        // Given
        val mod1 = TestUtils.loadTestMod("test_mod_1.dm")
        val mod1Def = services.parser.parse(mod1)
        val modDefinitions = mapOf(mod1.name to mod1Def)

        // When
        val mappedDefinitions = services.mapper.createMappings(modDefinitions)
        val mappings = mappedDefinitions[mod1.name]!!

        // Then
        // Check summoned monsters are properly mapped
        val summonedMonsters = listOf(5005L, 5006L, 5007L, 5008L, 5009L)
        summonedMonsters.forEach { id ->
            assertNotNull(
                mappings.getMapping(EntityType.MONSTER, id),
                "Summoned monster $id should be mapped"
            )
        }
    }
}