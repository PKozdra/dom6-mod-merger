// src/main/kotlin/com/dominions/modmerger/utils/ModPatterns.kt
package com.dominions.modmerger.constants

object ModPatterns {
    // Core patterns with precise whitespace handling
    val MOD_NAME = Regex("""^[ \t]*#modname[ \t]+"([^"]+)".*$""")
    val END = Regex("""^[ \t]*#end""")

    // Mod info patterns with precise whitespace handling
    val MOD_DESCRIPTION_LINE = Regex("""^[ \t]*#description[ \t]+"[^"]*".*$""")  // Complete description with quotes
    val MOD_DESCRIPTION_START = Regex("""^[ \t]*#description.*$""")              // Any description line
    val MOD_ICON_LINE = Regex("""^[ \t]*#icon[ \t]+.*$""")
    val MOD_VERSION_LINE = Regex("""^[ \t]*#version[ \t]+.*$""")
    val MOD_DOMVERSION_LINE = Regex("""^[ \t]*#domversion[ \t]+.*$""")

    val USE_NUMBERED_SPELL = Regex(
        """#(?:selectspell|copyspell|nextspell)\s+(\d+)(.*)$"""
    )

    // Spell patterns
    val SPELL_BLOCK_START = Regex("""#(newspell|selectspell)""")
    val SPELL_EFFECT = Regex("""#effect\s+([-]?\d+)(.*)$""")
    val SPELL_DAMAGE = Regex("""#damage\s+([-]?\d+)(.*)$""")
    val SPELL_COPY_ID = Regex("""#copyspell\s+(\d+)(.*)$""")
    val SPELL_SELECT_ID = Regex("""#selectspell\s+(\d+)(.*)$""")

    // Fixed version with named group
    val SPELL_COPY_NAME = Regex("""#copyspell\s+"(?<name>[^"]+)"(.*)$""")

    // Similarly, ensure SPELL_SELECT_NAME has the named group
    val SPELL_SELECT_NAME = Regex("""#selectspell\s+"(?<name>[^"]+)"(.*)$""")

