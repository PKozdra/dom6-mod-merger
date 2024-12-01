#modname "Test Mod One"
#description "A test mod containing various entity definitions and cross-references"
#version 1.0
#domversion 6.0

-- Monsters with shape changing and references
#newmonster 5001
#name "Shape Shifter Alpha"
#descr "A creature that can change forms."
#hp 10
#mor 10
#foreignshape 5002
#homeshape 5003
#secondshape 5004
#end

#newmonster 5002
#name "Shape Shifter Beta"
#descr "Second form with references to other monsters."
#hp 15
#mor 15
#domsummon 5005
#domsummon2 5006
#makemonsters1 5007
#homeshape 5001
#end

#newmonster 5003
#name "Shape Shifter Gamma"
#descr "Third form with additional references."
#hp 20
#mor 20
#firstshape 5001
#batstartsum1 5008
#batstartsum2d6 5009
#end

#newmonster 5004
#name "Shape Shifter Delta"
#descr "Fourth form with montag reference."
#hp 25
#mor 25
#montag 510
#homeshape 5001
#end

#newmonster 5005
#name "Summoned Creature Alpha"
#hp 5
#mor 5
#end

#newmonster 5006
#name "Summoned Creature Beta"
#hp 6
#mor 6
#end

#newmonster 5007
#name "Summoned Creature Gamma"
#hp 7
#mor 7
#end

#newmonster 5008
#name "Battle Summon Alpha"
#hp 8
#mor 8
#end

#newmonster 5009
#name "Battle Summon Beta"
#hp 9
#mor 9
#end

-- Weapons with secondary effects
#newweapon 801
#name "Primary Weapon"
#dmg 10
#secondaryeffect 802
#secondaryeffectalways 803
#end

#newweapon 802
#name "Secondary Effect Weapon"
#dmg 5
#end

#newweapon 803
#name "Always Secondary Effect Weapon"
#dmg 3
#end

-- Armor with references
#newarmor 270
#name "Magical Armor"
#type 5
#prot 18
#def -2
#end

-- Spells with various effects and references
#newspell
#name "Complex Summoning"
#effect 1
#damage 5001
#nextspell 1450
#school 0
#researchlevel 5
#end

#newspell
#name "Enchantment Spell"
#effect 81
#damage 500
#school 4
#researchlevel 3
#end

-- Items with requirements
#newitem 501
#name "Magic Item Alpha"
#mainpath 0
#mainlevel 1
#constlevel 12
#weapon 801
#armor 270
#restricted 50
#req_targitem 502
#end

#newitem 502
#name "Magic Item Beta"
#mainpath 1
#mainlevel 2
#req_targnoitem 501
#end

-- Sites with connections
#newsite 1501
#name "Primary Site"
#path 0
#level 0
#rarity 5
#homemon 5001
#homecom 5002
#addsite 1502
#end

#newsite 1502
#name "Secondary Site"
#path 1
#level 1
#rarity 5
#mon 5003
#com 5004
#end

-- Nation definition with various references
#newnation
#name "Test Nation"
#epithet "Nation of Tests"
#era 2
#color 0.1 0.2 0.3
#startsite 1501
#startcom 5001
#startscout 5002
#startunittype1 5003
#startunittype2 5004
#merccost 10
#hero1 5005
#hero2 5006
#multihero1 5007
#mountcom 5008
#mountunit 5009
#forestrec 5001
#forestcom 5002
#mountainrec 5003
#mountaincom 5004
#landcom 5001
#landunit 5002
#uwcom 5003
#uwunit 5004
#end

-- Event code example
#newevent
#rarity 5
#code -301
#req_monster 5001
#req_targmnr 5002
#killmon 5003
#transform 5004
#end

#end



