package com.example.game

import com.example.data.model.CommunityMod
import com.example.data.model.GeneratedStage
import com.example.data.model.HeroArchetype
import com.example.data.model.RaidPlayerState
import com.example.data.model.StageObstacle
import com.example.data.model.UserProfile
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Virtual 1000x1000 coordinate arena for real-time top-down action roguelike simulation.
 */
object ArenaBounds {
    const val SIZE = 1000f
    const val WALL_MARGIN = 48f
}

data class ActiveEnemy(
    val id: String,
    val name: String,
    val behavior: String,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val hp: Int,
    val maxHp: Int,
    val damage: Int,
    val speed: Float,
    val radius: Float,
    val tintHex: String,
    val attackCooldown: Float = 1.2f,
    val telegraphTimer: Float = 0f,
    val hitFlashTimer: Float = 0f,
    val stunTimer: Float = 0f,
    val isBoss: Boolean = false,
    val facingAngle: Float = (Math.PI / 2.0).toFloat(),
    val walkPhase: Float = 0f,
    val attackAnimTimer: Float = 0f
)

data class Projectile(
    val id: Long,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val damage: Int,
    val fromPlayer: Boolean,
    val colorHex: String,
    val ttl: Float
)

data class SlashArc(
    val x: Float,
    val y: Float,
    val angle: Float,
    val radius: Float,
    val colorHex: String,
    val ttl: Float,
    val maxTtl: Float,
    val sweepDeg: Float = 115f
)

data class ShockwaveRing(
    val x: Float,
    val y: Float,
    val maxRadius: Float,
    val colorHex: String,
    val ttl: Float,
    val maxTtl: Float
)

data class DashGhost(
    val x: Float,
    val y: Float,
    val colorHex: String,
    val ttl: Float,
    val maxTtl: Float
)

data class FloatingNumber(
    val id: Long,
    val x: Float,
    val y: Float,
    val text: String,
    val colorHex: String,
    val ttl: Float
)

data class SoulOrb(
    val id: Long,
    val x: Float,
    val y: Float,
    val value: Int
)

data class AshParticle(
    val x: Float,
    val y: Float,
    val vy: Float,
    val alpha: Float,
    val size: Float
)

data class CombatSnapshot(
    val stage: GeneratedStage,
    val archetype: HeroArchetype,
    val playerX: Float,
    val playerY: Float,
    val facingAngle: Float,
    val hp: Int,
    val maxHp: Int,
    val dashCooldown: Float,
    val skill1Cooldown: Float,
    val skill2Cooldown: Float,
    val invulnTimer: Float,
    val hitFlashTimer: Float,
    val weaponName: String,
    val secondaryWeaponName: String?,
    val usingSecondaryWeapon: Boolean,
    val skill1Id: String,
    val skill2Id: String,
    val weaponAccentHex: String,
    val weaponEffectTag: String,
    val relicEffectTag: String,
    val fogRadiusMultiplier: Float,
    val enemies: List<ActiveEnemy>,
    val projectiles: List<Projectile>,
    val slashes: List<SlashArc>,
    val rings: List<ShockwaveRing>,
    val dashGhosts: List<DashGhost>,
    val floatingNumbers: List<FloatingNumber>,
    val soulOrbs: List<SoulOrb>,
    val particles: List<AshParticle>,
    val remoteAllies: List<RaidPlayerState>,
    val killsThisRun: Int,
    val soulEarnedThisRun: Int,
    val comboCount: Int,
    val screenShake: Float,
    val stageCleared: Boolean,
    val playerDead: Boolean,
    val activePingBanner: String? = null,
    val elapsedTime: Float = 0f,
    val isMoving: Boolean = false,
    val movePhase: Float = 0f,
    val attackAnimTimer: Float = 0f,
    val attackAnimDuration: Float = 0.28f
)

