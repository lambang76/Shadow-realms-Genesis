package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class AppLanguage(val code: String) {
    INDONESIAN("id"),
    ENGLISH("en")
}

enum class HeroArchetype(
    val id: String,
    val baseHp: Int,
    val baseDamage: Int,
    val baseSpeed: Float,
    val accentHex: String
) {
    BLOODBLADE("BLOODBLADE", 150, 26, 235f, "#9E2A3B"),
    NECROMANCER("NECROMANCER", 115, 22, 225f, "#4EA8A8"),
    STALKER("STALKER", 120, 24, 275f, "#D4A359");

    companion object {
        fun fromId(id: String): HeroArchetype =
            entries.firstOrNull { it.id == id } ?: BLOODBLADE
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Wanderer",
    val selectedClass: String = "BLOODBLADE",
    val soulEssence: Long = 120L,
    val highestDepth: Long = 1L,
    val totalRuns: Long = 0L,
    val totalKills: Long = 0L,
    val crimsonMightRank: Long = 0L,
    val obsidianWardRank: Long = 0L,
    val forbiddenArcanaRank: Long = 0L,
    val equippedWeaponId: String = "starter_bloodblade",
    val equippedRelicId: String = "starter_ash_signet",
    val discoveredChronicles: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CommunityMod(
    @DocumentId val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val modType: String = "WEAPON", // WEAPON, RELIC, RULESET
    val name: String = "",
    val loreEn: String = "",
    val loreId: String = "",
    val effectTag: String = "VAMPIRIC",
    val powerValue: Long = 30L,
    val speedValue: Long = 100L,
    val accentHex: String = "#D4A359",
    @get:PropertyName("isPublished") @set:PropertyName("isPublished")
    var isPublished: Boolean = true,
    val upvotes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RaidLobby(
    @DocumentId val id: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val inviteCode: String = "",
    @get:PropertyName("isPublic") @set:PropertyName("isPublic")
    var isPublic: Boolean = true,
    val status: String = "WAITING", // WAITING, GENERATING, IN_GAME, COMPLETED
    val stageDepth: Long = 1L,
    val stageTitle: String = "Cathedral of Ash",
    val biomeTheme: String = "CRIMSON_CRYPT",
    val stageData: Map<String, Any>? = null,
    val activeModIds: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RaidPlayerState(
    @DocumentId val id: String = "",
    val playerId: String = "",
    val displayName: String = "",
    val archetype: String = "BLOODBLADE",
    val posX: Double = 500.0,
    val posY: Double = 500.0,
    val facingAngle: Double = 0.0,
    val hp: Long = 150L,
    val maxHp: Long = 150L,
    @get:PropertyName("isAttacking") @set:PropertyName("isAttacking")
    var isAttacking: Boolean = false,
    val pingText: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RaidEnemyState(
    @DocumentId val id: String = "",
    val enemyId: String = "",
    val name: String = "",
    val hp: Long = 100L,
    val maxHp: Long = 100L,
    val posX: Double = 500.0,
    val posY: Double = 260.0,
    @get:PropertyName("isBoss") @set:PropertyName("isBoss")
    var isBoss: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// --- AI PROCEDURAL STAGE & NARRATIVE MODELS ---

data class StageObstacle(
    val xRatio: Float,
    val yRatio: Float,
    val radius: Float,
    val kind: String // PILLAR, STATUE, BRAZIER, ALTAR
)

data class EnemyBlueprint(
    val id: String,
    val name: String,
    val behavior: String, // CHARGER, CASTER, STALKER, BOSS_WARDEN
    val maxHp: Int,
    val damage: Int,
    val speed: Float,
    val radius: Float,
    val spawnXRatio: Float,
    val spawnYRatio: Float,
    val tintHex: String
)

data class GeneratedStage(
    val depth: Int,
    val title: String,
    val omenWhisper: String,
    val biomeTheme: String, // CRIMSON_CRYPT, BONE_CATHEDRAL, VOID_SANCTUM, ASHEN_OSSUARY
    val ambientColorHex: String,
    val torchColorHex: String,
    val fogIntensity: Float,
    val obstacles: List<StageObstacle>,
    val enemies: List<EnemyBlueprint>
)

data class NarrativeChoice(
    val id: String,
    val label: String,
    val consequenceHint: String,
    val hpDelta: Int,
    val damageBonusPct: Int,
    val speedBonusPct: Int,
    val soulEssenceDelta: Int,
    val curseTag: String
)

data class BranchingNarrativeEvent(
    val chronicleTitle: String,
    val speakerName: String,
    val storyText: String,
    val choices: List<NarrativeChoice>
)
