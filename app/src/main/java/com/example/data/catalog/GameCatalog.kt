package com.example.data.catalog

import com.example.data.model.AppLanguage
import com.example.data.model.BranchingNarrativeEvent
import com.example.data.model.CommunityMod
import com.example.data.model.EnemyBlueprint
import com.example.data.model.GeneratedStage
import com.example.data.model.HeroArchetype
import com.example.data.model.NarrativeChoice
import com.example.data.model.SkillDefinition
import com.example.data.model.StageObstacle

object GameCatalog {

    val starterWeapons: List<CommunityMod> = listOf(
        CommunityMod(
            id = "starter_bloodblade",
            authorUid = "system",
            authorName = "Sanctum",
            modType = "WEAPON",
            name = "Sanguine Claymore",
            loreEn = "A jagged cathedral greatsword that drinks the warmth of fallen foes.",
            loreId = "Pedang besar katedral bergerigi yang meminum kehangatan musuh yang tumbang.",
            effectTag = "VAMPIRIC",
            powerValue = 28L,
            speedValue = 100L,
            accentHex = "#9E2A3B",
            isPublished = true
        ),
        CommunityMod(
            id = "starter_soul_lantern",
            authorUid = "system",
            authorName = "Sanctum",
            modType = "WEAPON",
            name = "Wraithbone Censer",
            loreEn = "Swings arcs of spectral teal fire that sear cursed flesh from afar.",
            loreId = "Mengayunkan api jiwa berwarna biru pucat yang membakar daging terkutuk dari jauh.",
            effectTag = "SOULBURN",
            powerValue = 24L,
            speedValue = 120L,
            accentHex = "#4EA8A8",
            isPublished = true
        ),
        CommunityMod(
            id = "starter_night_fangs",
            authorUid = "system",
            authorName = "Sanctum",
            modType = "WEAPON",
            name = "Obsidian Twin-Fangs",
            loreEn = "Twin shadow daggers striking with lethal speed and void criticals.",
            loreId = "Belati kembar obsidian yang menebas secepat bayangan dengan serangan kritis kehampaan.",
            effectTag = "VOID_CRIT",
            powerValue = 22L,
            speedValue = 145L,
            accentHex = "#D4A359",
            isPublished = true
        ),
        CommunityMod(
            id = "starter_abyssal_scythe",
            authorUid = "system",
            authorName = "Sanctum",
            modType = "WEAPON",
            name = "Abyssal Crescent Scythe",
            loreEn = "Sweeps a massive 160-degree reaping arc that pulls Soul Orbs and chills prey.",
            loreId = "Menebas lengkungan sabit luas 160 derajat yang menarik Orb Jiwa dan memperlambat musuh.",
            effectTag = "REAPING_CLEAVE",
            powerValue = 32L,
            speedValue = 92L,
            accentHex = "#B368E8",
            isPublished = true
        ),
        CommunityMod(
            id = "starter_bell_hammer",
            authorUid = "system",
            authorName = "Sanctum",
            modType = "WEAPON",
            name = "Cathedral Bell-Hammer",
            loreEn = "Crushes stone and bone with seismic golden shockwaves that briefly stun enemies.",
            loreId = "Menghancurkan batu dan tulang dengan gelombang kejut emas yang memingsankan musuh.",
            effectTag = "THUNDER_SMITE",
            powerValue = 38L,
            speedValue = 82L,
            accentHex = "#F0C674",
            isPublished = true
        )
    )

    val starterRelics: List<CommunityMod> = listOf(
        CommunityMod(
            id = "starter_ash_signet",
            authorUid = "system",
            authorName = "Sanctum",
            modType = "RELIC",
            name = "Signet of Cinders",
            loreEn = "Dashing releases an ember shockwave that scorches pursuing horrors.",
            loreId = "Menghindar melepaskan gelombang bara yang membakar monster pengejar.",
            effectTag = "DASH_NOVA",
            powerValue = 20L,
            speedValue = 110L,
            accentHex = "#D4A359",
            isPublished = true
        ),
        CommunityMod(
            id = "starter_frost_chalice",
            authorUid = "system",
            authorName = "Sanctum",
            modType = "RELIC",
            name = "Chalice of Hoarfrost",
            loreEn = "Chills nearby abyssal creatures and fortifies your vitality each floor.",
            loreId = "Membekukan makhluk jurang di sekitar dan memperkuat daya tahan di setiap lantai.",
            effectTag = "FROSTBITE",
            powerValue = 25L,
            speedValue = 100L,
            accentHex = "#4EA8A8",
            isPublished = true
        ),
        CommunityMod(
            id = "starter_shadow_veil",
            authorUid = "system",
            authorName = "Sanctum",
            modType = "RELIC",
            name = "Veil of Wraiths",
            loreEn = "Extends dash invulnerability and grants +12% movement swiftness.",
            loreId = "Memperpanjang kekebalan saat menghindar dan menambah +12% kecepatan gerak.",
            effectTag = "SHADOW_HASTE",
            powerValue = 22L,
            speedValue = 112L,
            accentHex = "#B368E8",
            isPublished = true
        )
    )

