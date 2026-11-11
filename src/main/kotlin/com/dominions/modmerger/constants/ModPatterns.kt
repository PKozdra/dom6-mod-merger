// src/main/kotlin/com/dominions/modmerger/constants/ModPatterns.kt
package com.dominions.modmerger.constants

// command lists match Dominions 6.37
// a USE_* list has the commands where the game reads a number of that type
// a *_REFERENCES list has the commands where the game reads a quoted name or a number
// commands that only read a name are in neither list, their names must not become numbers
// those are #startsite, #islandsite, item #spell and #autospell, merc #com and #unit
object ModPatterns {
    // mod info
    val MOD_NAME = Regex("""^[ \t]*#modname[ \t]+"([^"]+)".*$""")
    val MOD_DESCRIPTION_LINE = Regex("""^[ \t]*#description[ \t]+"[^"]*".*$""")  // closes on the same line
    val MOD_DESCRIPTION_START = Regex("""^[ \t]*#description.*$""")              // any description line
    val MOD_ICON_LINE = Regex("""^[ \t]*#icon[ \t]+.*$""")
    val MOD_VERSION_LINE = Regex("""^[ \t]*#version[ \t]+.*$""")
    val MOD_DOMVERSION_LINE = Regex("""^[ \t]*#domversion[ \t]+.*$""")

    // a keyword ends at the first character that is not a letter or digit, so "#end--x" ends a block
    val END = Regex("""^[ \t]*#end(?![A-Za-z0-9]).*$""")

    // #name inside any block
    val ENTITY_NAME = Regex("""^\s*#name\s*"([^"]+)".*$""")

    // spells
    val NEW_UNNUMBERED_SPELL = Regex("""^\s*#newspell(.*)$""")
    val USE_NUMBERED_SPELL = Regex("""^\s*#(?:copyspell|nextspell|selectspell|onebattlespell|3castbattlespell)\s+(\d+)(.*)$""")
    val SPELL_REFERENCES = Regex("""^\s*#(?:copyspell|nextspell|selectspell|onebattlespell|3castbattlespell)\s*"([^"]+)".*$""")
    // #selectspell "Fireball" starts a spell block like #selectspell 1200 does
    val SPELL_BLOCK_START = Regex("""^\s*#(?:newspell|selectspell\s+\d+|selectspell\s*"[^"]*")(.*)$""")
    val SPELL_EFFECT = Regex("""^\s*#effect\s+([-]?\d+)(.*)$""")
    val SPELL_DAMAGE = Regex("""^\s*#damage\s+([-]?\d+)(.*)$""")
    val SPELL_SELECT_ID = Regex("""^\s*#selectspell\s+(\d+)(.*)$""")
    val SPELL_SELECT_NAME = Regex("""^\s*#selectspell\s*"([^"]+)"(.*)$""")
    val SPELL_COPY_ID = Regex("""^\s*#copyspell\s+(\d+)(.*)$""")
    val SPELL_COPY_NAME = Regex("""^\s*#copyspell\s*"([^"]+)"(.*)$""")

