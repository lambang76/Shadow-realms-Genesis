package com.example.data.catalog

import com.example.data.model.AppLanguage
import com.example.data.model.BranchingNarrativeEvent
import com.example.data.model.CommunityMod
import com.example.data.model.EnemyBlueprint
import com.example.data.model.GeneratedStage
import com.example.data.model.HeroArchetype
import com.example.data.model.NarrativeChoice
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
        )
    )

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
        val title = if (lang == AppLanguage.INDONESIAN) {
            if (isBossFloor) "Tahta Abu: Lantai $depth" else "Katedral Bayangan: Lantai $depth"
        } else {
            if (isBossFloor) "Throne of Ash: Floor $depth" else "Cathedral of Shadows: Floor $depth"
        }
        val whisper = if (lang == AppLanguage.INDONESIAN) {
            "Obor berkedip di antara pilar batu yang meneteskan darah beku."
        } else {
            "Torches sputter between stone pillars weeping frozen blood."
        }
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
                    spawnXRatio = 0.25f,
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
                    spawnXRatio = 0.76f,
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
            if (isBossFloor) {
                add(
                    EnemyBlueprint(
                        id = "boss_$depth",
                        name = if (lang == AppLanguage.INDONESIAN) "Uskup Terkutuk Malakor" else "Malakor, Fallen Prelate",
                        behavior = "BOSS_WARDEN",
                        maxHp = 240 + depth * 65,
                        damage = 22 + depth * 4,
                        speed = 105f,
                        radius = 38f,
                        spawnXRatio = 0.5f,
                        spawnYRatio = 0.28f,
                        tintHex = "#D94356"
                    )
                )
            }
        }
        return GeneratedStage(
            depth = depth,
            title = title,
            omenWhisper = whisper,
            biomeTheme = if (isBossFloor) "CRIMSON_CRYPT" else "BONE_CATHEDRAL",
            ambientColorHex = "#14121A",
            torchColorHex = "#D4A359",
            fogIntensity = 0.42f,
            obstacles = listOf(
                StageObstacle(0.28f, 0.42f, 34f, "PILLAR"),
                StageObstacle(0.72f, 0.42f, 34f, "PILLAR"),
                StageObstacle(0.50f, 0.58f, 28f, "ALTAR")
            ),
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