    val availableSkill1: List<SkillDefinition> = listOf(
        SkillDefinition(
            id = "skill1_crimson_cleave",
            slot = 1,
            nameId = "Tebasan Darah",
            nameEn = "Crimson Cleave",
            descId = "Gelombang tebasan merah 360° yang menyerap sedikit HP.",
            descEn = "360° crimson shockwave slash that restores a sliver of HP.",
            accentHex = "#D94356",
            cooldownSec = 3.8f
        ),
        SkillDefinition(
            id = "skill1_soul_barrage",
            slot = 1,
            nameId = "Rentetan Roh",
            nameEn = "Soul Barrage",
            descId = "Menembakkan 7 bola api roh ke arah musuh terdekat.",
            descEn = "Fires a 7-bolt fan of spectral bolts toward nearby foes.",
            accentHex = "#4EA8A8",
            cooldownSec = 4.0f
        ),
        SkillDefinition(
            id = "skill1_phantom_ring",
            slot = 1,
            nameId = "Cincin Belati",
            nameEn = "Phantom Ring",
            descId = "Melepas 10 belati bayangan melingkar + lonjakan kecepatan singkat.",
            descEn = "Unleashes a 10-dagger ring burst + brief shadow speed surge.",
            accentHex = "#D4A359",
            cooldownSec = 3.5f
        )
    )

    val availableSkill2: List<SkillDefinition> = listOf(
        SkillDefinition(
            id = "skill2_judgement",
            slot = 2,
            nameId = "Penghakiman Suci",
            nameEn = "Cathedral Judgement",
            descId = "Ledakan sigil emas raksasa yang memingsankan musuh & memulihkan 12% HP.",
            descEn = "Massive golden sigil blast that stuns foes & heals 12% HP.",
            accentHex = "#F0C674",
            cooldownSec = 7.5f
        ),
        SkillDefinition(
            id = "skill2_abyssal_vortex",
            slot = 2,
            nameId = "Pusaran Jurang",
            nameEn = "Abyssal Vortex",
            descId = "Menarik semua musuh ke pusat sigil lalu meledakkan kerusakan kritikal ungu.",
            descEn = "Pulls enemies inward into a collapsing void nova with crit burst.",
            accentHex = "#B368E8",
            cooldownSec = 7.0f
        ),
        SkillDefinition(
            id = "skill2_blood_eclipse",
            slot = 2,
            nameId = "Gerhana Darah",
            nameEn = "Blood Eclipse",
            descId = "Memberi kebal sementara + badai darah radius luas dengan lifesteal besar.",
            descEn = "Grants brief invulnerability + wide blood storm with heavy lifesteal.",
            accentHex = "#9E2A3B",
            cooldownSec = 8.0f
        )
    )

    fun skillById(id: String): SkillDefinition =
        (availableSkill1 + availableSkill2).firstOrNull { it.id == id } ?: availableSkill1.first()

    fun classDisplayName(archetype: HeroArchetype, lang: AppLanguage): String = when (archetype) {
        HeroArchetype.BLOODBLADE -> if (lang == AppLanguage.INDONESIAN) "Ksatria Pedang Darah" else "Bloodblade Knight"
        HeroArchetype.NECROMANCER -> if (lang == AppLanguage.INDONESIAN) "Ahli Nujum Abu" else "Ashbound Necromancer"
        HeroArchetype.STALKER -> if (lang == AppLanguage.INDONESIAN) "Pengintai Bayangan" else "Shadow Stalker"
    }

