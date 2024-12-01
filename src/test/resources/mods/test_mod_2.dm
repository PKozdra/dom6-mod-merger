#modname "Test Mod Two"
#description "Second test mod with conflicting IDs and cross-references"
#version 1.0
#domversion 6.0

-- Conflicting monster IDs with shape changes
#newmonster 5001
#name "Rival Shape Shifter Alpha"
#descr "A different creature with the same ID."
#hp 30
#mor 30
#foreignshape 5002
#homeshape 5003
#secondshape 5004
#end

#newmonster 5002
#name "Rival Shape Shifter Beta"
#descr "Another form with different references."
#hp 35
#mor 35
#domsummon 5005
#domsummon2 5006
#makemonsters1 5007
#homeshape 5001
#end

#newmonster 5003
#name "Rival Shape Shifter Gamma"
#hp 40
#mor 40
#firstshape 5001
#batstartsum1 5008
#batstartsum2d6 5009
#end

-- Conflicting weapon IDs
#newweapon 801
#name "Rival Primary Weapon"
#dmg 15
#secondaryeffect 802
#end

#newweapon 802
#name "Rival Secondary Effect Weapon"
#dmg 8
#end

-- Conflicting armor ID
#newarmor 270
#name "Rival Magical Armor"
#type 5
#prot 20
#def -1
#end

-- Spells referencing conflicting monsters
#newspell
#name "Rival Summoning"
#effect 1
#damage 5001
#nextspell 1451
#school 1
#researchlevel 5
#end

-- Items with conflicting IDs
#newitem 501
#name "Rival Magic Item Alpha"
#mainpath 2
#mainlevel 3
#weapon 801
#armor 270
#restricted 51
#req_targitem 502
#end

#newitem 502
#name "Rival Magic Item Beta"
#mainpath 3
#mainlevel 4
#req_targnoitem 501
#end

-- Sites with conflicting IDs
#newsite 1501
#name "Rival Primary Site"
#path 2
#level 2
#rarity 5
#homemon 5001
#homecom 5002
#addsite 1502
#end

#newsite 1502
#name "Rival Secondary Site"
#path 3
#level 3
#rarity 5
#mon 5003
#com 5004
#end

-- Nation with references to conflicting IDs
#newnation
#name "Rival Test Nation"
#epithet "Nation of Rival Tests"
#era 2
#color 0.4 0.5 0.6
#startsite 1501
#startcom 5001
#startscout 5002
#startunittype1 5003
#startunittype2 5004
#merccost 15
#hero1 5005
#hero2 5006
#end

-- Event with conflicting code
#newevent
#rarity 5
#code -301
#req_monster 5001
#req_targmnr 5002
#killmon 5003
#transform 5004
#end