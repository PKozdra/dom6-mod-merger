#modname "test_mod_2"
#description "This mod enchances a game in mysterious ways..."
#icon "./banner.png"
#version 1.00

#newmonster 1000
#name "Test Monster"
#end

#newmonster 1001
#name "Test Monster 2"
#end

#tempscalecap 9

#selectmonster 2845 --Ephor
#raredomsummon 2843
#domsummon20 -4149
#end

#selectmonster 2842 --Spectral Hoplite
#montag 4149
#end

#selectmonster 2841 --Spectral Peltast
#montag 4149
#end

#selectmonster 2840 --Spectral Archer
#montag 4149
#end

#selectmonster 2190 --Draug
#holy
#end

#selectmonster 2929 --Swan
#holy
#end


#selectspell 339
#fatiguecost 1000
#nreff 1010
#end

#selectspell 459
#fatiguecost 1600
#nreff 1009
#end

#selectmonster 3169 --Kuon Argyreos
#rpcost 0
--#heretic 0
--#popkill 0
--#incunrest 0
#startage -1
#older 0
#addrandomage 0
#addupkeep -1000
--#amphibian
#neednoteat
#holy
#end

#selectmonster 3170 --Kuon Khryseos
#rpcost 0
--#heretic 0
--#popkill 0
--#incunrest 0
#startage -1
#older 0
#addrandomage 0
#addupkeep -1000
--#amphibian
#neednoteat
#holy
#end

#newspell
#copyspell 1034 -- Akashic Knowledge
#name "Improved Akashic Knowledge"
#descr "The caster searches for gems of all types in the astral plane."
#pathlevel 0 1
#researchlevel 0
#fatiguecost 0
#restricted 6 --EA MEKONE
#end

#newsite 1755
#name "Cozycamp"
#path 8
#rarity 5
#blessatt 1
#blessdef 1
#blessstr 1
#blesshp 2
#blessmor 2
#blessmr 1
#blessreinvig 10
#blessprec 6
#blessfireres 5
#blesscoldres 5
#blessshockres 5
#blesspoisres 5
#blessdarkvis 100
--#blessatt 3
--#blessdef 3
--#blessstr 3
--#blesshp 6
--#blessmor 4
--#blessmr 3
--#blessreinvig 10
----#blessreinvig 999
--#blessprec 6
--#blessfireres 10
--#blesscoldres 10
--#blessshockres 10
--#blesspoisres 10
--#blessdarkvis 100
--#blessairshld 90
#end

--#newspell
--#copyspell "Royal Protection"
--#name "Heavenly Undead Protection"
--#descr "Protect all undead."
--#school -1
--#path 0 9
--#pathlevel 0 1
--#casttime 0
--#aoe 666
--#ainocast 1
--#restricted 6 --EA MEKONE
--#explspr -1
--#sound -1
--#end

--#newspell
--#copyspell "Doom"
--#name "Heavenly Curse of Doom"
--#descr "Doom everything."
--#school -1
--#path 0 9
--#pathlevel 0 1
--#casttime 0
--#aoe 666
--#ainocast 1
--#restricted 6 --EA MEKONE
--#explspr -1
--#sound -1
----#nextspell "Heavenly Undead Protection"
--#end

#newspell
#copyspell "Divine Blessing"
#name "Divine Blessing of Luck"
#descr "Bless everything."
--#spec 12599296
#school -1
#path 0 9
#pathlevel 0 1
#casttime 0
#aoe 666
#ainocast 1
#restricted 6 --EA MEKONE
#explspr -1
#sound -1
--#nextspell "Heavenly Curse of Doom"
#end

--#newspell
--#copyspell "Divine Blessing"
--#name "Divine Blessing of Everything"
--#descr "Bless everything."
--#spec 12599296
--#school 7
--#researchlevel 0
--#path 0 9
--#pathlevel 0 1
--#fatiguecost 0
--#casttime 0
--#aoe 666
----#ainocast 1
--#aispellmod -30
--#restricted 6 --EA MEKONE
--#explspr -1
--#sound -1
----#nextspell "Heavenly Curse of Doom"
--#end

