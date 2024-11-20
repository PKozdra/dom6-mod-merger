// src/main/kotlin/com/dominions/modmerger/utils/ModPatterns.kt
package com.dominions.modmerger.utils

object ModPatterns {
    // Core patterns
    val MOD_NAME = Regex("""#modname\s+"([^"]+)"""")
    val END = Regex("""#end""")

    // Spell patterns
    val SPELL_BLOCK_START = Regex("""#(newspell|selectspell)""")
    val SPELL_EFFECT = Regex("""#effect\s+([-]?\d+)(.*)$""")
    val SPELL_DAMAGE = Regex("""#damage\s+([-]?\d+)(.*)$""")
    val SPELL_COPY_ID = Regex("""#copyspell\s+(\d+)(.*)$""")
    val SPELL_COPY_NAME = Regex("""#copyspell\s+"([^"]+)"(.*)$""")
    val SPELL_SELECT_ID = Regex("""#selectspell\s+(\d+)(.*)$""")
    val SPELL_SELECT_NAME = Regex("""#selectspell\s+"([^"]+)"(.*)$""")

    // Entity patterns
    val NEW_NUMBERED_WEAPON = Regex("""#newweapon\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_WEAPON = Regex("""#newweapon(.*)$""")
    val NEW_NUMBERED_MONSTER = Regex("""#newmonster\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_MONSTER = Regex("""#newmonster(.*)$""")
    val NEW_NUMBERED_ARMOR = Regex("""#newarmor\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_ARMOR = Regex("""#newarmor(.*)$""")

    // Event codes
    val SELECT_NUMBERED_EVENTCODE = Regex("""#(?:code|code2)\s+-(\d+)(.*)$""")
    val USE_NUMBERED_EVENTCODE = Regex("""#(?:code|code2|resetcode|req_code|req_anycode|req_notanycode|req_nearbycode|req_nearowncode|codedelay|codedelay2|resetcodedelay|resetcodedelay2)\s+-(\d+)(.*)$""")

    val SELECT_NUMBERED_POPTYPE = Regex("""#poptype\s+(\d+)(.*)$""")
    val SELECT_NUMBERED_RESTRICTED_ITEM = Regex("""#restricteditem\s+(\d+)(.*)$""")
}
