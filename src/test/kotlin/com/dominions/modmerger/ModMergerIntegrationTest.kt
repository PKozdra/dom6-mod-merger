package com.dominions.modmerger.test

import com.dominions.modmerger.core.ModMergerService
import com.dominions.modmerger.core.mapping.IdMapper
import com.dominions.modmerger.core.parsing.*
import com.dominions.modmerger.core.scanning.DefaultModScanner
import com.dominions.modmerger.core.scanning.ModScanner
import com.dominions.modmerger.core.writing.ModWriter
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.domain.ModFile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModMergerTest {
    private lateinit var parser: ModParser
    private lateinit var scanner: ModScanner
    private lateinit var mapper: IdMapper
    private lateinit var writer: ModWriter
    private lateinit var service: ModMergerService

    private lateinit var mod1: ModFile
    private lateinit var mod2: ModFile
    private lateinit var modDefinitions: Map<String, ModDefinition>

    @BeforeEach
    fun setup() {
        // Initialize core components
        val lineTypeDetector = LineTypeDetector()
        val spellBlockParser = SpellBlockParser()
        val entityParser = EntityParser()
        val eventParser = EventParser()
        parser = ModParser(spellBlockParser, entityParser, eventParser, lineTypeDetector)
        scanner = DefaultModScanner(parser)
        writer = ModWriter()

        // Create test mod files
        val mod1Content = """
            #modname "test_mod_1"
            #description "This mod enhances a game in mysterious ways..."
            #icon "./banner.png"
            #version 1.00
            
            #newmonster 5000
            #name "Test Monster"
            #end
            
            #newmonster 5001
            #name "Test Monster 2"
            #end
            
            #end
        """.trimIndent()

        val mod2Content = """
            #modname "test_mod_2"
            #description "This mod enhances a game in mysterious ways..."
            #icon "./banner.png"
            #version 1.00
            
            #newmonster 5000
            #name "Test Monster"
            #end
            
            #newmonster 5001
            #name "Test Monster 2"
            #end
            
            #end
        """.trimIndent()

        mod1 = ModFile.fromContent("test_mod_1", mod1Content)
        mod2 = ModFile.fromContent("test_mod_2", mod2Content)

        // Parse mods and create initial definitions
        val modDef1 = parser.parse(mod1)
        val modDef2 = parser.parse(mod2)
        modDefinitions = mapOf(
            mod1.name to modDef1,
            mod2.name to modDef2
        )

        // Initialize mapper with definitions
        mapper = IdMapper(modDefinitions)

        // Initialize service with all components
        service = ModMergerService(parser, scanner, mapper, writer)
    }

    @Test
    fun `test parsing mod files`() {
        // Parse first mod
        val modDef1 = parser.parse(mod1)
        assertEquals("test_mod_1", modDef1.name)
        assertTrue(modDef1.getDefinition(EntityType.MONSTER).definedIds.contains(5000))
        assertTrue(modDef1.getDefinition(EntityType.MONSTER).definedIds.contains(5001))

        // Parse second mod
        val modDef2 = parser.parse(mod2)
        assertEquals("test_mod_2", modDef2.name)
        assertTrue(modDef2.getDefinition(EntityType.MONSTER).definedIds.contains(5000))
        assertTrue(modDef2.getDefinition(EntityType.MONSTER).definedIds.contains(5001))
    }

    @Test
    fun `test conflict detection`() {
        val modDef1 = parser.parse(mod1)
        val modDef2 = parser.parse(mod2)

        val conflicts = modDef1.findConflicts(modDef2)
        assertEquals(1, conflicts.size)

        val conflict = conflicts.first()
        assertEquals(EntityType.MONSTER, conflict.type)
        assertTrue(conflict.conflictingIds.any { it.value == 5000L })
        assertTrue(conflict.conflictingIds.any { it.value == 5001L })
    }

    @Test
    fun `test id mapping for conflicting mods`() {
        val mappedDefinitions = mapper.createMappings(modDefinitions)

        // Check first mod retains original IDs
        val mappedMod1 = mappedDefinitions[mod1.name]
        assertNotNull(mappedMod1)
        assertEquals(5000L, mappedMod1.getMapping(EntityType.MONSTER, 5000L))
        assertEquals(5001L, mappedMod1.getMapping(EntityType.MONSTER, 5001L))

        // Check second mod gets remapped IDs
        val mappedMod2 = mappedDefinitions[mod2.name]
        assertNotNull(mappedMod2)
        val newId1 = mappedMod2.getMapping(EntityType.MONSTER, 5000L)
        val newId2 = mappedMod2.getMapping(EntityType.MONSTER, 5001L)

        assertNotNull(newId1)
        assertNotNull(newId2)
        assertNotEquals(5000L, newId1)
        assertNotEquals(5001L, newId2)
    }

    @Test
    fun `test invalid mod content handling`() {

        // Test case 2: Missing required end tag
        val missingEndContent = """
            #modname "invalid_mod"
            #newmonster 5000
            #name "No End Tag Monster"
        """.trimIndent()

        val missingEndMod = ModFile.fromContent("missing_end_mod", missingEndContent)
        assertThrows<ModParsingException> {
            parser.parse(missingEndMod)
        }

        // Test case 3: Invalid numeric range
        val invalidRangeContent = """
            #modname "invalid_mod"
            #newmonster 999999999999999
            #name "Out of Range Monster"
            #end
        """.trimIndent()

        val invalidRangeMod = ModFile.fromContent("invalid_range_mod", invalidRangeContent)
        assertThrows<ModParsingException> {
            parser.parse(invalidRangeMod)
        }

        // Test case 4: Malformed command
        val malformedContent = """
            #modname "invalid_mod"
            #newmon ster 5000
            #name "Malformed Command Monster"
            #end
        """.trimIndent()

        val malformedMod = ModFile.fromContent("malformed_mod", malformedContent)
        assertThrows<ModParsingException> {
            parser.parse(malformedMod)
        }
    }


    @Test
    fun `test empty mod handling`() {
        val emptyContent = """
            #modname "empty_mod"
            #end
        """.trimIndent()

        val emptyMod = ModFile.fromContent("empty_mod", emptyContent)
        val modDef = parser.parse(emptyMod)

        EntityType.entries.forEach { type ->
            assertTrue(modDef.getDefinition(type).definedIds.isEmpty())
        }
    }
}