class CombatSimulation(
    stage: GeneratedStage,
    private val profile: UserProfile,
    private val equippedWeapon: CommunityMod?,
    equippedRelic: CommunityMod?,
    activeRulesets: List<CommunityMod>,
    private val bonusDamagePct: Int,
    bonusSpeedPct: Int,
    currentHpOverride: Int?,
    private val onSlashSfx: () -> Unit,
    private val onDashSfx: () -> Unit,
    private val onHitSfx: () -> Unit,
    private val secondaryWeapon: CommunityMod? = null,
    private val skill1Id: String = "skill1_crimson_cleave",
    private val skill2Id: String = "skill2_judgement"
) {
    private val archetype = HeroArchetype.fromId(profile.selectedClass)
    private var nextId = 1L

    private val enemyHpScale: Float
    private val playerSpeedScale: Float
    private val fogMultiplier: Float

    init {
        var hpMult = 1f
        var spdMult = 1f + (bonusSpeedPct / 100f)
        var fogMult = 1f
        activeRulesets.forEach { rule ->
            hpMult *= (rule.powerValue.coerceIn(50L, 300L) / 100f)
            spdMult *= (rule.speedValue.coerceIn(70L, 200L) / 100f)
            if (rule.effectTag.contains("FOG", ignoreCase = true)) {
                fogMult *= 0.72f
            }
        }
        if (equippedRelic?.effectTag == "SHADOW_HASTE") {
            spdMult *= 1.12f
        }
        enemyHpScale = hpMult
        playerSpeedScale = spdMult
        fogMultiplier = fogMult
    }

    private val maxHp = archetype.baseHp + (profile.obsidianWardRank.toInt() * 18)
    private val cooldownMultiplier = (1f - profile.forbiddenArcanaRank.toInt() * 0.05f).coerceAtLeast(0.55f)
    private val relicTag = equippedRelic?.effectTag ?: "DASH_NOVA"

    private var usingSecondary = false
    private var primaryAttackCooldown = 0f
    private var speedBuffTimer = 0f
    private var comboTimer = 0f
    private var moveX = 0f
    private var moveY = 0f

    private fun currentWeapon(): CommunityMod? =
        if (usingSecondary && secondaryWeapon != null) secondaryWeapon else equippedWeapon

    private fun computeBaseDamage(): Int {
        val weaponPwr = currentWeapon()?.powerValue?.toInt()?.div(2) ?: 0
        return ((archetype.baseDamage + weaponPwr + profile.crimsonMightRank.toInt() * 4) *
            (1f + bonusDamagePct / 100f)).toInt().coerceAtLeast(10)
    }

    private fun computeAttackSpeedMult(): Float =
        ((currentWeapon()?.speedValue?.toFloat() ?: 100f) / 100f).coerceIn(0.6f, 2.2f)

    private var state = CombatSnapshot(
        stage = stage,
        archetype = archetype,
        playerX = ArenaBounds.SIZE * 0.5f,
        playerY = ArenaBounds.SIZE * 0.80f,
        facingAngle = (-Math.PI / 2.0).toFloat(),
        hp = (currentHpOverride ?: maxHp).coerceIn(1, maxHp),
        maxHp = maxHp,
        dashCooldown = 0f,
        skill1Cooldown = 0f,
        skill2Cooldown = 0f,
        invulnTimer = 0f,
        hitFlashTimer = 0f,
        weaponName = equippedWeapon?.name ?: archetype.name,
        secondaryWeaponName = secondaryWeapon?.name,
        usingSecondaryWeapon = false,
        skill1Id = skill1Id,
        skill2Id = skill2Id,
        weaponAccentHex = equippedWeapon?.accentHex ?: archetype.accentHex,
        weaponEffectTag = equippedWeapon?.effectTag ?: "VAMPIRIC",
        relicEffectTag = relicTag,
        fogRadiusMultiplier = fogMultiplier,
        enemies = stage.enemies.map { bp ->
            val scaledHp = (bp.maxHp * enemyHpScale).toInt().coerceAtLeast(25)
            ActiveEnemy(
                id = bp.id,
                name = bp.name,
                behavior = bp.behavior,
                x = bp.spawnXRatio * ArenaBounds.SIZE,
                y = bp.spawnYRatio * ArenaBounds.SIZE,
                vx = 0f,
                vy = 0f,
                hp = scaledHp,
                maxHp = scaledHp,
                damage = bp.damage,
                speed = bp.speed,
                radius = bp.radius,
                tintHex = bp.tintHex,
                isBoss = bp.behavior == "BOSS_WARDEN"
            )
        },
        projectiles = emptyList(),
        slashes = emptyList(),
        rings = emptyList(),
        dashGhosts = emptyList(),
        floatingNumbers = emptyList(),
        soulOrbs = emptyList(),
        particles = List(36) {
            AshParticle(
                x = Random.nextFloat() * ArenaBounds.SIZE,
                y = Random.nextFloat() * ArenaBounds.SIZE,
                vy = -(16f + Random.nextFloat() * 38f),
                alpha = 0.22f + Random.nextFloat() * 0.55f,
                size = 2f + Random.nextFloat() * 3.8f
            )
        },
        remoteAllies = emptyList(),
        killsThisRun = 0,
        soulEarnedThisRun = 0,
        comboCount = 0,
        screenShake = 0f,
        stageCleared = false,
        playerDead = false
    )

    fun snapshot(): CombatSnapshot = state

    fun setMoveInput(dx: Float, dy: Float) {
        moveX = dx.coerceIn(-1f, 1f)
        moveY = dy.coerceIn(-1f, 1f)
        if (hypot(moveX, moveY) > 0.15f) {
            state = state.copy(facingAngle = atan2(moveY, moveX))
        }
    }

    fun triggerWeaponSwap() {
        if (secondaryWeapon == null || state.playerDead) return
        usingSecondary = !usingSecondary
        val active = currentWeapon()
        val accent = active?.accentHex ?: archetype.accentHex
        val tag = active?.effectTag ?: "VAMPIRIC"
        onDashSfx()
        state = state.copy(
            usingSecondaryWeapon = usingSecondary,
            weaponName = active?.name ?: archetype.name,
            weaponAccentHex = accent,
            weaponEffectTag = tag,
            rings = state.rings + ShockwaveRing(state.playerX, state.playerY, 64f, accent, 0.25f, 0.25f),
            floatingNumbers = state.floatingNumbers + FloatingNumber(
                nextId++, state.playerX, state.playerY - 36f, active?.name?.take(14) ?: "SWAP", accent, 0.85f
            )
        )
    }

    fun updateRemoteAllies(allies: List<RaidPlayerState>) {
        state = state.copy(remoteAllies = allies)
    }

    fun applyRemoteEnemySync(remoteHpById: Map<String, Int>) {
        if (remoteHpById.isEmpty()) return
        val updated = state.enemies.mapNotNull { enemy ->
            val syncedHp = remoteHpById[enemy.id]
            val finalHp = if (syncedHp != null) minOf(enemy.hp, syncedHp) else enemy.hp
            if (finalHp <= 0) null else enemy.copy(hp = finalHp)
        }
        state = state.copy(
            enemies = updated,
            stageCleared = updated.isEmpty() && !state.playerDead
        )
    }

    private fun autoAimTowardNearestEnemy(): Float {
        if (hypot(moveX, moveY) > 0.25f) return state.facingAngle
        val nearest = state.enemies.minByOrNull { hypot(it.x - state.playerX, it.y - state.playerY) }
        return if (nearest != null) {
            val angle = atan2(nearest.y - state.playerY, nearest.x - state.playerX)
            state = state.copy(facingAngle = angle)
            angle
        } else {
            state.facingAngle
        }
    }

    fun triggerPrimaryAttack() {
        if (primaryAttackCooldown > 0f || state.playerDead) return
        val spdMult = computeAttackSpeedMult()
        primaryAttackCooldown = (0.34f / spdMult).coerceIn(0.15f, 0.58f)
        onSlashSfx()

        val angle = autoAimTowardNearestEnemy()
        val dmg = computeBaseDamage()
        val wTag = state.weaponEffectTag
        val wHex = state.weaponAccentHex
        val animDur = (0.28f / spdMult.coerceAtMost(1.4f)).coerceIn(0.18f, 0.30f)
        state = state.copy(attackAnimTimer = animDur, attackAnimDuration = animDur)

        when {
            wTag == "SOULBURN" || (archetype == HeroArchetype.NECROMANCER && wTag == "VAMPIRIC") -> {
                val bolts = (-1..1).map { offset ->
                    val a = angle + offset * 0.14f
                    Projectile(
                        id = nextId++,
                        x = state.playerX + cos(a) * 26f,
                        y = state.playerY + sin(a) * 26f,
                        vx = cos(a) * 540f,
                        vy = sin(a) * 540f,
                        radius = 11f,
                        damage = if (offset == 0) dmg else (dmg * 0.65f).toInt(),
                        fromPlayer = true,
                        colorHex = wHex,
                        ttl = 1.35f
                    )
                }
                state = state.copy(projectiles = state.projectiles + bolts)
            }
            wTag == "REAPING_CLEAVE" -> {
                val reach = 152f
                val slash = SlashArc(
                    x = state.playerX + cos(angle) * 22f,
                    y = state.playerY + sin(angle) * 22f,
                    angle = angle,
                    radius = reach,
                    colorHex = wHex,
                    ttl = 0.22f,
                    maxTtl = 0.22f,
                    sweepDeg = 165f
                )
                // Reap pulls nearby soul orbs
                val pulledOrbs = state.soulOrbs.map { orb ->
                    orb.copy(
                        x = orb.x + (state.playerX - orb.x) * 0.45f,
                        y = orb.y + (state.playerY - orb.y) * 0.45f
                    )
                }
                state = state.copy(slashes = state.slashes + slash, soulOrbs = pulledOrbs)
                damageEnemiesInCone(state.playerX, state.playerY, angle, reach, dmg, 1.55f)
            }
            wTag == "THUNDER_SMITE" -> {
                val reach = 135f
                val slash = SlashArc(
                    x = state.playerX + cos(angle) * 26f,
                    y = state.playerY + sin(angle) * 26f,
                    angle = angle,
                    radius = reach,
                    colorHex = wHex,
                    ttl = 0.22f,
                    maxTtl = 0.22f,
                    sweepDeg = 140f
                )
                val ring = ShockwaveRing(state.playerX + cos(angle) * 44f, state.playerY + sin(angle) * 44f, 120f, wHex, 0.26f, 0.26f)
                state = state.copy(slashes = state.slashes + slash, rings = state.rings + ring, screenShake = 8f)
                damageEnemiesInCone(state.playerX, state.playerY, angle, reach, (dmg * 1.15f).toInt(), 1.4f, stunSec = 0.55f)
            }
            wTag == "VOID_CRIT" -> {
                val reach = 108f
                val s1 = SlashArc(state.playerX + cos(angle) * 26f, state.playerY + sin(angle) * 26f, angle - 0.18f, reach, wHex, 0.16f, 0.16f, 95f)
                val s2 = SlashArc(state.playerX + cos(angle) * 32f, state.playerY + sin(angle) * 32f, angle + 0.18f, reach * 0.92f, "#FFFFFF", 0.14f, 0.14f, 95f)
                state = state.copy(slashes = state.slashes + s1 + s2)
                damageEnemiesInCone(state.playerX, state.playerY, angle, reach, dmg, 1.3f)
            }
            else -> {
                val reach = if (archetype == HeroArchetype.BLOODBLADE) 134f else 106f
                val slash = SlashArc(
                    x = state.playerX + cos(angle) * 30f,
                    y = state.playerY + sin(angle) * 30f,
                    angle = angle,
                    radius = reach,
                    colorHex = wHex,
                    ttl = 0.19f,
                    maxTtl = 0.19f,
                    sweepDeg = 125f
                )
                state = state.copy(slashes = state.slashes + slash)
                damageEnemiesInCone(state.playerX, state.playerY, angle, reach, dmg, 1.38f)
            }
        }
    }

    fun triggerDash() {
        if (state.dashCooldown > 0f || state.playerDead) return
        onDashSfx()
        val angle = state.facingAngle
        val dashDist = 158f
        val startX = state.playerX
        val startY = state.playerY
        val targetX = (startX + cos(angle) * dashDist)
            .coerceIn(ArenaBounds.WALL_MARGIN, ArenaBounds.SIZE - ArenaBounds.WALL_MARGIN)
        val targetY = (startY + sin(angle) * dashDist)
            .coerceIn(ArenaBounds.WALL_MARGIN, ArenaBounds.SIZE - ArenaBounds.WALL_MARGIN)

        val ghosts = listOf(0.25f, 0.55f, 0.8f).map { t ->
            DashGhost(
                x = startX + (targetX - startX) * t,
                y = startY + (targetY - startY) * t,
                colorHex = state.weaponAccentHex,
                ttl = 0.28f,
                maxTtl = 0.28f
            )
        }
        val invulnBonus = if (relicTag == "SHADOW_HASTE") 0.50f else 0.36f
        state = state.copy(
            playerX = targetX,
            playerY = targetY,
            dashCooldown = 1.5f * cooldownMultiplier,
            invulnTimer = invulnBonus,
            dashGhosts = state.dashGhosts + ghosts
        )

        if (relicTag == "DASH_NOVA" || relicTag == "FROSTBITE") {
            val dmg = computeBaseDamage()
            val nova = ShockwaveRing(targetX, targetY, 125f, "#D4A359", 0.28f, 0.28f)
            state = state.copy(rings = state.rings + nova)
            damageEnemiesInRadius(targetX, targetY, 125f, (dmg * 0.8f).toInt())
        }
    }

    fun triggerSkill1() {
        if (state.skill1Cooldown > 0f || state.playerDead) return
        onSlashSfx()
        val angle = autoAimTowardNearestEnemy()
        val dmg = computeBaseDamage()
        state = state.copy(attackAnimTimer = 0.32f, attackAnimDuration = 0.32f)

        when (skill1Id) {
            "skill1_soul_barrage" -> {
                state = state.copy(skill1Cooldown = 4.0f * cooldownMultiplier)
                val burst = (0 until 7).map { idx ->
                    val a = angle + (idx - 3f) * 0.19f
                    Projectile(
                        id = nextId++,
                        x = state.playerX,
                        y = state.playerY,
                        vx = cos(a) * 490f,
                        vy = sin(a) * 490f,
                        radius = 12f,
                        damage = (dmg * 0.95f).toInt(),
                        fromPlayer = true,
                        colorHex = "#4EA8A8",
                        ttl = 1.4f
                    )
                }
                state = state.copy(
                    projectiles = state.projectiles + burst,
                    rings = state.rings + ShockwaveRing(state.playerX, state.playerY, 90f, "#4EA8A8", 0.24f, 0.24f)
                )
            }
            "skill1_phantom_ring" -> {
                state = state.copy(skill1Cooldown = 3.5f * cooldownMultiplier)
                speedBuffTimer = 2.2f
                val daggers = (0 until 10).map { idx ->
                    val a = idx * (Math.PI * 2.0 / 10.0).toFloat()
                    Projectile(
                        id = nextId++,
                        x = state.playerX,
                        y = state.playerY,
                        vx = cos(a) * 560f,
                        vy = sin(a) * 560f,
                        radius = 10f,
                        damage = (dmg * 0.9f).toInt(),
                        fromPlayer = true,
                        colorHex = "#D4A359",
                        ttl = 1.05f
                    )
                }
                state = state.copy(
                    projectiles = state.projectiles + daggers,
                    rings = state.rings + ShockwaveRing(state.playerX, state.playerY, 120f, "#D4A359", 0.25f, 0.25f)
                )
            }
            else -> {
                // skill1_crimson_cleave
                state = state.copy(skill1Cooldown = 3.8f * cooldownMultiplier)
                val arc = SlashArc(state.playerX, state.playerY, angle, 170f, "#D94356", 0.28f, 0.28f, 340f)
                val ring = ShockwaveRing(state.playerX, state.playerY, 175f, "#D94356", 0.28f, 0.28f)
                state = state.copy(slashes = state.slashes + arc, rings = state.rings + ring, screenShake = 6f)
                val hits = damageEnemiesInRadius(state.playerX, state.playerY, 175f, (dmg * 1.45f).toInt())
                if (hits > 0) {
                    val heal = (maxHp * 0.05f).toInt().coerceAtLeast(4)
                    state = state.copy(
                        hp = (state.hp + heal).coerceAtMost(maxHp),
                        floatingNumbers = state.floatingNumbers + FloatingNumber(nextId++, state.playerX, state.playerY - 30f, "+$heal", "#4EA8A8", 0.8f)
                    )
                }
            }
        }
    }

    fun triggerSkill2() {
        if (state.skill2Cooldown > 0f || state.playerDead) return
        onSlashSfx()
        val dmg = computeBaseDamage()
        state = state.copy(attackAnimTimer = 0.38f, attackAnimDuration = 0.38f)

        when (skill2Id) {
            "skill2_abyssal_vortex" -> {
                state = state.copy(skill2Cooldown = 7.0f * cooldownMultiplier, screenShake = 14f)
                val vortexRadius = 260f
                // Pull nearby enemies toward player then blast!
                val pulled = state.enemies.map { en ->
                    val dx = state.playerX - en.x
                    val dy = state.playerY - en.y
                    val d = hypot(dx, dy)
                    if (d <= vortexRadius && d > 10f) {
                        en.copy(
                            x = en.x + dx * 0.45f,
                            y = en.y + dy * 0.45f,
                            stunTimer = maxOf(en.stunTimer, 0.7f)
                        )
                    } else en
                }
                state = state.copy(
                    enemies = pulled,
                    rings = state.rings + ShockwaveRing(state.playerX, state.playerY, vortexRadius, "#B368E8", 0.42f, 0.42f),
                    slashes = state.slashes + SlashArc(state.playerX, state.playerY, state.facingAngle, 210f, "#B368E8", 0.35f, 0.35f, 340f)
                )
                damageEnemiesInRadius(state.playerX, state.playerY, vortexRadius, (dmg * 2.4f).toInt(), stunSec = 0.7f)
            }
            "skill2_blood_eclipse" -> {
                state = state.copy(
                    skill2Cooldown = 8.0f * cooldownMultiplier,
                    invulnTimer = maxOf(state.invulnTimer, 1.2f),
                    screenShake = 12f
                )
                val eclipseRadius = 230f
                state = state.copy(
                    rings = state.rings + ShockwaveRing(state.playerX, state.playerY, eclipseRadius, "#9E2A3B", 0.40f, 0.40f),
                    slashes = state.slashes + SlashArc(state.playerX, state.playerY, state.facingAngle, eclipseRadius, "#D94356", 0.35f, 0.35f, 340f)
                )
                val hits = damageEnemiesInRadius(state.playerX, state.playerY, eclipseRadius, (dmg * 2.15f).toInt())
                if (hits > 0) {
                    val heal = (maxHp * 0.16f).toInt().coerceAtLeast(12)
                    state = state.copy(
                        hp = (state.hp + heal).coerceAtMost(maxHp),
                        floatingNumbers = state.floatingNumbers + FloatingNumber(nextId++, state.playerX, state.playerY - 32f, "+$heal", "#4EA8A8", 0.95f)
                    )
                }
            }
            else -> {
                // skill2_judgement
                state = state.copy(skill2Cooldown = 7.5f * cooldownMultiplier, screenShake = 15f)
                val ultRadius = 225f
                state = state.copy(
                    slashes = state.slashes + SlashArc(state.playerX, state.playerY, state.facingAngle, ultRadius, "#F0C674", 0.36f, 0.36f, 340f),
                    rings = state.rings + ShockwaveRing(state.playerX, state.playerY, ultRadius, "#F0C674", 0.40f, 0.40f)
                )
                val dealt = damageEnemiesInRadius(state.playerX, state.playerY, ultRadius, (dmg * 2.35f).toInt(), stunSec = 0.9f)
                if (dealt > 0 || archetype == HeroArchetype.BLOODBLADE) {
                    val heal = (maxHp * 0.12f).toInt().coerceAtLeast(8)
                    state = state.copy(
                        hp = (state.hp + heal).coerceAtMost(maxHp),
                        floatingNumbers = state.floatingNumbers + FloatingNumber(nextId++, state.playerX, state.playerY - 30f, "+$heal", "#4EA8A8", 0.9f)
                    )
                }
            }
        }
    }

    fun step(dtRaw: Float) {
        if (state.playerDead) return
        val dt = dtRaw.coerceIn(0.001f, 0.05f)

        primaryAttackCooldown = (primaryAttackCooldown - dt).coerceAtLeast(0f)
        speedBuffTimer = (speedBuffTimer - dt).coerceAtLeast(0f)
        if (comboTimer > 0f) {
            comboTimer = (comboTimer - dt).coerceAtLeast(0f)
        }
        val activeCombo = if (comboTimer > 0f) state.comboCount else 0
        val shake = (state.screenShake - dt * 28f).coerceAtLeast(0f)
        val attackAnim = (state.attackAnimTimer - dt).coerceAtLeast(0f)

        // Move player (stays active even after stageCleared so player can collect orbs & enter portal!)
        val speedBuff = if (speedBuffTimer > 0f) 1.28f else 1f
        val speed = archetype.baseSpeed * playerSpeedScale * speedBuff
        val moving = hypot(moveX, moveY) > 0.08f
        val nextMovePhase = if (moving) state.movePhase + dt * 12f * speedBuff else state.movePhase
        var px = state.playerX + moveX * speed * dt
        var py = state.playerY + moveY * speed * dt
        px = px.coerceIn(ArenaBounds.WALL_MARGIN, ArenaBounds.SIZE - ArenaBounds.WALL_MARGIN)
        py = py.coerceIn(ArenaBounds.WALL_MARGIN, ArenaBounds.SIZE - ArenaBounds.WALL_MARGIN)

        val resolvedPlayer = resolveObstacleCollision(px, py, 20f, state.stage.obstacles)
        px = resolvedPlayer.first
        py = resolvedPlayer.second

        // Update particles
        val particles = state.particles.map { p ->
            val ny = p.y + p.vy * dt
            if (ny < 0f) p.copy(x = Random.nextFloat() * ArenaBounds.SIZE, y = ArenaBounds.SIZE)
            else p.copy(y = ny)
        }

        // Update projectiles
        val survivingProjectiles = mutableListOf<Projectile>()
        val currentEnemies = state.enemies.toMutableList()
        var currentHp = state.hp
        var invuln = (state.invulnTimer - dt).coerceAtLeast(0f)
        var hitFlash = (state.hitFlashTimer - dt).coerceAtLeast(0f)
        val floats = state.floatingNumbers
            .mapNotNull { fn -> if (fn.ttl - dt <= 0f) null else fn.copy(y = fn.y - 28f * dt, ttl = fn.ttl - dt) }
            .toMutableList()
        val orbs = state.soulOrbs.toMutableList()
        var kills = state.killsThisRun
        var souls = state.soulEarnedThisRun
        var combo = activeCombo
        var newShake = shake

        for (proj in state.projectiles) {
            val nttl = proj.ttl - dt
            if (nttl <= 0f) continue
            val nx = proj.x + proj.vx * dt
            val ny = proj.y + proj.vy * dt
            if (nx < 20f || nx > ArenaBounds.SIZE - 20f || ny < 20f || ny > ArenaBounds.SIZE - 20f) continue
            if (hitsObstacle(nx, ny, proj.radius, state.stage.obstacles)) continue

            var consumed = false
            if (proj.fromPlayer) {
                for (i in currentEnemies.indices) {
                    val en = currentEnemies[i]
                    if (hypot(nx - en.x, ny - en.y) <= en.radius + proj.radius) {
                        val critChance = if (state.weaponEffectTag == "VOID_CRIT") 0.32f else 0.12f
                        val crit = if (Random.nextFloat() < critChance) 1.6f else 1f
                        val dmg = (proj.damage * crit).toInt()
                        val newEnHp = en.hp - dmg
                        onHitSfx()
                        combo++
                        comboTimer = 3.2f
                        floats.add(
                            FloatingNumber(
                                nextId++, en.x, en.y - en.radius,
                                if (crit > 1f) "$dmg!" else "$dmg",
                                if (crit > 1f) "#F0C674" else "#EAE2D6",
                                0.7f
                            )
                        )
                        if (newEnHp <= 0) {
                            kills++
                            orbs.add(SoulOrb(nextId++, en.x, en.y, if (en.isBoss) 55 else 14))
                            currentEnemies.removeAt(i)
                        } else {
                            currentEnemies[i] = en.copy(hp = newEnHp, hitFlashTimer = 0.14f)
                        }
                        consumed = true
                        break
                    }
                }
            } else {
                if (invuln <= 0f && hypot(nx - px, ny - py) <= 20f + proj.radius) {
                    currentHp = (currentHp - proj.damage).coerceAtLeast(0)
                    invuln = 0.45f
                    hitFlash = 0.22f
                    newShake = maxOf(newShake, 7f)
                    onHitSfx()
                    floats.add(FloatingNumber(nextId++, px, py - 24f, "-${proj.damage}", "#D94356", 0.8f))
                    consumed = true
                }
            }

            if (!consumed) {
                survivingProjectiles.add(proj.copy(x = nx, y = ny, ttl = nttl))
            }
        }

        // Update enemies & AI
        val updatedEnemies = mutableListOf<ActiveEnemy>()
        for (en in currentEnemies) {
            val stun = (en.stunTimer - dt).coerceAtLeast(0f)
            if (stun > 0f) {
                updatedEnemies.add(
                    en.copy(
                        stunTimer = stun,
                        hitFlashTimer = (en.hitFlashTimer - dt).coerceAtLeast(0f)
                    )
                )
                continue
            }

            val dx = px - en.x
            val dy = py - en.y
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            val dirX = dx / dist
            val dirY = dy / dist
            val slowFactor = if (relicTag == "FROSTBITE" && dist < 175f) 0.72f else 1f
            val moveSpd = when (en.behavior) {
                "CASTER" -> if (dist < 220f) -en.speed * 0.4f else if (dist > 340f) en.speed else 0f
                "STALKER" -> en.speed * 1.15f
                else -> en.speed
            } * slowFactor

            var ex = (en.x + dirX * moveSpd * dt).coerceIn(ArenaBounds.WALL_MARGIN, ArenaBounds.SIZE - ArenaBounds.WALL_MARGIN)
            var ey = (en.y + dirY * moveSpd * dt).coerceIn(ArenaBounds.WALL_MARGIN, ArenaBounds.SIZE - ArenaBounds.WALL_MARGIN)
            val resolvedEn = resolveObstacleCollision(ex, ey, en.radius, state.stage.obstacles)
            ex = resolvedEn.first
            ey = resolvedEn.second

            var atkCd = (en.attackCooldown - dt).coerceAtLeast(0f)
            var tele = (en.telegraphTimer - dt).coerceAtLeast(0f)
            var enAtkAnim = (en.attackAnimTimer - dt).coerceAtLeast(0f)
            val enFacing = atan2(dy, dx)
            val enWalk = en.walkPhase + dt * (kotlin.math.abs(moveSpd) * 0.095f).coerceAtLeast(1.8f)

            if (atkCd <= 0f) {
                when (en.behavior) {
                    "CASTER" -> {
                        val a = atan2(dy, dx)
                        survivingProjectiles.add(
                            Projectile(
                                id = nextId++, x = ex, y = ey,
                                vx = cos(a) * 265f, vy = sin(a) * 265f,
                                radius = 10f, damage = en.damage, fromPlayer = false,
                                colorHex = "#4EA8A8", ttl = 2.6f
                            )
                        )
                        atkCd = 2.1f
                        enAtkAnim = 0.28f
                    }
                    "BOSS_WARDEN" -> {
                        for (k in 0 until 8) {
                            val a = (k * (Math.PI * 2.0) / 8.0).toFloat() + state.elapsedTime
                            survivingProjectiles.add(
                                Projectile(
                                    id = nextId++, x = ex, y = ey,
                                    vx = cos(a) * 240f, vy = sin(a) * 240f,
                                    radius = 12f, damage = en.damage, fromPlayer = false,
                                    colorHex = "#D94356", ttl = 2.8f
                                )
                            )
                        }
                        atkCd = 2.3f
                        tele = 0.5f
                        enAtkAnim = 0.35f
                    }
                    else -> {
                        if (dist <= en.radius + 26f && invuln <= 0f) {
                            currentHp = (currentHp - en.damage).coerceAtLeast(0)
                            invuln = 0.55f
                            hitFlash = 0.25f
                            newShake = maxOf(newShake, 7f)
                            onHitSfx()
                            floats.add(FloatingNumber(nextId++, px, py - 24f, "-${en.damage}", "#D94356", 0.8f))
                            atkCd = 1.15f
                            enAtkAnim = 0.28f
                        }
                    }
                }
            }

            updatedEnemies.add(
                en.copy(
                    x = ex, y = ey,
                    attackCooldown = atkCd,
                    telegraphTimer = tele,
                    hitFlashTimer = (en.hitFlashTimer - dt).coerceAtLeast(0f),
                    facingAngle = enFacing,
                    walkPhase = enWalk,
                    attackAnimTimer = enAtkAnim
                )
            )
        }

        // Collect nearby Soul Orbs
        val remainingOrbs = mutableListOf<SoulOrb>()
        for (orb in orbs) {
            val d = hypot(px - orb.x, py - orb.y)
            if (d < 48f) {
                souls += orb.value
                floats.add(FloatingNumber(nextId++, px, py - 34f, "+${orb.value} Soul", "#4EA8A8", 0.85f))
            } else if (d < 185f) {
                val pull = 310f * dt / d.coerceAtLeast(1f)
                remainingOrbs.add(orb.copy(x = orb.x + (px - orb.x) * pull, y = orb.y + (py - orb.y) * pull))
            } else {
                remainingOrbs.add(orb)
            }
        }

        val slashes = state.slashes.mapNotNull { s ->
            if (s.ttl - dt <= 0f) null else s.copy(ttl = s.ttl - dt)
        }
        val rings = state.rings.mapNotNull { r ->
            if (r.ttl - dt <= 0f) null else r.copy(ttl = r.ttl - dt)
        }
        val ghosts = state.dashGhosts.mapNotNull { g ->
            if (g.ttl - dt <= 0f) null else g.copy(ttl = g.ttl - dt)
        }

        val clearedNow = updatedEnemies.isEmpty() && currentHp > 0
        val finalProjectiles = if (clearedNow) survivingProjectiles.filter { it.fromPlayer } else survivingProjectiles

        state = state.copy(
            playerX = px,
            playerY = py,
            hp = currentHp,
            dashCooldown = (state.dashCooldown - dt).coerceAtLeast(0f),
            skill1Cooldown = (state.skill1Cooldown - dt).coerceAtLeast(0f),
            skill2Cooldown = (state.skill2Cooldown - dt).coerceAtLeast(0f),
            invulnTimer = invuln,
            hitFlashTimer = hitFlash,
            enemies = updatedEnemies,
            projectiles = finalProjectiles,
            slashes = slashes,
            rings = rings,
            dashGhosts = ghosts,
            floatingNumbers = floats,
            soulOrbs = remainingOrbs,
            particles = particles,
            killsThisRun = kills,
            soulEarnedThisRun = souls,
            comboCount = combo,
            screenShake = newShake,
            stageCleared = clearedNow,
            playerDead = currentHp <= 0,
            elapsedTime = state.elapsedTime + dt,
            isMoving = moving,
            movePhase = nextMovePhase,
            attackAnimTimer = attackAnim
        )
    }

    private fun damageEnemiesInCone(
        ox: Float, oy: Float, facing: Float, reach: Float, dmg: Int, halfArcRad: Float, stunSec: Float = 0f
    ): Int {
        var hits = 0
        val remaining = mutableListOf<ActiveEnemy>()
        val floats = state.floatingNumbers.toMutableList()
        val orbs = state.soulOrbs.toMutableList()
        var kills = state.killsThisRun
        var hp = state.hp
        var combo = state.comboCount

        for (en in state.enemies) {
            val dx = en.x - ox
            val dy = en.y - oy
            val dist = hypot(dx, dy)
            val angleToEn = atan2(dy, dx)
            val diff = kotlin.math.abs(atan2(sin(angleToEn - facing), cos(angleToEn - facing)))
            if (dist <= reach + en.radius && diff <= halfArcRad) {
                hits++
                combo++
                comboTimer = 3.2f
                val critChance = if (state.weaponEffectTag == "VOID_CRIT") 0.34f else 0.14f
                val isCrit = Random.nextFloat() < critChance
                val finalDmg = if (isCrit) (dmg * 1.65f).toInt() else dmg
                val newHp = en.hp - finalDmg
                onHitSfx()
                floats.add(
                    FloatingNumber(
                        nextId++, en.x, en.y - en.radius,
                        if (isCrit) "$finalDmg!" else "$finalDmg",
                        if (isCrit) "#F0C674" else "#EAE2D6", 0.7f
                    )
                )
                if (state.weaponEffectTag == "VAMPIRIC" && Random.nextFloat() < 0.28f) {
                    hp = (hp + 5).coerceAtMost(maxHp)
                }
                if (newHp <= 0) {
                    kills++
                    orbs.add(SoulOrb(nextId++, en.x, en.y, if (en.isBoss) 55 else 14))
                } else {
                    remaining.add(
                        en.copy(
                            hp = newHp,
                            hitFlashTimer = 0.15f,
                            stunTimer = maxOf(en.stunTimer, stunSec)
                        )
                    )
                }
            } else {
                remaining.add(en)
            }
        }
        state = state.copy(
            hp = hp,
            enemies = remaining,
            floatingNumbers = floats,
            soulOrbs = orbs,
            killsThisRun = kills,
            comboCount = combo,
            stageCleared = remaining.isEmpty() && hp > 0
        )
        return hits
    }

    private fun damageEnemiesInRadius(
        cx: Float, cy: Float, radius: Float, dmg: Int, stunSec: Float = 0f
    ): Int {
        var hits = 0
        val remaining = mutableListOf<ActiveEnemy>()
        val floats = state.floatingNumbers.toMutableList()
        val orbs = state.soulOrbs.toMutableList()
        var kills = state.killsThisRun
        var combo = state.comboCount

        for (en in state.enemies) {
            if (hypot(en.x - cx, en.y - cy) <= radius + en.radius) {
                hits++
                combo++
                comboTimer = 3.2f
                val newHp = en.hp - dmg
                onHitSfx()
                floats.add(FloatingNumber(nextId++, en.x, en.y - en.radius, "$dmg", "#F0C674", 0.75f))
                if (newHp <= 0) {
                    kills++
                    orbs.add(SoulOrb(nextId++, en.x, en.y, if (en.isBoss) 55 else 14))
                } else {
                    remaining.add(
                        en.copy(
                            hp = newHp,
                            hitFlashTimer = 0.16f,
                            stunTimer = maxOf(en.stunTimer, stunSec)
                        )
                    )
                }
            } else {
                remaining.add(en)
            }
        }
        state = state.copy(
            enemies = remaining,
            floatingNumbers = floats,
            soulOrbs = orbs,
            killsThisRun = kills,
            comboCount = combo,
            stageCleared = remaining.isEmpty() && state.hp > 0
        )
        return hits
    }

    private fun resolveObstacleCollision(
        x: Float, y: Float, entityRadius: Float, obstacles: List<StageObstacle>
    ): Pair<Float, Float> {
        var rx = x
        var ry = y
        for (obs in obstacles) {
            val ox = obs.xRatio * ArenaBounds.SIZE
            val oy = obs.yRatio * ArenaBounds.SIZE
            val minDist = obs.radius + entityRadius
            val dx = rx - ox
            val dy = ry - oy
            val d = hypot(dx, dy)
            if (d < minDist && d > 0.001f) {
                val push = (minDist - d) / d
                rx += dx * push
                ry += dy * push
            }
        }
        return rx to ry
    }

    private fun hitsObstacle(
        x: Float, y: Float, radius: Float, obstacles: List<StageObstacle>
    ): Boolean = obstacles.any { obs ->
        val ox = obs.xRatio * ArenaBounds.SIZE
        val oy = obs.yRatio * ArenaBounds.SIZE
        hypot(x - ox, y - oy) < obs.radius + radius
    }
}
