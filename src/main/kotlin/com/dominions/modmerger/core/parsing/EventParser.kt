// src/main/kotlin/com/dominions/modmerger/core/parsing/EventParser.kt
package com.dominions.modmerger.core.parsing

import com.dominions.modmerger.constants.ModPatterns
import com.dominions.modmerger.constants.ModRanges
import com.dominions.modmerger.domain.EntityType
import com.dominions.modmerger.domain.ModDefinition
import com.dominions.modmerger.utils.ModUtils
import mu.KLogger
import mu.KotlinLogging


class EventParser {
    private val logger: KLogger = KotlinLogging.logger {}

    fun handleEventCode(line: String, definition: ModDefinition) {
        ModUtils.extractId(line, ModPatterns.USE_NUMBERED_EVENTCODE)?.let { id ->
            // Handle usage of event codes
            if (id >= ModRanges.Modding.EVENTCODE_START) {
                definition.addDefinedId(EntityType.EVENT_CODE, id)
            } else {
                definition.addVanillaEditedId(EntityType.EVENT_CODE, id)
            }
        }
    }
}