--#newspell
--#copyspell 771 -- Resist Cold
--#name "Heavenly Curse of Luck"
--#descr "Curse everything with fateweaving malus."
--#effect 504
--#damage 255
--#spec 8650752
--#school -1
--#path 0 9
--#pathlevel 0 1
--#casttime 0
--#aoe 666
--#ainocast 1
--#restricted 6 --EA MEKONE
--#explspr -1
--#sound -1
----#nextspell "Omega-Blessing [Holy]"
--#nextspell "Divine Blessing of Luck"
--#end

#selectitem 991
#bestowtomount
#copyitem 363 -- Amulet of the Fish
#copyspr 363 -- Amulet of the Fish
#type 8
#name "Sacred Garland"
#descr "This garland marks the wearer out as allied to a Pretender and divested with some of their power. The bearer can call upon its power to assume the mantle of a DemiGod, gaining many of the powers and benefits of a Pretender God. If the bearer dies they can be called back by faithful priests, although the Garland itself will not return from the nether realms."
#spell "Gateway"
--#mr 4
--#prec 6
--#mapspeed 200
#constlevel 12
#magicboost 53 1
#magicboost 9 1
--#reinvigoration 10
--#fastcast 100
#allrange 4
--#bonusspells 2
#fixforgebonus 4
#addupkeep -10000
#pen 2
#researchbonus 6
--#gold 5
--#hp 12
#autospell "Divine Blessing of Luck"
--#autospell "Divine Blessing"
--#autospell "Omega-Blessing [Holy]"
#autospellrepeat 1
--#commaster
--#batstartsum1 "Circle Communicant"
--#hp 8
--#autospell "Twist Fate"
#autohealer 100
--#voidsanity 999
--#drainimmune
--#voidsanity 50
#combatcaster
#recuperation
#nofind
#cursed
#bless
#waterbreathing
#end

-- PLACEHOLDER_NATION|6 --EA MEKONE|

-- PLACEHOLDER_START

#selectmonster 3122 --King of Pallene
#clearmagic
#magicskill 0 4 -- Fire
#magicskill 3 5 -- Earth
#magicskill 9 4 -- Holy
#descr "Alcyonaeus is called the Greatest of the Gigantes, blessed by their progenitor with the Gift of Immortality. As long as he remains in the lands of the exalted he cannot die. When his brother Porphyrion founded Mekone, he got jealous and left for distant lands. Far away from the golden city he made himself king over mortal men and called his land Pallene. For centuries men suffered under his tyranny, but with the recent emergence of the God-slayer Alcyonaus has abandoned his kingdom and returned to his kin in Mekone to lead the Gigantes against the false gods of men.
Original magic path: E3, F2
[#tmpfiregems 5]
[#tmpearthgems 6]"
#tmpearthgems 6
#tmpfiregems 5
#rcost -500
#gcost 0
#rpcost 1
#addupkeep -2000
#itemslots 4138526
#ambidextrous 99
#enc 0
#startage -1
#older 0
#addrandomage 0
#startitem 991
#mor 30
#startheroab 100
#reformtime -2
#immortal
#ainorec
#noreqtemple
#noreqlab
#neednoteat
#end

#selectmonster 5815 --Apprentice of Titans
#clearmagic
#magicskill 0 5 -- Fire
#magicskill 1 3 -- Air
#magicskill 2 2 -- Water
#magicskill 3 5 -- Earth
#magicskill 9 2 -- Holy
#descr "Arges is an ancient Cyclops that once worked the forge under the tutelage of the Titan of the Forge. He forged many legendary weapons for his masters until the founding of Mekone and the beginning of the God War. Now Arges wishes to overthrow the Titans and take control of the Forge of the Ancients for himself. Arges is skilled in the creation of magical artifacts and can create wonders using less gems than normal.
Original magic path: A2, E3, F3, W1
[#tmpfiregems 6]
[#tmpairgems 4]
[#tmpwatergems 3]
[#tmpearthgems 6]"
#tmpearthgems 6
#tmpwatergems 3
#tmpairgems 4
#tmpfiregems 6
#rcost -500
#gcost 0
#rpcost 1
#addupkeep -2000
#itemslots 4138526
#ambidextrous 99
#enc 0
#startage -1
#older 0
#addrandomage 0
#startitem 991
#mor 30
#startheroab 100
#reformtime -2
#immortal
#ainorec
#noreqtemple
#noreqlab
#neednoteat
#end

#end