    // weapons
    val NEW_NUMBERED_WEAPON = Regex("""^\s*#newweapon\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_WEAPON = Regex("""^\s*#newweapon(.*)$""")
    val SELECT_NUMBERED_WEAPON = Regex("""^\s*#selectweapon\s+(\d+)(.*)$""")
    val USE_NUMBERED_WEAPON = Regex("""^\s*#(?:weapon|newweapon|copyweapon|danceweapon|selectweapon|secondaryeffect|secondaryeffectalways)\s+(\d+)(.*)$""")
    val WEAPON_REFERENCES = Regex("""^\s*#(?:weapon|copyweapon|selectweapon|secondaryeffect|secondaryeffectalways)\s*"([^"]+)".*$""")

    // armor
    val NEW_NUMBERED_ARMOR = Regex("""^\s*#newarmor\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_ARMOR = Regex("""^\s*#newarmor(.*)$""")
    val SELECT_NUMBERED_ARMOR = Regex("""^\s*#selectarmor\s+(\d+)(.*)$""")
    val USE_NUMBERED_ARMOR = Regex("""^\s*#(?:armor|newarmor|copyarmor|selectarmor)\s+(\d+)(.*)$""")
    val ARMOR_REFERENCES = Regex("""^\s*#(?:armor|copyarmor|selectarmor)\s*"([^"]+)".*$""")

    // monsters
    // a negative monster number is a montag, EntityProcessor maps it as one
    val NEW_NUMBERED_MONSTER = Regex("""^\s*#newmonster\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_MONSTER = Regex("""^\s*#newmonster(.*)$""")
    val SELECT_NUMBERED_MONSTER = Regex("""^\s*#selectmonster\s+(\d+)(.*)$""")
    val USE_MONSTER = Regex(
        """^\s*#(?:com|mon|2com|4com|5com|lich|1unit|hero1|hero2|hero3|hero4|hero5|hero6|hero7|hero8|hero9|uwcom|uwrec|addgod|defcom|delgod|hero10|natcom|natmon|notmnr|seacom|searec|slaver|summon|cavecom|caverec|deepcom|deeprec|defcom1|defcom2|defmult|defunit|dripcom|driprec|farmcom|farmrec|homecom|homemon|kelpcom|kelprec|killcom|killmon|landcom|landrec|onlymnr|req_mnr|summon1|summon2|summon3|summon4|summon5|wallcom|1d3units|1d6units|2d3units|2d6units|3d3units|3d6units|4d3units|4d6units|5d6units|6d6units|7d6units|8d6units|9d6units|airboost|animated|assassin|bugshape|coastcom|coastrec|defunit1|defunit2|domshape|guardcom|mountmnr|plaincom|plainrec|sleepres|startcom|swampcom|swamprec|wallunit|wastecom|wasterec|10d6units|11d6units|12d6units|13d6units|14d6units|15d6units|16d6units|addreccom|coastcom1|coastcom2|copystats|damagemon|defunit1b|defunit1c|defunit1d|defunit2b|domsummon|farsumcom|fireboost|forestcom|forestrec|guardunit|holyboost|homeshape|landshape|req_mnrbs|req_nomnr|transform|twiceborn|uwdefcom1|uwdefcom2|uwwallcom|var0units|addrecunit|battlesum1|battlesum2|battlesum3|battlesum4|battlesum5|bloodboost|buguwshape|cheapgod20|cheapgod40|coastunit1|coastunit2|coastunit3|coridermnr|deathboost|domsummon2|earthboost|firstshape|kill2d6mon|multihero1|multihero2|multihero3|multihero4|multihero5|multihero6|multihero7|newmonster|ownsmonrec|plainshape|raiseshape|startscout|stealthcom|summonlvl2|summonlvl3|summonlvl4|uwdefunit1|uwdefunit2|uwwallunit|waterboost|watershape|worldshape|xpshapemon|astralboost|autumnshape|battleshape|cavefortcom|cavefortrec|domsummon20|dripfortcom|dripfortrec|farmfortcom|farmfortrec|forestshape|guardspirit|mountaincom|mountainrec|natureboost|notdomshape|req_deadmnr|req_monster|req_realmnr|req_targmnr|secondshape|shapechange|springshape|summershape|uwdefunit1b|uwdefunit1c|uwdefunit1d|uwdefunit2b|wintershape|assfollower1|assfollower2|assfollower3|batstartsum1|batstartsum2|batstartsum3|batstartsum4|batstartsum5|battlesum1d2|battlesum1d3|coastfortcom|coastfortrec|foreignshape|glamourboost|plainfortcom|plainfortrec|prophetshape|req_godismnr|swampfortcom|swampfortrec|wastefortcom|wastefortrec|addforeigncom|battlesumwarm|bugswarmshape|forestfortcom|forestfortrec|makemonsters1|makemonsters2|makemonsters3|makemonsters4|makemonsters5|monpresentrec|raredomsummon|req_2monsters|req_5monsters|req_monsterbs|req_nomonster|req_norealmnr|req_pretismnr|req_targnomnr|templetrainer|addforeignunit|assfollower1d3|batstartsum1d3|batstartsum1d6|batstartsum2d6|batstartsum3d6|batstartsum4d6|batstartsum5d6|batstartsum6d6|batstartsum7d6|batstartsum8d6|batstartsum9d6|forcetransform|foreignfortcom|foreignfortrec|foreignwallcom|secondtmpshape|startunittype1|startunittype2|startunittype3|bugswarmuwshape|foreignguardcom|foreignwallunit|mountainfortcom|mountainfortrec|req_godisnotmnr|req_targrealmnr|foreignguardunit|req_targnorealmnr)\s+([-]?\d+)(.*)$"""
    )
    val MONSTER_REFERENCES = Regex(
        """^\s*#(?:com|mon|2com|4com|5com|lich|1unit|uwcom|uwrec|addgod|defcom|delgod|natcom|natmon|notmnr|seacom|searec|slaver|summon|cavecom|caverec|deepcom|deeprec|defcom1|defcom2|defmult|defunit|dripcom|driprec|farmcom|farmrec|homecom|homemon|kelpcom|kelprec|killcom|killmon|landcom|landrec|onlymnr|req_mnr|summon1|summon2|summon3|summon4|summon5|wallcom|1d3units|1d6units|2d3units|2d6units|3d3units|3d6units|4d3units|4d6units|5d6units|6d6units|7d6units|8d6units|9d6units|airboost|animated|assassin|bugshape|coastcom|coastrec|defunit1|defunit2|domshape|guardcom|mountmnr|plaincom|plainrec|sleepres|startcom|swampcom|swamprec|wallunit|wastecom|wasterec|10d6units|11d6units|12d6units|13d6units|14d6units|15d6units|16d6units|addreccom|coastcom1|coastcom2|damagemon|defunit1b|defunit1c|defunit1d|defunit2b|domsummon|farsumcom|fireboost|forestcom|forestrec|guardunit|holyboost|homeshape|landshape|req_mnrbs|req_nomnr|transform|twiceborn|uwdefcom1|uwdefcom2|uwwallcom|var0units|addrecunit|battlesum1|battlesum2|battlesum3|battlesum4|battlesum5|bloodboost|buguwshape|cheapgod20|cheapgod40|coastunit1|coastunit2|coastunit3|coridermnr|deathboost|domsummon2|earthboost|firstshape|kill2d6mon|multihero1|multihero2|multihero3|multihero4|multihero5|multihero6|multihero7|ownsmonrec|plainshape|raiseshape|startscout|stealthcom|summonlvl2|summonlvl3|summonlvl4|uwdefunit1|uwdefunit2|uwwallunit|waterboost|watershape|worldshape|xpshapemon|astralboost|autumnshape|battleshape|cavefortcom|cavefortrec|domsummon20|dripfortcom|dripfortrec|farmfortcom|farmfortrec|forestshape|guardspirit|mountaincom|mountainrec|natureboost|notdomshape|req_deadmnr|req_monster|req_realmnr|req_targmnr|secondshape|shapechange|springshape|summershape|uwdefunit1b|uwdefunit1c|uwdefunit1d|uwdefunit2b|wintershape|assfollower1|assfollower2|assfollower3|batstartsum1|batstartsum2|batstartsum3|batstartsum4|batstartsum5|battlesum1d2|battlesum1d3|coastfortcom|coastfortrec|foreignshape|glamourboost|plainfortcom|plainfortrec|prophetshape|req_godismnr|swampfortcom|swampfortrec|wastefortcom|wastefortrec|addforeigncom|battlesumwarm|bugswarmshape|forestfortcom|forestfortrec|makemonsters1|makemonsters2|makemonsters3|makemonsters4|makemonsters5|monpresentrec|raredomsummon|req_2monsters|req_5monsters|req_monsterbs|req_nomonster|req_norealmnr|req_pretismnr|req_targnomnr|selectmonster|templetrainer|addforeignunit|assfollower1d3|batstartsum1d3|batstartsum1d6|batstartsum2d6|batstartsum3d6|batstartsum4d6|batstartsum5d6|batstartsum6d6|batstartsum7d6|batstartsum8d6|batstartsum9d6|forcetransform|foreignfortcom|foreignfortrec|foreignwallcom|secondtmpshape|startunittype1|startunittype2|startunittype3|bugswarmuwshape|foreignguardcom|foreignwallunit|mountainfortcom|mountainfortrec|req_godisnotmnr|req_targrealmnr|foreignguardunit|req_targnorealmnr)\s*"([^"]+)".*$"""
    )

    // items
    // the game ignores a number after #newitem and takes the next free slot, a few mods still write one
    val NEW_NUMBERED_ITEM = Regex("""^\s*#newitem\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_ITEM = Regex("""^\s*#newitem(.*)$""")
    val SELECT_NUMBERED_ITEM = Regex("""^\s*#selectitem\s+(\d+)(.*)$""")
    val USE_NUMBERED_ITEM = Regex("""^\s*#(?:copyitem|startitem|selectitem|req_targitem|req_worlditem|req_targnoitem|req_noworlditem)\s+(\d+)(.*)$""")
    val ITEM_REFERENCES = Regex("""^\s*#(?:copyitem|startitem|selectitem|req_targitem|req_worlditem|req_targnoitem|req_noworlditem)\s*"([^"]+)".*$""")

    // #copyspr copies from a monster in a monster block and from an item in an item block
    // so it is in neither list, EntityProcessor picks the type from the open block
    val USE_COPYSPR = Regex("""^\s*#copyspr\s+(\d+)(.*)$""")

    // sites
    val NEW_NUMBERED_SITE = Regex("""^\s*#newsite\s+(\d+)(.*)$""")
    val NEW_UNNUMBERED_SITE = Regex("""^\s*#newsite(.*)$""")
    val SELECT_NUMBERED_SITE = Regex("""^\s*#selectsite\s+(\d+)(.*)$""")
    val USE_NUMBERED_SITE = Regex("""^\s*#(?:addsite|godsite|newsite|copysite|futuresite|hiddensite|onlyatsite|removesite|selectsite|onlysitedst|maybeaddsite|req_nositenbr|maybehiddensite)\s+(\d+)(.*)$""")
    val SITE_REFERENCES = Regex("""^\s*#(?:godsite|copysite|futuresite|onlyatsite|selectsite|onlysitedst)\s*"([^"]+)".*$""")

    // nations
    // #newnation takes no number
    val NEW_UNNUMBERED_NATION = Regex("""^\s*#newnation(.*)$""")
    val SELECT_NUMBERED_NATION = Regex("""^\s*#selectnation\s+(\d+)(.*)$""")
    val USE_NUMBERED_NATION = Regex("""^\s*#(?:nat|nation|assowner|extramsg|req_nation|restricted|nationrebate|notfornation|req_domowner|req_nonation|req_targally|selectnation|req_fornation|req_fullowner|req_notnation|req_targowner|req_notforally|req_targnotally|req_notfornation|req_targnotowner)\s+(\d+)(.*)$""")
    val NATION_REFERENCES = Regex("""^\s*#(?:nat|nation|assowner|extramsg|req_nation|restricted|nationrebate|notfornation|req_domowner|req_nonation|req_targally|req_fornation|req_fullowner|req_notnation|req_targowner|req_notforally|req_targnotally|req_notfornation|req_targnotowner)\s*"([^"]+)".*$""")

    // name types
    val SELECT_NUMBERED_NAMETYPE = Regex("""^\s*#selectnametype\s+(\d+)(.*)$""")
    val USE_NAMETYPE = Regex("""^\s*#(?:nametype|selectnametype)\s+(\d+)(.*)$""")

    // montags
    val SELECT_NUMBERED_MONTAG = Regex("""^\s*#montag\s+(\d+)(.*)$""")
    val USE_NUMBERED_MONTAG = Regex("""^\s*#montag\s+(\d+)(.*)$""")

    // event codes
    // mod event codes are negative, so the patterns capture the minus sign with the number
    val SELECT_NUMBERED_EVENTCODE = Regex("""^\s*#(?:code|code2)\s+(-\d+)(.*)$""")
    val USE_NUMBERED_EVENTCODE = Regex("""^\s*#(?:code|code2|req_code|codedelay|resetcode|codedelay2|req_anycode|req_notcode|req_nearbycode|req_notanycode|resetcodedelay|req_nearowncode|resetcodedelay2)\s+(-\d+)(.*)$""")

    // restricted items
    val SELECT_NUMBERED_RESTRICTED_ITEM = Regex("""^\s*#restricteditem\s+(\d+)(.*)$""")
    val USE_NUMBERED_RESTRICTED_ITEM = Regex("""^\s*#(?:restricteditem|userestricteditem)\s+(\d+)(.*)$""")
    val RESTRICTED_ITEM_REFERENCES = Regex("""^\s*#(?:restricteditem|userestricteditem)\s*"([^"]+)".*$""")

    // pop types
    val SELECT_NUMBERED_POPTYPE = Regex("""^\s*#selectpoptype\s+(\d+)(.*)$""")
    val USE_NUMBERED_POPTYPE = Regex("""^\s*#(?:poptype|likespop|setpoptype|req_poptype|selectpoptype|req_notpoptype)\s+(\d+)(.*)$""")

    // enchantments
    val USE_GLOBAL_ENCHANTMENT = Regex("""^\s*#(?:req_ench|nationench|req_myench|req_noench|req_enchdom|assownerench|enchrebate10|enchrebate20|enchrebate50|enchrebate75|enchrebate100|enchrebate25p|enchrebate50p|req_enchnearby|req_enchtarget|req_hostileench|req_friendlyench)\s+(\d+)(.*)$""")

    // mercenaries
    // #com and #unit in a mercenary block only read a name, so they must stay names
    val NEWMERC_PATTERN = Regex("""^[ \t]*#newmerc.*$""")
    val MERC_UNIT_PATTERNS = Regex("""^[ \t]*#(com|unit)(?![A-Za-z0-9]).*$""")
}