    // Weapons
    val NEW_NUMBERED_WEAPON = Regex("""#newweapon\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_WEAPON = Regex("""#newweapon(.*)$""")
    val SELECT_NUMBERED_WEAPON = Regex("""#selectweapon\s+(\d+)(.*)$""")
    val USE_NUMBERED_WEAPON =
        Regex("""#(?:newweapon|weapon|copyweapon|secondaryeffect|secondaryeffectalways|selectweapon)\s+(\d+)(.*)$""")

    // Armor
    val NEW_NUMBERED_ARMOR = Regex("""#newarmor\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_ARMOR = Regex("""#newarmor(.*)$""")
    val NEW_NAMED_ARMOR = Regex("""#newarmor\s+"([^"]+)"(.*)$""")
    val SELECT_NUMBERED_ARMOR = Regex("""#selectarmor\s+(\d+)(.*)$""")
    val USE_NUMBERED_ARMOR = Regex("""#(?:newarmor|armor|copyarmor)\s+(\d+)(.*)$""")

    // Monsters
    val NEW_NUMBERED_MONSTER = Regex("""#newmonster\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_MONSTER = Regex("""#newmonster(.*)$""")
    val SELECT_NUMBERED_MONSTER = Regex("""#selectmonster\s+(\d+)(.*)$""")
    val USE_MONSTER = Regex(
        """#(?:newmonster|copyspr|monpresentrec|ownsmonrec|raiseshape|shapechange|prophetshape|firstshape|secondshape|secondtmpshape|forestshape|plainshape|foreignshape|homeshape|springshape|summershape|autumnshape|wintershape|landshape|watershape|twiceborn|domsummon|domsummon2|domsummon20|raredomsummon|templetrainer|makemonsters1|makemonsters2|makemonsters3|makemonsters4|makemonsters5|summon1|summon2|summon3|summon4|summon5|battlesum1|battlesum2|battlesum3|battlesum4|battlesum5|batstartsum1|batstartsum2|batstartsum3|batstartsum4|batstartsum5|batstartsum1d3|batstartsum1d6|batstartsum2d6|batstartsum3d6|batstartsum4d6|batstartsum5d6|batstartsum6d6|batstartsum7d6|batstartsum8d6|batstartsum9d6|farsumcom|onlymnr|homemon|homecom|mon|com|summon|summonlvl2|summonlvl3|summonlvl4|startcom|coastcom1|coastcom2|addforeignunit|addforeigncom|forestrec|mountainrec|swamprec|wasterec|caverec|startscout|forestcom|mountaincom|swampcom|wastecom|cavecom|startunittype1|startunittype2|addrecunit|addreccom|uwrec|uwcom|coastunit1|coastunit2|coastunit3|landrec|landcom|hero1|hero2|hero3|hero4|hero5|hero6|hero7|hero8|hero9|hero10|multihero1|multihero2|multihero3|multihero4|multihero5|multihero6|multihero7|defcom1|defcom2|defunit1|defunit1b|defunit1c|defunit1d|defunit2|defunit2b|addgod|delgod|cheapgod20|cheapgod40|guardspirit|transform|fireboost|airboost|waterboost|earthboost|astralboost|deathboost|natureboost|bloodboost|holyboost|req_monster|req_2monsters|req_5monsters|req_nomonster|req_mnr|req_nomnr|req_deadmnr|req_targmnr|req_targnomnr|assassin|stealthcom|2com|4com|5com|1unit|1d3units|2d3units|3d3units|4d3units|1d6units|2d6units|3d6units|4d6units|5d6units|6d7units|7d6units|8d6units|9d6units|10d6units|11d6units|12d6units|13d6units|14d6units|15d6units|16d6units|killmon|kill2d6mon|killcom|copystats|xpshapemon|coridermnr|mountmnr|lich|battleshape|worldshape|animated|domshape|notdomshape|slaver|natmon|natcom|wallcom|wallunit|uwwallunit|uwwallcom|defcom|defunit|farmrec|driprec|coastrec|searec|deeprec|kelprec|forestfortrec|mountainfortrec|swampfortrec|wastefortrec|farmfortrec|cavefortrec|dripfortrec|coastfortrec|seafortrec|deepfortrec|kelpfortrec|farmcom|dripcom|coastcom|seacom|deepcom|kelpcom|forestfortcom|mountainfortcom|swampfortcom|wastefortcom|farmfortcom|cavefortcom|dripfortcom|coastfortcom|seafortcom|deepfortcom|kelpfortcom|req_godismnr|req_godisnotmnr|guardcom|guardunit|notmnr|uwdefcom1|uwdefcom2|uwdefunit1|uwdefunit1b|uwdefunit1c|uwdefunit1d|uwdefunit2|uwdefunit2b)\s+([-]?\d+)(.*)$"""
    )

    // Items
    val NEW_NUMBERED_ITEM = Regex("""#newitem\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_ITEM = Regex("""#newitem(.*)$""")
    val SELECT_NUMBERED_ITEM = Regex("""#selectitem\s+(\d+)(.*)$""")
    val USE_NUMBERED_ITEM =
        Regex("""#(?:selectitem|startitem|copyitem|copyspr|req_targitem|req_targnoitem|req_worlditem|req_noworlditem)\s+(\d+)(.*)$""")

    // Sites
    val NEW_NUMBERED_SITE = Regex("""#newsite\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_SITE = Regex("""#newsite(.*)$""")
    val SELECT_NUMBERED_SITE = Regex("""#selectsite\s+(\d+)(.*)$""")
    val USE_NUMBERED_SITE =
        Regex("""#(?:selectsite|newsite|godsite|req_nositenbr|startsite|addsite|removesite|hiddensite|futuresite|islandsite|onlyatsite|onlysitedst)\s+(\d+)(.*)$""")

    // Nations
    val NEW_UNNUMBERED_NATION = Regex("""#newnation(.*)$""")
    val SELECT_NUMBERED_NATION = Regex("""#selectnation\s+(\d+)(.*)$""")
    val USE_NUMBERED_NATION =
        Regex("""#(?:selectnation|nation|restricted|notfornation|nationrebate|req_nation|req_nonation|req_fornation|req_notfornation|req_notnation|req_notforally|req_fullowner|req_domowner|req_targowner|assowner|extramsg|nat|req_targnotowner)\s+(\d+)(.*)$""")

    // Name types
    val SELECT_NUMBERED_NAMETYPE = Regex("""#selectnametype\s+(\d+)(.*)$""")
    val USE_NAMETYPE = Regex("""#(?:nametype|selectnametype)\s+(\d+)(.*)$""")

    // Montags
    val SELECT_NUMBERED_MONTAG = Regex("""#montag\s+(\d+)(.*)$""")
    val USE_NUMBERED_MONTAG = Regex("""#montag\s+(\d+)(.*)$""")

    // Event codes
    val SELECT_NUMBERED_EVENTCODE = Regex("""#(?:code|code2)\s+-(\d+)(.*)$""")
    val USE_NUMBERED_EVENTCODE =
        Regex("""#(?:code|code2|resetcode|req_code|req_anycode|req_notanycode|req_nearbycode|req_nearowncode|codedelay|codedelay2|resetcodedelay|resetcodedelay2)\s+-(\d+)(.*)$""")

    // Restricted items
    val SELECT_NUMBERED_RESTRICTED_ITEM = Regex("""#restricteditem\s+(\d+)(.*)$""")
    val USE_NUMBERED_RESTRICTED_ITEM = Regex("""#(?:restricteditem|userestricteditem)\s+(\d+)(.*)$""")

    // Pop types
    val SELECT_NUMBERED_POPTYPE = Regex("""#poptype\s+(\d+)(.*)$""")

    // Enchantments
    val USE_GLOBAL_ENCHANTMENT =
        Regex("""#(?:enchrebate50|enchrebate20|enchrebate10|req_noench|req_ench|req_myench|req_friendlyench|req_hostileench|req_enchdom|nationench|enchrebate25p|enchrebate50p)\s+(\d+)(.*)$""")
    val USE_GLOBAL_ENCHANTMENT_DAMAGE = Regex("""#damage\s+(\d+)(.*)$""")
}