    fun classLore(archetype: HeroArchetype, lang: AppLanguage): String = when (archetype) {
        HeroArchetype.BLOODBLADE -> if (lang == AppLanguage.INDONESIAN) {
            "Tebasan jarak dekat yang luas dengan serapan darah dan ketahanan tinggi."
        } else {
            "Wide crimson cleave arcs with built-in lifesteal and towering armor."
        }
        HeroArchetype.NECROMANCER -> if (lang == AppLanguage.INDONESIAN) {
            "Memanggil ledakan jiwa dan gelombang tulang terkutuk untuk mengendalikan arena."
        } else {
            "Commands spectral soul bolts and cursed bone novas to control the crypt."
        }
        HeroArchetype.STALKER -> if (lang == AppLanguage.INDONESIAN) {
            "Gerakan lincah, serangan kritis mematikan, dan perangkap bayangan."
        } else {
            "Blinding agility, lethal void criticals, and shadow ambush strikes."
        }
    }

    fun fallbackStage(depth: Int, lang: AppLanguage): GeneratedStage {
        val isBossFloor = depth % 3 == 0
        val biomes = listOf("CRIMSON_CRYPT", "BONE_CATHEDRAL", "VOID_SANCTUM", "ASHEN_OSSUARY")
        val biome = if (isBossFloor) "CRIMSON_CRYPT" else biomes[(depth - 1) % biomes.size]
        val ambientHex = when (biome) {
            "VOID_SANCTUM" -> "#100E1B"
            "BONE_CATHEDRAL" -> "#15141C"
            "ASHEN_OSSUARY" -> "#171316"
            else -> "#181016"
        }
        val torchHex = when (biome) {
            "VOID_SANCTUM" -> "#4EA8A8"
            "CRIMSON_CRYPT" -> "#E06D44"
            else -> "#D4A359"
        }
        val title = if (lang == AppLanguage.INDONESIAN) {
            when {
                isBossFloor -> "Tahta Abu: Lantai $depth"
                biome == "VOID_SANCTUM" -> "Suaka Kehampaan: Lantai $depth"
                biome == "ASHEN_OSSUARY" -> "Ruang Tulang Terbakar: Lantai $depth"
                else -> "Katedral Bayangan: Lantai $depth"
            }
        } else {
            when {
                isBossFloor -> "Throne of Ash: Floor $depth"
                biome == "VOID_SANCTUM" -> "Void Sanctum: Floor $depth"
                biome == "ASHEN_OSSUARY" -> "Ashen Ossuary: Floor $depth"
                else -> "Cathedral of Shadows: Floor $depth"
            }
        }
        val whisper = if (lang == AppLanguage.INDONESIAN) {
            "Obor berkedip di antara pilar batu yang meneteskan darah beku."
        } else {
            "Torches sputter between stone pillars weeping frozen blood."
        }
        val extraCount = ((depth - 1) / 2).coerceAtMost(2)
        val enemies = buildList {
            add(
                EnemyBlueprint(
                    id = "e_${depth}_1",
                    name = if (lang == AppLanguage.INDONESIAN) "Penjaga Tulang" else "Ossuary Warden",
                    behavior = "CHARGER",
                    maxHp = 70 + depth * 22,
                    damage = 12 + depth * 3,
                    speed = 115f + depth * 4f,
                    radius = 24f,
                    spawnXRatio = 0.22f,
                    spawnYRatio = 0.22f,
                    tintHex = "#9E2A3B"
                )
            )
            add(
                EnemyBlueprint(
                    id = "e_${depth}_2",
                    name = if (lang == AppLanguage.INDONESIAN) "Pendeta Kabut" else "Mist Acolyte",
                    behavior = "CASTER",
                    maxHp = 55 + depth * 18,
                    damage = 14 + depth * 3,
                    speed = 88f,
                    radius = 22f,
                    spawnXRatio = 0.78f,
                    spawnYRatio = 0.24f,
                    tintHex = "#4EA8A8"
                )
            )
            add(
                EnemyBlueprint(
                    id = "e_${depth}_3",
                    name = if (lang == AppLanguage.INDONESIAN) "Pengejar Malam" else "Night Stalker",
                    behavior = "STALKER",
                    maxHp = 50 + depth * 16,
                    damage = 15 + depth * 3,
                    speed = 145f + depth * 3f,
                    radius = 20f,
                    spawnXRatio = 0.5f,
                    spawnYRatio = 0.18f,
                    tintHex = "#D4A359"
                )
            )
            for (k in 0 until extraCount) {
                val beh = if (k % 2 == 0) "STALKER" else "CASTER"
                add(
                    EnemyBlueprint(
                        id = "e_${depth}_extra_$k",
                        name = if (lang == AppLanguage.INDONESIAN) "Bayangan Terkutuk" else "Cursed Shade",
                        behavior = beh,
                        maxHp = 48 + depth * 15,
                        damage = 12 + depth * 2,
                        speed = 125f + depth * 3f,
                        radius = 20f,
                        spawnXRatio = if (k % 2 == 0) 0.18f else 0.82f,
                        spawnYRatio = 0.34f,
                        tintHex = if (beh == "CASTER") "#4EA8A8" else "#B368E8"
                    )
                )
            }
            if (isBossFloor) {
                add(
                    EnemyBlueprint(
                        id = "boss_$depth",
                        name = if (lang == AppLanguage.INDONESIAN) "Uskup Terkutuk Malakor" else "Malakor, Fallen Prelate",
                        behavior = "BOSS_WARDEN",
                        maxHp = 260 + depth * 65,
                        damage = 22 + depth * 4,
                        speed = 105f,
                        radius = 40f,
                        spawnXRatio = 0.5f,
                        spawnYRatio = 0.28f,
                        tintHex = "#D94356"
                    )
                )
            }
        }
        val obstacles = when (depth % 3) {
            1 -> listOf(
                StageObstacle(0.26f, 0.42f, 34f, "PILLAR"),
                StageObstacle(0.74f, 0.42f, 34f, "PILLAR"),
                StageObstacle(0.50f, 0.56f, 28f, "ALTAR"),
                StageObstacle(0.26f, 0.64f, 24f, "BRAZIER"),
                StageObstacle(0.74f, 0.64f, 24f, "BRAZIER")
            )
            2 -> listOf(
                StageObstacle(0.32f, 0.36f, 30f, "STATUE"),
                StageObstacle(0.68f, 0.36f, 30f, "STATUE"),
                StageObstacle(0.32f, 0.64f, 26f, "BRAZIER"),
                StageObstacle(0.68f, 0.64f, 26f, "BRAZIER")
            )
            else -> listOf(
                StageObstacle(0.22f, 0.48f, 34f, "PILLAR"),
                StageObstacle(0.78f, 0.48f, 34f, "PILLAR"),
                StageObstacle(0.50f, 0.42f, 28f, "BRAZIER")
            )
        }
        return GeneratedStage(
            depth = depth,
            title = title,
            omenWhisper = whisper,
            biomeTheme = biome,
            ambientColorHex = ambientHex,
            torchColorHex = torchHex,
            fogIntensity = 0.42f,
            obstacles = obstacles,
            enemies = enemies
        )
    }

