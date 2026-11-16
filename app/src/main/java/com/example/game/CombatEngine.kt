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
    val isBoss: Boolean = false
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
    val weaponAccentHex: String,
    val weaponEffectTag: String,
    val relicEffectTag: String,
    val fogRadiusMultiplier: Float,
    val enemies: List<ActiveEnemy>,
    val projectiles: List<Projectile>,
    val slashes: List<SlashArc>,
    val floatingNumbers: List<FloatingNumber>,
    val soulOrbs: List<SoulOrb>,
    val particles: List<AshParticle>,
    val remoteAllies: List<RaidPlayerState>,
    val killsThisRun: Int,
    val soulEarnedThisRun: Int,
    val stageCleared: Boolean,
    val playerDead: Boolean,
    val activePingBanner: String? = null,
    val elapsedTime: Float = 0f
)

class CombatSimulation(
    stage: GeneratedStage,
    profile: UserProfile,
    equippedWeapon: CommunityMod?,
    equippedRelic: CommunityMod?,
    activeRulesets: List<CommunityMod>,
    bonusDamagePct: Int,
    bonusSpeedPct: Int,
    currentHpOverride: Int?,
    private val onSlashSfx: () -> Unit,
    private val onDashSfx: () -> Unit,
    private val onHitSfx: () -> Unit
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
        enemyHpScale = hpMult
        playerSpeedScale = spdMult
        fogMultiplier = fogMult
    }

    private val maxHp = archetype.baseHp + (profile.obsidianWardRank.toInt() * 18)
    private val baseDamage = (
        (archetype.baseDamage +
            (equippedWeapon?.powerValue?.toInt()?.div(2) ?: 0) +
            (profile.crimsonMightRank.toInt() * 4)) *
            (1f + bonusDamagePct / 100f)
        ).toInt().coerceAtLeast(10)

    private val cooldownMultiplier = (1f - profile.forbiddenArcanaRank.toInt() * 0.05f).coerceAtLeast(0.55f)
    private val attackSpeedMultiplier = (equippedWeapon?.speedValue?.toFloat() ?: 100f) / 100f
    private val weaponHex = equippedWeapon?.accentHex ?: archetype.accentHex
    private val weaponTag = equippedWeapon?.effectTag ?: "VAMPIRIC"
    private val relicTag = equippedRelic?.effectTag ?: "DASH_NOVA"

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
        weaponAccentHex = weaponHex,
        weaponEffectTag = weaponTag,
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
        floatingNumbers = emptyList(),
        soulOrbs = emptyList(),
        particles = List(28) {
            AshParticle(
                x = Random.nextFloat() * ArenaBounds.SIZE,
                y = Random.nextFloat() * ArenaBounds.SIZE,
                vy = -(15f + Random.nextFloat() * 35f),
                alpha = 0.2f + Random.nextFloat() * 0.55f,
                size = 2f + Random.nextFloat() * 3.5f
            )
        },
        remoteAllies = emptyList(),
        killsThisRun = 0,
        soulEarnedThisRun = 0,
        stageCleared = false,
        playerDead = false
    )

    private var primaryAttackCooldown = 0f
    private var moveX = 0f
    private var moveY = 0f

    fun snapshot(): CombatSnapshot = state

    fun setMoveInput(dx: Float, dy: Float) {
        moveX = dx.coerceIn(-1f, 1f)
        moveY = dy.coerceIn(-1f, 1f)
        if (hypot(moveX, moveY) > 0.15f) {
            state = state.copy(facingAngle = atan2(moveY, moveX))
        }
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

    fun triggerPrimaryAttack() {
        if (primaryAttackCooldown > 0f || state.playerDead) return
        primaryAttackCooldown = (0.36f / attackSpeedMultiplier).coerceIn(0.16f, 0.6f)
        onSlashSfx()

        val angle = state.facingAngle
        if (archetype == HeroArchetype.NECROMANCER) {
            val proj = Projectile(
                id = nextId++,
                x = state.playerX + cos(angle) * 26f,
                y = state.playerY + sin(angle) * 26f,
                vx = cos(angle) * 520f,
                vy = sin(angle) * 520f,
                radius = 12f,
                damage = baseDamage,
                fromPlayer = true,
                colorHex = weaponHex,
                ttl = 1.4f
            )
            state = state.copy(projectiles = state.projectiles + proj)
        } else {
            val reach = if (archetype == HeroArchetype.BLOODBLADE) 128f else 98f
            val slash = SlashArc(
                x = state.playerX + cos(angle) * 32f,
                y = state.playerY + sin(angle) * 32f,
                angle = angle,
                radius = reach,
                colorHex = weaponHex,
                ttl = 0.18f,
                maxTtl = 0.18f
            )
            state = state.copy(slashes = state.slashes + slash)
            damageEnemiesInCone(state.playerX, state.playerY, angle, reach, baseDamage, 1.35f)
        }
    }

    fun triggerDash() {
        if (state.dashCooldown > 0f || state.playerDead) return
        onDashSfx()
        val angle = state.facingAngle
        val dashDist = 145f
        val targetX = (state.playerX + cos(angle) * dashDist)
            .coerceIn(ArenaBounds.WALL_MARGIN, ArenaBounds.SIZE - ArenaBounds.WALL_MARGIN)
        val targetY = (state.playerY + sin(angle) * dashDist)
            .coerceIn(ArenaBounds.WALL_MARGIN, ArenaBounds.SIZE - ArenaBounds.WALL_MARGIN)

        state = state.copy(
            playerX = targetX,
            playerY = targetY,
            dashCooldown = 1.6f * cooldownMultiplier,
            invulnTimer = 0.35f
        )

        if (relicTag == "DASH_NOVA" || relicTag == "FROSTBITE") {
            val nova = SlashArc(targetX, targetY, 0f, 110f, "#D4A359", 0.24f, 0.24f)
            state = state.copy(slashes = state.slashes + nova)
            damageEnemiesInRadius(targetX, targetY, 115f, (baseDamage * 0.75f).toInt())
        }
    }

    fun triggerSkill1() {
        if (state.skill1Cooldown > 0f || state.playerDead) return
        onSlashSfx()
        state = state.copy(skill1Cooldown = 4.0f * cooldownMultiplier)
        val angle = state.facingAngle
        when (archetype) {
            HeroArchetype.BLOODBLADE -> {
                val arc = SlashArc(state.playerX, state.playerY, angle, 155f, "#D94356", 0.28f, 0.28f)
                state = state.copy(slashes = state.slashes + arc)
                damageEnemiesInRadius(state.playerX, state.playerY, 160f, (baseDamage * 1.45f).toInt())
            }
            HeroArchetype.NECROMANCER -> {
                val burst = (0 until 6).map { idx ->
                    val a = angle + (idx - 2.5f) * 0.22f
                    Projectile(
                        id = nextId++,
                        x = state.playerX,
                        y = state.playerY,
                        vx = cos(a) * 440f,
                        vy = sin(a) * 440f,
                        radius = 11f,
                        damage = (baseDamage * 0.9f).toInt(),
                        fromPlayer = true,
                        colorHex = "#4EA8A8",
                        ttl = 1.3f
                    )
                }
                state = state.copy(projectiles = state.projectiles + burst)
            }
            HeroArchetype.STALKER -> {
                val fan = (-2..2).map { offset ->
                    val a = angle + offset * 0.16f
                    Projectile(
                        id = nextId++,
                        x = state.playerX,
                        y = state.playerY,
                        vx = cos(a) * 580f,
                        vy = sin(a) * 580f,
                        radius = 9f,
                        damage = (baseDamage * 0.95f).toInt(),
                        fromPlayer = true,
                        colorHex = "#D4A359",
                        ttl = 1.0f
                    )
                }
                state = state.copy(projectiles = state.projectiles + fan)
            }
        }
    }

    fun triggerSkill2() {
        if (state.skill2Cooldown > 0f || state.playerDead) return
        onSlashSfx()
        state = state.copy(skill2Cooldown = 7.5f * cooldownMultiplier)
        val ultRadius = 210f
        val ult = SlashArc(state.playerX, state.playerY, state.facingAngle, ultRadius, "#F0C674", 0.35f, 0.35f)
        state = state.copy(slashes = state.slashes + ult)
        val dealt = damageEnemiesInRadius(state.playerX, state.playerY, ultRadius, (baseDamage * 2.2f).toInt())
        if (dealt > 0 && (archetype == HeroArchetype.BLOODBLADE || weaponTag == "VAMPIRIC")) {
            val heal = (maxHp * 0.10f).toInt().coerceAtLeast(6)
            state = state.copy(
                hp = (state.hp + heal).coerceAtMost(maxHp),
                floatingNumbers = state.floatingNumbers + FloatingNumber(
                    nextId++, state.playerX, state.playerY - 30f, "+$heal", "#4EA8A8", 0.9f
                )
            )
        }
    }

    fun step(dtRaw: Float) {
        if (state.playerDead || state.stageCleared) return
        val dt = dtRaw.coerceIn(0.001f, 0.05f)

        primaryAttackCooldown = (primaryAttackCooldown - dt).coerceAtLeast(0f)

        // Move player
        val speed = archetype.baseSpeed * playerSpeedScale
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
        var currentEnemies = state.enemies.toMutableList()
        var currentHp = state.hp
        var invuln = (state.invulnTimer - dt).coerceAtLeast(0f)
        var hitFlash = (state.hitFlashTimer - dt).coerceAtLeast(0f)
        val floats = state.floatingNumbers
            .mapNotNull { fn -> if (fn.ttl - dt <= 0f) null else fn.copy(y = fn.y - 28f * dt, ttl = fn.ttl - dt) }
            .toMutableList()
        val orbs = state.soulOrbs.toMutableList()
        var kills = state.killsThisRun
        var souls = state.soulEarnedThisRun

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
                        val crit = if (weaponTag == "VOID_CRIT" && Random.nextFloat() < 0.28f) 1.6f else 1f
                        val dmg = (proj.damage * crit).toInt()
                        val newEnHp = en.hp - dmg
                        onHitSfx()
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
                            orbs.add(SoulOrb(nextId++, en.x, en.y, if (en.isBoss) 45 else 12))
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
            val dx = px - en.x
            val dy = py - en.y
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            val dirX = dx / dist
            val dirY = dy / dist
            val slowFactor = if (relicTag == "FROSTBITE" && dist < 170f) 0.75f else 1f
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
                    }
                    "BOSS_WARDEN" -> {
                        // Radial 8-bolt gothic barrage
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
                    }
                    else -> {
                        if (dist <= en.radius + 26f && invuln <= 0f) {
                            currentHp = (currentHp - en.damage).coerceAtLeast(0)
                            invuln = 0.55f
                            hitFlash = 0.25f
                            onHitSfx()
                            floats.add(FloatingNumber(nextId++, px, py - 24f, "-${en.damage}", "#D94356", 0.8f))
                            atkCd = 1.15f
                        }
                    }
                }
            }

            updatedEnemies.add(
                en.copy(
                    x = ex, y = ey,
                    attackCooldown = atkCd,
                    telegraphTimer = tele,
                    hitFlashTimer = (en.hitFlashTimer - dt).coerceAtLeast(0f)
                )
            )
        }

        // Collect nearby Soul Orbs
        val remainingOrbs = mutableListOf<SoulOrb>()
        for (orb in orbs) {
            val d = hypot(px - orb.x, py - orb.y)
            if (d < 46f) {
                souls += orb.value
                floats.add(FloatingNumber(nextId++, px, py - 34f, "+${orb.value} Soul", "#4EA8A8", 0.85f))
            } else if (d < 165f) {
                val pull = 280f * dt / d.coerceAtLeast(1f)
                remainingOrbs.add(orb.copy(x = orb.x + (px - orb.x) * pull, y = orb.y + (py - orb.y) * pull))
            } else {
                remainingOrbs.add(orb)
            }
        }

        val slashes = state.slashes.mapNotNull { s ->
            if (s.ttl - dt <= 0f) null else s.copy(ttl = s.ttl - dt)
        }

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
            projectiles = survivingProjectiles,
            slashes = slashes,
            floatingNumbers = floats,
            soulOrbs = remainingOrbs,
            particles = particles,
            killsThisRun = kills,
            soulEarnedThisRun = souls,
            stageCleared = updatedEnemies.isEmpty() && currentHp > 0,
            playerDead = currentHp <= 0,
            elapsedTime = state.elapsedTime + dt
        )
    }

    private fun damageEnemiesInCone(
        ox: Float, oy: Float, facing: Float, reach: Float, dmg: Int, halfArcRad: Float
    ): Int {
        var hits = 0
        val remaining = mutableListOf<ActiveEnemy>()
        val floats = state.floatingNumbers.toMutableList()
        val orbs = state.soulOrbs.toMutableList()
        var kills = state.killsThisRun
        var hp = state.hp

        for (en in state.enemies) {
            val dx = en.x - ox
            val dy = en.y - oy
            val dist = hypot(dx, dy)
            val angleToEn = atan2(dy, dx)
            val diff = kotlin.math.abs(atan2(sin(angleToEn - facing), cos(angleToEn - facing)))
            if (dist <= reach + en.radius && diff <= halfArcRad) {
                hits++
                val isCrit = weaponTag == "VOID_CRIT" && Random.nextFloat() < 0.30f
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
                if (weaponTag == "VAMPIRIC" && Random.nextFloat() < 0.25f) {
                    hp = (hp + 4).coerceAtMost(maxHp)
                }
                if (newHp <= 0) {
                    kills++
                    orbs.add(SoulOrb(nextId++, en.x, en.y, if (en.isBoss) 45 else 12))
                } else {
                    remaining.add(en.copy(hp = newHp, hitFlashTimer = 0.15f))
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
            stageCleared = remaining.isEmpty() && hp > 0
        )
        return hits
    }

    private fun damageEnemiesInRadius(cx: Float, cy: Float, radius: Float, dmg: Int): Int {
        var hits = 0
        val remaining = mutableListOf<ActiveEnemy>()
        val floats = state.floatingNumbers.toMutableList()
        val orbs = state.soulOrbs.toMutableList()
        var kills = state.killsThisRun

        for (en in state.enemies) {
            if (hypot(en.x - cx, en.y - cy) <= radius + en.radius) {
                hits++
                val newHp = en.hp - dmg
                onHitSfx()
                floats.add(FloatingNumber(nextId++, en.x, en.y - en.radius, "$dmg", "#F0C674", 0.75f))
                if (newHp <= 0) {
                    kills++
                    orbs.add(SoulOrb(nextId++, en.x, en.y, if (en.isBoss) 45 else 12))
                } else {
                    remaining.add(en.copy(hp = newHp, hitFlashTimer = 0.16f))
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