    fun fallbackNarrative(depth: Int, lang: AppLanguage): BranchingNarrativeEvent {
        return if (lang == AppLanguage.INDONESIAN) {
            BranchingNarrativeEvent(
                chronicleTitle = "Sumpah di Altar Lantai $depth",
                speakerName = "Suara dari Relikuari Pecah",
                storyText = "Sebuah cawan perak berlumur abu menawarkan kekuatan terlarang di tengah kabut katedral. Apa pilihanmu?",
                choices = listOf(
                    NarrativeChoice("drink", "Teguk Darah Abu", "+20% Serangan, -15 HP", -15, 20, 0, 25, "BLOOD_PACT"),
                    NarrativeChoice("shatter", "Hancurkan Altar", "+30 Esensi Jiwa, +10% Kecepatan", 0, 0, 10, 30, "DEFIANCE"),
                    NarrativeChoice("pray", "Berdoa dalam Sunyi", "+35 HP Pulih", 35, 5, 0, 10, "VIGIL")
                )
            )
        } else {
            BranchingNarrativeEvent(
                chronicleTitle = "Vow at Floor $depth Shrine",
                speakerName = "Voice of the Shattered Reliquary",
                storyText = "A silver chalice brimming with warm ash trembles in the mist, offering a dark pact before the next descent.",
                choices = listOf(
                    NarrativeChoice("drink", "Drink the Ashen Draught", "+20% Damage, -15 HP", -15, 20, 0, 25, "BLOOD_PACT"),
                    NarrativeChoice("shatter", "Shatter the Reliquary", "+30 Soul Essence, +10% Speed", 0, 0, 10, 30, "DEFIANCE"),
                    NarrativeChoice("pray", "Keep Silent Vigil", "Heal +35 HP", 35, 5, 0, 10, "VIGIL")
                )
            )
        }
    }
}
