package com.example.ui.combat

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import com.example.data.model.HeroArchetype
import com.example.data.model.RaidPlayerState
import com.example.data.model.StageObstacle
import com.example.game.ActiveEnemy
import com.example.game.ArenaBounds
import com.example.game.CombatSnapshot
import com.example.ui.theme.AbyssalObsidian
import com.example.ui.theme.CarvedStoneBorder
import com.example.ui.theme.CrimsonBlood
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CryptSlate
import com.example.ui.theme.EmberGold
import com.example.ui.theme.SpectralTeal
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal fun parseHexSafe(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

private sealed class DepthDrawable(val sortY: Float) {
    class ObstacleItem(val obs: StageObstacle) : DepthDrawable(obs.yRatio * ArenaBounds.SIZE)
    class EnemyItem(val enemy: ActiveEnemy) : DepthDrawable(enemy.y)
    class AllyItem(val ally: RaidPlayerState) : DepthDrawable(ally.posY.toFloat())
    class HeroItem(sortY: Float) : DepthDrawable(sortY)
}

internal fun DrawScope.renderDetailedArena(
    snapshot: CombatSnapshot,
    ambientColor: Color,
    torchColor: Color
) {
    val scale = minOf(size.width, size.height) / ArenaBounds.SIZE
    val offsetX = (size.width - ArenaBounds.SIZE * scale) * 0.5f
    val offsetY = (size.height - ArenaBounds.SIZE * scale) * 0.5f
    fun toScreen(vx: Float, vy: Float) = Offset(offsetX + vx * scale, offsetY + vy * scale)

    val flicker = 0.90f + 0.10f * sin(snapshot.elapsedTime * 8.4f) + 0.04f * cos(snapshot.elapsedTime * 15.3f)

    // 1. Stone chamber floor + 2.5D perimeter walls & carved tiles
    drawGothicFloorAndWalls(
        snapshot = snapshot,
        ambientColor = ambientColor,
        torchColor = torchColor,
        flicker = flicker,
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY,
        toScreen = ::toScreen
    )

    // 2. Portal gate (ground layer when cleared)
    if (snapshot.stageCleared) {
        drawAbyssalPortal(
            portalCenter = toScreen(500f, 190f),
            heroPos = toScreen(snapshot.playerX, snapshot.playerY),
            elapsedTime = snapshot.elapsedTime,
            scale = scale
        )
    }

    // 3. Soul Orbs on floor
    snapshot.soulOrbs.forEach { orb ->
        val sp = toScreen(orb.x, orb.y)
        val bob = sin(snapshot.elapsedTime * 5f + orb.id) * 3.5f * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SpectralTeal.copy(alpha = 0.52f), Color.Transparent),
                center = Offset(sp.x, sp.y + bob),
                radius = 22f * scale
            ),
            radius = 22f * scale,
            center = Offset(sp.x, sp.y + bob)
        )
        drawCircle(color = SpectralTeal, radius = 8.5f * scale, center = Offset(sp.x, sp.y + bob))
        drawCircle(color = Color.White, radius = 3.5f * scale, center = Offset(sp.x, sp.y + bob - 1.5f * scale))
    }

    // 4. Dash Ghost Afterimages (humanoid silhouette trail!)
    snapshot.dashGhosts.forEach { ghost ->
        val sp = toScreen(ghost.x, ghost.y)
        val gColor = parseHexSafe(ghost.colorHex, EmberGold)
        val alpha = (ghost.ttl / ghost.maxTtl).coerceIn(0f, 1f) * 0.48f
        drawOval(
            color = gColor.copy(alpha = alpha),
            topLeft = Offset(sp.x - 14f * scale, sp.y - 34f * scale),
            size = Size(28f * scale, 46f * scale)
        )
        drawCircle(color = gColor.copy(alpha = alpha), radius = 9f * scale, center = Offset(sp.x, sp.y - 36f * scale))
    }

    // 5. Expanding Shockwave Rings
    snapshot.rings.forEach { ring ->
        val sp = toScreen(ring.x, ring.y)
        val c = parseHexSafe(ring.colorHex, EmberGold)
        val progress = 1f - (ring.ttl / ring.maxTtl).coerceIn(0f, 1f)
        val curR = (ring.maxRadius * (0.25f + 0.75f * progress)) * scale
        val alpha = (1f - progress).coerceIn(0f, 1f)
        drawCircle(color = c.copy(alpha = alpha * 0.75f), radius = curR, center = sp, style = Stroke(width = 8.5f * scale))
        drawCircle(color = Color.White.copy(alpha = alpha * 0.58f), radius = curR * 0.93f, center = sp, style = Stroke(width = 2.5f * scale))
    }

    // 6. Y-Sorted 2.5D Entities (Obstacles, Enemies, Allies, Hero)
    val depthList = buildList {
        snapshot.stage.obstacles.forEach { add(DepthDrawable.ObstacleItem(it)) }
        snapshot.enemies.forEach { add(DepthDrawable.EnemyItem(it)) }
        snapshot.remoteAllies.forEach { add(DepthDrawable.AllyItem(it)) }
        add(DepthDrawable.HeroItem(snapshot.playerY))
    }.sortedBy { it.sortY }

    depthList.forEach { item ->
        when (item) {
            is DepthDrawable.ObstacleItem -> {
                val obs = item.obs
                drawDetailedObstacle(
                    obs = obs,
                    sp = toScreen(obs.xRatio * ArenaBounds.SIZE, obs.yRatio * ArenaBounds.SIZE),
                    flicker = flicker,
                    elapsedTime = snapshot.elapsedTime,
                    scale = scale
                )
            }
            is DepthDrawable.EnemyItem -> {
                val en = item.enemy
                drawDetailedEnemy(
                    en = en,
                    sp = toScreen(en.x, en.y),
                    elapsedTime = snapshot.elapsedTime,
                    scale = scale
                )
            }
            is DepthDrawable.AllyItem -> {
                val ally = item.ally
                drawRemoteHumanoidAlly(
                    ally = ally,
                    sp = toScreen(ally.posX.toFloat(), ally.posY.toFloat()),
                    elapsedTime = snapshot.elapsedTime,
                    scale = scale
                )
            }
            is DepthDrawable.HeroItem -> {
                drawDetailedHero(
                    snapshot = snapshot,
                    sp = toScreen(snapshot.playerX, snapshot.playerY),
                    scale = scale
                )
            }
        }
    }

    // 7. Tapered Multi-Pass Slash Arcs
    snapshot.slashes.forEach { s ->
        val sp = toScreen(s.x, s.y)
        val c = parseHexSafe(s.colorHex, EmberGold)
        val alpha = (s.ttl / s.maxTtl).coerceIn(0f, 1f)
        val startDeg = Math.toDegrees(s.angle.toDouble()).toFloat() - s.sweepDeg / 2f
        drawArc(
            color = c.copy(alpha = alpha * 0.45f),
            startAngle = startDeg - 4f,
            sweepAngle = s.sweepDeg + 8f,
            useCenter = false,
            topLeft = Offset(sp.x - s.radius * scale, sp.y - s.radius * scale),
            size = Size(s.radius * 2f * scale, s.radius * 2f * scale),
            style = Stroke(width = 26f * scale, cap = StrokeCap.Round)
        )
        drawArc(
            color = c.copy(alpha = alpha * 0.9f),
            startAngle = startDeg,
            sweepAngle = s.sweepDeg,
            useCenter = false,
            topLeft = Offset(sp.x - s.radius * scale, sp.y - s.radius * scale),
            size = Size(s.radius * 2f * scale, s.radius * 2f * scale),
            style = Stroke(width = 14f * scale, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color.White.copy(alpha = alpha * 0.95f),
            startAngle = startDeg + 8f,
            sweepAngle = (s.sweepDeg - 16f).coerceAtLeast(12f),
            useCenter = false,
            topLeft = Offset(sp.x - s.radius * scale, sp.y - s.radius * scale),
            size = Size(s.radius * 2f * scale, s.radius * 2f * scale),
            style = Stroke(width = 4.5f * scale, cap = StrokeCap.Round)
        )
    }

    // 8. Projectiles with Glowing Trails
    snapshot.projectiles.forEach { p ->
        val sp = toScreen(p.x, p.y)
        val c = parseHexSafe(p.colorHex, SpectralTeal)
        val tailEnd = Offset(sp.x - p.vx * 0.038f * scale, sp.y - p.vy * 0.038f * scale)
        drawLine(color = c.copy(alpha = 0.52f), start = sp, end = tailEnd, strokeWidth = p.radius * 1.45f * scale, cap = StrokeCap.Round)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c.copy(alpha = 0.55f), Color.Transparent),
                center = sp,
                radius = p.radius * 2.5f * scale
            ),
            radius = p.radius * 2.5f * scale,
            center = sp
        )
        drawCircle(color = c, radius = p.radius * scale, center = sp)
        drawCircle(color = Color.White, radius = p.radius * 0.48f * scale, center = sp)
    }

    // 9. Atmospheric Floating Ember Motes
    snapshot.particles.forEach { pt ->
        val sp = toScreen(pt.x, pt.y)
        drawCircle(
            color = torchColor.copy(alpha = pt.alpha * 0.85f),
            radius = pt.size * scale,
            center = sp
        )
    }

    // 10. Floating Damage / Heal Text
    snapshot.floatingNumbers.forEach { fn ->
        val sp = toScreen(fn.x, fn.y)
        drawContext.canvas.nativeCanvas.drawText(
            fn.text,
            sp.x,
            sp.y - 24f * scale,
            android.graphics.Paint().apply {
                color = try {
                    android.graphics.Color.parseColor(fn.colorHex)
                } catch (_: Exception) {
                    android.graphics.Color.WHITE
                }
                textSize = 31f * scale
                isFakeBoldText = true
                setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
                textAlign = android.graphics.Paint.Align.CENTER
            }
        )
    }

    // 11. Vignette & Carved Border
    val center = toScreen(500f, 500f)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Transparent, AbyssalObsidian.copy(alpha = 0.62f)),
            center = center,
            radius = ArenaBounds.SIZE * 0.72f * scale
        ),
        topLeft = Offset(offsetX, offsetY),
        size = Size(ArenaBounds.SIZE * scale, ArenaBounds.SIZE * scale)
    )
    drawRect(
        color = CarvedStoneBorder,
        topLeft = Offset(offsetX, offsetY),
        size = Size(ArenaBounds.SIZE * scale, ArenaBounds.SIZE * scale),
        style = Stroke(width = 6f)
    )
}

private fun DrawScope.drawGothicFloorAndWalls(
    snapshot: CombatSnapshot,
    ambientColor: Color,
    torchColor: Color,
    flicker: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    toScreen: (Float, Float) -> Offset
) {
    // Base stone floor
    drawRect(
        color = ambientColor,
        topLeft = Offset(offsetX, offsetY),
        size = Size(ArenaBounds.SIZE * scale, ArenaBounds.SIZE * scale)
    )

    // Beveled carved stone flagstones with weathered cracks
    val tileSize = 100f * scale
    for (row in 0 until 10) {
        for (col in 0 until 10) {
            val tx = offsetX + col * tileSize
            val ty = offsetY + row * tileSize
            val shade = if ((row + col) % 2 == 0) Color.White.copy(alpha = 0.032f) else Color.Black.copy(alpha = 0.09f)
            drawRect(
                color = shade,
                topLeft = Offset(tx + 2f, ty + 2f),
                size = Size(tileSize - 4f, tileSize - 4f)
            )
            // Subtle bevel top highlight
            drawLine(
                color = Color.White.copy(alpha = 0.045f),
                start = Offset(tx + 2f, ty + 2f),
                end = Offset(tx + tileSize - 4f, ty + 2f),
                strokeWidth = 1.4f
            )
            // Deterministic stone cracks on some tiles
            if ((row * 7 + col * 13) % 5 == 0) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.28f),
                    start = Offset(tx + tileSize * 0.25f, ty + tileSize * 0.35f),
                    end = Offset(tx + tileSize * 0.65f, ty + tileSize * 0.62f),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.22f),
                    start = Offset(tx + tileSize * 0.50f, ty + tileSize * 0.52f),
                    end = Offset(tx + tileSize * 0.35f, ty + tileSize * 0.80f),
                    strokeWidth = 1.2f
                )
            }
        }
    }

    // Perimeter 2.5D Dungeon Wall Strip along the top edge with Gothic Archways
    val wallH = 44f * scale
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1F1A2C), Color(0xFF120F1B)),
            startY = offsetY,
            endY = offsetY + wallH
        ),
        topLeft = Offset(offsetX, offsetY),
        size = Size(ArenaBounds.SIZE * scale, wallH)
    )
    // Arch alcoves along north wall
    for (i in 1..4) {
        val ax = offsetX + i * 200f * scale
        drawRoundRect(
            color = Color(0xFF0A0810),
            topLeft = Offset(ax - 22f * scale, offsetY + 8f * scale),
            size = Size(44f * scale, 34f * scale),
            cornerRadius = CornerRadius(22f * scale, 22f * scale)
        )
        drawRoundRect(
            color = CarvedStoneBorder,
            topLeft = Offset(ax - 22f * scale, offsetY + 8f * scale),
            size = Size(44f * scale, 34f * scale),
            cornerRadius = CornerRadius(22f * scale, 22f * scale),
            style = Stroke(width = 2f)
        )
    }
    drawLine(
        color = CarvedStoneBorder,
        start = Offset(offsetX, offsetY + wallH),
        end = Offset(offsetX + ArenaBounds.SIZE * scale, offsetY + wallH),
        strokeWidth = 3f
    )

    // Intricate Rotating Arcane Ritual Circle in center
    val center = toScreen(500f, 500f)
    drawCircle(color = torchColor.copy(alpha = 0.16f), radius = 215f * scale, center = center, style = Stroke(width = 4f))
    drawCircle(color = torchColor.copy(alpha = 0.11f), radius = 152f * scale, center = center, style = Stroke(width = 2f))
    drawCircle(color = torchColor.copy(alpha = 0.08f), radius = 84f * scale, center = center, style = Stroke(width = 2f))
    val rot = snapshot.elapsedTime * 0.24f
    val sigilR = 205f * scale
    val diamondPath = Path().apply {
        for (k in 0..3) {
            val a1 = rot + k * (PI / 2.0).toFloat()
            val a2 = rot + (k + 1) * (PI / 2.0).toFloat()
            moveTo(center.x + cos(a1) * sigilR, center.y + sin(a1) * sigilR)
            lineTo(center.x + cos(a2) * sigilR, center.y + sin(a2) * sigilR)
        }
        for (k in 0..3) {
            val a1 = rot + (PI / 4.0).toFloat() + k * (PI / 2.0).toFloat()
            val a2 = rot + (PI / 4.0).toFloat() + (k + 1) * (PI / 2.0).toFloat()
            moveTo(center.x + cos(a1) * sigilR, center.y + sin(a1) * sigilR)
            lineTo(center.x + cos(a2) * sigilR, center.y + sin(a2) * sigilR)
        }
    }
    drawPath(diamondPath, color = torchColor.copy(alpha = 0.13f), style = Stroke(width = 1.8f))

    // 4-Corner Flickering Wall Sconces / Torches
    val torchPositions = listOf(
        Offset(88f, 88f), Offset(912f, 88f),
        Offset(88f, 912f), Offset(912f, 912f)
    )
    torchPositions.forEachIndexed { idx, tp ->
        val sp = toScreen(tp.x, tp.y)
        val localFlicker = flicker * (0.94f + 0.06f * sin(snapshot.elapsedTime * 10f + idx))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(torchColor.copy(alpha = 0.42f * localFlicker), Color.Transparent),
                center = sp,
                radius = 250f * scale * localFlicker
            ),
            radius = 250f * scale * localFlicker,
            center = sp
        )
        drawCircle(color = Color(0xFF2F283E), radius = 14f * scale, center = sp)
        drawCircle(color = Color(0xFFE06B38), radius = 9.5f * scale * localFlicker, center = sp)
        drawCircle(color = Color(0xFFFFE194), radius = 4.5f * scale, center = sp)
    }
}

private fun DrawScope.drawDetailedObstacle(
    obs: StageObstacle,
    sp: Offset,
    flicker: Float,
    elapsedTime: Float,
    scale: Float
) {
    val r = obs.radius * scale
    // Cast elongated shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.56f),
        topLeft = Offset(sp.x - r * 0.9f, sp.y - r * 0.15f),
        size = Size(r * 2.15f, r * 1.15f)
    )

    when (obs.kind) {
        "BRAZIER" -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(EmberGold.copy(alpha = 0.36f * flicker), Color.Transparent),
                    center = Offset(sp.x, sp.y - r * 0.45f),
                    radius = r * 3.1f * flicker
                ),
                radius = r * 3.1f * flicker,
                center = Offset(sp.x, sp.y - r * 0.45f)
            )
            // Iron pedestal + bowl
            drawRoundRect(
                color = Color(0xFF282234),
                topLeft = Offset(sp.x - r * 0.65f, sp.y - r * 0.15f),
                size = Size(r * 1.3f, r * 0.6f),
                cornerRadius = CornerRadius(6f * scale)
            )
            drawCircle(color = Color(0xFF3A304A), radius = r * 0.85f, center = Offset(sp.x, sp.y - r * 0.25f))
            drawCircle(color = EmberGold.copy(alpha = 0.7f), radius = r * 0.85f, center = Offset(sp.x, sp.y - r * 0.25f), style = Stroke(width = 2.5f))
            // Animated fire tongues
            val sway = sin(elapsedTime * 11f + obs.xRatio * 10f) * r * 0.15f
            val flameOuter = Path().apply {
                moveTo(sp.x - r * 0.52f, sp.y - r * 0.25f)
                quadraticTo(sp.x - r * 0.2f, sp.y - r * 1.05f, sp.x + sway, sp.y - r * 1.35f * flicker)
                quadraticTo(sp.x + r * 0.3f, sp.y - r * 0.95f, sp.x + r * 0.52f, sp.y - r * 0.25f)
                close()
            }
            drawPath(flameOuter, color = Color(0xFFE8612C))
            val flameCore = Path().apply {
                moveTo(sp.x - r * 0.28f, sp.y - r * 0.25f)
                lineTo(sp.x + sway * 0.5f, sp.y - r * 0.9f * flicker)
                lineTo(sp.x + r * 0.28f, sp.y - r * 0.25f)
                close()
            }
            drawPath(flameCore, color = Color(0xFFFFE599))
        }
        "ALTAR" -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SpectralTeal.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(sp.x, sp.y - r * 0.3f),
                    radius = r * 2.6f
                ),
                radius = r * 2.6f,
                center = Offset(sp.x, sp.y - r * 0.3f)
            )
            // Stepped stone altar dais + crimson cloth runner
            drawRoundRect(
                color = Color(0xFF2B253A),
                topLeft = Offset(sp.x - r, sp.y - r * 0.35f),
                size = Size(r * 2f, r * 0.9f),
                cornerRadius = CornerRadius(4f * scale)
            )
            drawRoundRect(
                color = Color(0xFF3E3552),
                topLeft = Offset(sp.x - r * 0.88f, sp.y - r * 0.72f),
                size = Size(r * 1.76f, r * 0.75f),
                cornerRadius = CornerRadius(4f * scale)
            )
            drawRect(
                color = CrimsonBlood,
                topLeft = Offset(sp.x - r * 0.32f, sp.y - r * 0.72f),
                size = Size(r * 0.64f, r * 1.15f)
            )
            // Floating glowing soul crystal
            val floatY = sin(elapsedTime * 3.5f) * 4f * scale
            val crystal = Path().apply {
                moveTo(sp.x, sp.y - r * 1.35f + floatY)
                lineTo(sp.x + r * 0.28f, sp.y - r * 0.92f + floatY)
                lineTo(sp.x, sp.y - r * 0.55f + floatY)
                lineTo(sp.x - r * 0.28f, sp.y - r * 0.92f + floatY)
                close()
            }
            drawPath(crystal, color = SpectralTeal)
            drawPath(crystal, color = Color.White.copy(alpha = 0.75f), style = Stroke(width = 1.8f))
        }
        "STATUE" -> {
            // Plinth base
            drawRoundRect(
                color = Color(0xFF282235),
                topLeft = Offset(sp.x - r * 0.85f, sp.y - r * 0.1f),
                size = Size(r * 1.7f, r * 0.75f),
                cornerRadius = CornerRadius(4f * scale)
            )
            // Cloaked knight / angel stone body
            val body = Path().apply {
                moveTo(sp.x - r * 0.65f, sp.y + r * 0.15f)
                lineTo(sp.x - r * 0.42f, sp.y - r * 0.95f)
                lineTo(sp.x + r * 0.42f, sp.y - r * 0.95f)
                lineTo(sp.x + r * 0.65f, sp.y + r * 0.15f)
                close()
            }
            drawPath(body, color = Color(0xFF423A54))
            drawPath(body, color = Color(0xFF5F5475), style = Stroke(width = 2f))
            // Stone wings
            val wings = Path().apply {
                moveTo(sp.x - r * 0.3f, sp.y - r * 0.7f)
                lineTo(sp.x - r * 1.15f, sp.y - r * 1.25f)
                lineTo(sp.x - r * 0.6f, sp.y - r * 0.2f)
                moveTo(sp.x + r * 0.3f, sp.y - r * 0.7f)
                lineTo(sp.x + r * 1.15f, sp.y - r * 1.25f)
                lineTo(sp.x + r * 0.6f, sp.y - r * 0.2f)
            }
            drawPath(wings, color = Color(0xFF362F47), style = Stroke(width = 5f * scale, cap = StrokeCap.Round))
            // Hooded statue head + glowing crimson eyes
            drawCircle(color = Color(0xFF4E4563), radius = r * 0.34f, center = Offset(sp.x, sp.y - r * 1.1f))
            drawCircle(color = CrimsonBright, radius = 2.6f * scale, center = Offset(sp.x - r * 0.1f, sp.y - r * 1.08f))
            drawCircle(color = CrimsonBright, radius = 2.6f * scale, center = Offset(sp.x + r * 0.1f, sp.y - r * 1.08f))
        }
        else -> {
            // 2.5D Fluted Gothic Stone Pillar
            drawRoundRect(
                color = Color(0xFF251F33),
                topLeft = Offset(sp.x - r * 0.95f, sp.y - r * 0.15f),
                size = Size(r * 1.9f, r * 0.85f),
                cornerRadius = CornerRadius(5f * scale)
            )
            // Vertical pillar column body
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF241E30), Color(0xFF483F5C), Color(0xFF211C2D)),
                    startX = sp.x - r * 0.72f,
                    endX = sp.x + r * 0.72f
                ),
                topLeft = Offset(sp.x - r * 0.72f, sp.y - r * 1.4f),
                size = Size(r * 1.44f, r * 1.65f),
                cornerRadius = CornerRadius(6f * scale)
            )
            // Pillar top capital
            drawRoundRect(
                color = Color(0xFF4B4261),
                topLeft = Offset(sp.x - r * 0.86f, sp.y - r * 1.58f),
                size = Size(r * 1.72f, r * 0.44f),
                cornerRadius = CornerRadius(4f * scale)
            )
            drawRoundRect(
                color = EmberGold.copy(alpha = 0.32f),
                topLeft = Offset(sp.x - r * 0.86f, sp.y - r * 1.58f),
                size = Size(r * 1.72f, r * 0.44f),
                cornerRadius = CornerRadius(4f * scale),
                style = Stroke(width = 2f)
            )
        }
    }
}

private fun DrawScope.drawAbyssalPortal(
    portalCenter: Offset,
    heroPos: Offset,
    elapsedTime: Float,
    scale: Float
) {
    val pulse = 48f + 7f * sin(elapsedTime * 4.5f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(SpectralTeal.copy(alpha = 0.92f), Color(0xFF1F4E5A).copy(alpha = 0.35f), Color.Transparent),
            center = portalCenter,
            radius = pulse * 2.25f * scale
        ),
        radius = pulse * 2.25f * scale,
        center = portalCenter
    )
    drawCircle(color = SpectralTeal, radius = pulse * scale, center = portalCenter, style = Stroke(width = 4.5f))
    drawCircle(color = Color.White.copy(alpha = 0.82f), radius = pulse * 0.62f * scale, center = portalCenter, style = Stroke(width = 2.5f))

    // Rotating arcane portal spokes
    val rot = elapsedTime * 2.4f
    for (k in 0..5) {
        val a = rot + k * (PI / 3.0).toFloat()
        drawLine(
            color = SpectralTeal.copy(alpha = 0.7f),
            start = Offset(portalCenter.x + cos(a) * pulse * 0.3f * scale, portalCenter.y + sin(a) * pulse * 0.3f * scale),
            end = Offset(portalCenter.x + cos(a) * pulse * 0.95f * scale, portalCenter.y + sin(a) * pulse * 0.95f * scale),
            strokeWidth = 2.5f * scale,
            cap = StrokeCap.Round
        )
    }

    // Pulsing guide chevron above hero pointing toward portal
    val dx = portalCenter.x - heroPos.x
    val dy = portalCenter.y - heroPos.y
    val dist = hypot(dx, dy)
    if (dist > 80f * scale) {
        val dir = atan2(dy, dx)
        val markerCenter = Offset(heroPos.x + cos(dir) * 52f * scale, heroPos.y + sin(dir) * 52f * scale)
        drawCircle(color = SpectralTeal.copy(alpha = 0.85f), radius = 7f * scale, center = markerCenter)
        drawCircle(color = Color.White, radius = 3.2f * scale, center = markerCenter)
    }
}

private fun DrawScope.drawDetailedHero(
    snapshot: CombatSnapshot,
    sp: Offset,
    scale: Float
) {
    val accent = parseHexSafe(snapshot.weaponAccentHex, EmberGold)
    val heroTint = when {
        snapshot.hitFlashTimer > 0f -> CrimsonBright
        snapshot.invulnTimer > 0f -> Color.White.copy(alpha = 0.85f)
        else -> accent
    }

    val facing = snapshot.facingAngle
    val flip = if (cos(facing) < -0.1f) -1f else 1f
    val stride = if (snapshot.isMoving) sin(snapshot.movePhase) else 0f
    val bob = if (snapshot.isMoving) kotlin.math.abs(cos(snapshot.movePhase)) * 4.5f * scale
    else sin(snapshot.elapsedTime * 3.6f) * 1.8f * scale
    val lean = if (snapshot.isMoving) 0.14f * flip else 0f

    // Warm Lantern Aura around Hero
    val lanternRadius = 290f * scale * snapshot.fogRadiusMultiplier
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(EmberGold.copy(alpha = 0.24f), Color.Transparent),
            center = sp,
            radius = lanternRadius
        ),
        radius = lanternRadius,
        center = sp
    )

    // Ground Contact Shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.62f),
        topLeft = Offset(sp.x - 19f * scale, sp.y + 4f * scale),
        size = Size(38f * scale, 15f * scale)
    )

    // Animated Flowing Cloak trailing opposite facing direction
    val capeSway = sin(snapshot.elapsedTime * 8f + snapshot.movePhase * 0.6f) * 4.5f * scale
    val backDir = if (cos(facing) >= 0f) -1f else 1f
    val capePath = Path().apply {
        moveTo(sp.x - 4f * scale * backDir, sp.y - 26f * scale - bob)
        lineTo(sp.x + backDir * (22f * scale + kotlin.math.abs(stride) * 7f * scale), sp.y + 3f * scale + capeSway)
        lineTo(sp.x + backDir * 8f * scale, sp.y + 6f * scale - capeSway * 0.5f)
        close()
    }
    val capeColor = when (snapshot.archetype) {
        HeroArchetype.BLOODBLADE -> Color(0xFF4A121D)
        HeroArchetype.STALKER -> Color(0xFF281D3A)
        HeroArchetype.NECROMANCER -> Color(0xFF122C2F)
    }
    drawPath(capePath, color = capeColor)
    drawPath(capePath, color = heroTint.copy(alpha = 0.72f), style = Stroke(width = 1.8f))

    // Articulated Animated Legs & Boots
    val leftFootX = sp.x - 6f * scale + stride * 9f * scale * flip
    val leftFootY = sp.y + 7f * scale - (if (stride > 0f) stride * 3.5f * scale else 0f)
    val rightFootX = sp.x + 6f * scale - stride * 9f * scale * flip
    val rightFootY = sp.y + 7f * scale - (if (stride < 0f) -stride * 3.5f * scale else 0f)

    // Legs
    drawLine(
        color = Color(0xFF1B1726),
        start = Offset(sp.x - 5f * scale, sp.y - 10f * scale - bob),
        end = Offset(leftFootX, leftFootY),
        strokeWidth = 6.2f * scale,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFF282238),
        start = Offset(sp.x + 5f * scale, sp.y - 10f * scale - bob),
        end = Offset(rightFootX, rightFootY),
        strokeWidth = 6.2f * scale,
        cap = StrokeCap.Round
    )
    // Armored Boots
    drawCircle(color = Color(0xFF4A425C), radius = 4.2f * scale, center = Offset(leftFootX, leftFootY))
    drawCircle(color = Color(0xFF5D5373), radius = 4.2f * scale, center = Offset(rightFootX, rightFootY))

    // Torso & Armor Breastplate (slightly tilted when sprinting)
    val torsoTopY = sp.y - 29f * scale - bob
    val torsoBotY = sp.y - 8f * scale - bob
    rotate(degrees = Math.toDegrees(lean.toDouble()).toFloat(), pivot = Offset(sp.x, torsoBotY)) {
        // Armored Chest Plate
        val chest = Path().apply {
            moveTo(sp.x - 12f * scale, torsoTopY)
            lineTo(sp.x + 12f * scale, torsoTopY)
            lineTo(sp.x + 8.5f * scale, torsoBotY)
            lineTo(sp.x - 8.5f * scale, torsoBotY)
            close()
        }
        drawPath(chest, color = Color(0xFF2D273E))
        drawPath(chest, color = heroTint.copy(alpha = 0.85f), style = Stroke(width = 2.2f))

        // Chest emblem & leather belt
        drawRect(
            color = Color(0xFF1A1523),
            topLeft = Offset(sp.x - 9f * scale, torsoBotY - 5f * scale),
            size = Size(18f * scale, 4.5f * scale)
        )
        drawCircle(color = EmberGold, radius = 2.5f * scale, center = Offset(sp.x, torsoBotY - 2.8f * scale))

        // Pauldrons (Armored Shoulder Guards)
        drawCircle(color = Color(0xFF3E3554), radius = 6.8f * scale, center = Offset(sp.x - 12.5f * scale, torsoTopY + 3f * scale))
        drawCircle(color = heroTint, radius = 6.8f * scale, center = Offset(sp.x - 12.5f * scale, torsoTopY + 3f * scale), style = Stroke(width = 1.8f))
        drawCircle(color = Color(0xFF3E3554), radius = 6.8f * scale, center = Offset(sp.x + 12.5f * scale, torsoTopY + 3f * scale))
        drawCircle(color = heroTint, radius = 6.8f * scale, center = Offset(sp.x + 12.5f * scale, torsoTopY + 3f * scale), style = Stroke(width = 1.8f))

        // Off-hand arm swinging opposite stride
        val offArmX = sp.x - 12f * scale * flip - stride * 5f * scale
        val offArmY = torsoTopY + 14f * scale
        drawLine(
            color = Color(0xFF3A324D),
            start = Offset(sp.x - 11f * scale * flip, torsoTopY + 4f * scale),
            end = Offset(offArmX, offArmY),
            strokeWidth = 4.8f * scale,
            cap = StrokeCap.Round
        )
        drawCircle(color = heroTint, radius = 3.2f * scale, center = Offset(offArmX, offArmY))

        // Head & Archetype-Distinct Helm / Cowl
        val headCenter = Offset(sp.x, torsoTopY - 8.5f * scale)
        when (snapshot.archetype) {
            HeroArchetype.BLOODBLADE -> {
                // Dark Crimson Horned Knight Helm
                drawCircle(color = Color(0xFF282238), radius = 9.5f * scale, center = headCenter)
                drawCircle(color = CrimsonBright, radius = 9.5f * scale, center = headCenter, style = Stroke(width = 2f))
                // Horns
                drawLine(color = Color(0xFFD7CFC5), start = Offset(headCenter.x - 6f * scale, headCenter.y - 5f * scale), end = Offset(headCenter.x - 12f * scale, headCenter.y - 14f * scale), strokeWidth = 3f * scale, cap = StrokeCap.Round)
                drawLine(color = Color(0xFFD7CFC5), start = Offset(headCenter.x + 6f * scale, headCenter.y - 5f * scale), end = Offset(headCenter.x + 12f * scale, headCenter.y - 14f * scale), strokeWidth = 3f * scale, cap = StrokeCap.Round)
                // Glowing T-visor
                drawLine(color = CrimsonBright, start = Offset(headCenter.x - 4.5f * scale, headCenter.y), end = Offset(headCenter.x + 4.5f * scale, headCenter.y), strokeWidth = 2.4f * scale)
            }
            HeroArchetype.STALKER -> {
                // Pointed Assassin Hood + Glowing Eyes
                val hood = Path().apply {
                    moveTo(headCenter.x, headCenter.y - 13f * scale)
                    lineTo(headCenter.x + 10f * scale, headCenter.y + 6f * scale)
                    lineTo(headCenter.x - 10f * scale, headCenter.y + 6f * scale)
                    close()
                }
                drawPath(hood, color = Color(0xFF1D172B))
                drawPath(hood, color = heroTint, style = Stroke(width = 1.8f))
                drawCircle(color = EmberGold, radius = 2.1f * scale, center = Offset(headCenter.x + 2.5f * scale * flip - 1.5f * scale, headCenter.y))
                drawCircle(color = EmberGold, radius = 2.1f * scale, center = Offset(headCenter.x + 2.5f * scale * flip + 2.5f * scale, headCenter.y))
            }
            HeroArchetype.NECROMANCER -> {
                // Spectral Crowned Cowl
                drawCircle(color = Color(0xFF1A292E), radius = 9.5f * scale, center = headCenter)
                drawCircle(color = SpectralTeal, radius = 9.5f * scale, center = headCenter, style = Stroke(width = 2f))
                // Crown spikes
                drawLine(color = Color(0xFFDAD4C8), start = Offset(headCenter.x - 5f * scale, headCenter.y - 8f * scale), end = Offset(headCenter.x - 6f * scale, headCenter.y - 15f * scale), strokeWidth = 2.2f * scale)
                drawLine(color = Color(0xFFDAD4C8), start = Offset(headCenter.x, headCenter.y - 9f * scale), end = Offset(headCenter.x, headCenter.y - 17f * scale), strokeWidth = 2.4f * scale)
                drawLine(color = Color(0xFFDAD4C8), start = Offset(headCenter.x + 5f * scale, headCenter.y - 8f * scale), end = Offset(headCenter.x + 6f * scale, headCenter.y - 15f * scale), strokeWidth = 2.2f * scale)
                drawCircle(color = SpectralTeal, radius = 2.3f * scale, center = Offset(headCenter.x - 2.5f * scale, headCenter.y))
                drawCircle(color = SpectralTeal, radius = 2.3f * scale, center = Offset(headCenter.x + 2.5f * scale, headCenter.y))
            }
        }
    }

    // Main-Hand Arm & Articulated Weapon Swing Animation!
    val swingAngle = if (snapshot.attackAnimTimer > 0f && snapshot.attackAnimDuration > 0f) {
        val progress = 1f - (snapshot.attackAnimTimer / snapshot.attackAnimDuration).coerceIn(0f, 1f)
        facing + (-1.35f + progress * 2.75f)
    } else {
        facing + stride * 0.14f
    }

    val shoulderPos = Offset(sp.x + cos(swingAngle) * 8f * scale, sp.y - 22f * scale - bob + sin(swingAngle) * 4f * scale)
    val handPos = Offset(shoulderPos.x + cos(swingAngle) * 14f * scale, shoulderPos.y + sin(swingAngle) * 14f * scale)
    drawLine(color = Color(0xFF473E5B), start = shoulderPos, end = handPos, strokeWidth = 5f * scale, cap = StrokeCap.Round)
    drawCircle(color = heroTint, radius = 3.6f * scale, center = handPos)

    // Render Held Detailed Weapon
    val wCos = cos(swingAngle)
    val wSin = sin(swingAngle)
    val perpX = -wSin
    val perpY = wCos
    when (snapshot.weaponEffectTag) {
        "REAPING_CLEAVE" -> {
            // Crescent Bone Scythe
            val shaftTip = Offset(handPos.x + wCos * 42f * scale, handPos.y + wSin * 42f * scale)
            drawLine(color = Color(0xFF7D6C5F), start = handPos, end = shaftTip, strokeWidth = 4.2f * scale, cap = StrokeCap.Round)
            val scytheBlade = Path().apply {
                moveTo(shaftTip.x, shaftTip.y)
                quadraticTo(
                    shaftTip.x + wCos * 10f * scale + perpX * 14f * scale,
                    shaftTip.y + wSin * 10f * scale + perpY * 14f * scale,
                    shaftTip.x - wCos * 6f * scale + perpX * 26f * scale,
                    shaftTip.y - wSin * 6f * scale + perpY * 26f * scale
                )
                lineTo(shaftTip.x - wCos * 8f * scale + perpX * 8f * scale, shaftTip.y - wSin * 8f * scale + perpY * 8f * scale)
                close()
            }
            drawPath(scytheBlade, color = heroTint)
            drawPath(scytheBlade, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = 1.6f))
        }
        "THUNDER_SMITE" -> {
            // Cathedral Bell-Warhammer
            val shaftTip = Offset(handPos.x + wCos * 34f * scale, handPos.y + wSin * 34f * scale)
            drawLine(color = Color(0xFF6B5D54), start = handPos, end = shaftTip, strokeWidth = 4.5f * scale, cap = StrokeCap.Round)
            val headA = Offset(shaftTip.x + perpX * 12f * scale, shaftTip.y + perpY * 12f * scale)
            val headB = Offset(shaftTip.x - perpX * 12f * scale, shaftTip.y - perpY * 12f * scale)
            drawLine(color = Color(0xFF5D546E), start = headA, end = headB, strokeWidth = 11f * scale, cap = StrokeCap.Round)
            drawLine(color = heroTint, start = headA, end = headB, strokeWidth = 4f * scale, cap = StrokeCap.Round)
        }
        "SOULBURN" -> {
            // Wraithbone Lantern Censer
            val staffTip = Offset(handPos.x + wCos * 26f * scale, handPos.y + wSin * 26f * scale)
            val lanternPos = Offset(staffTip.x + wCos * 8f * scale, staffTip.y + 8f * scale)
            drawLine(color = EmberGold, start = handPos, end = staffTip, strokeWidth = 3.5f * scale, cap = StrokeCap.Round)
            drawLine(color = Color(0xFFA0958A), start = staffTip, end = lanternPos, strokeWidth = 1.8f * scale)
            drawCircle(color = SpectralTeal.copy(alpha = 0.45f), radius = 12f * scale, center = lanternPos)
            drawCircle(color = SpectralTeal, radius = 6.5f * scale, center = lanternPos)
            drawCircle(color = Color.White, radius = 2.6f * scale, center = lanternPos)
        }
        "VOID_CRIT" -> {
            // Twin Curved Obsidian Daggers
            val tip1 = Offset(handPos.x + cos(swingAngle - 0.25f) * 28f * scale, handPos.y + sin(swingAngle - 0.25f) * 28f * scale)
            val tip2 = Offset(sp.x - 12f * scale * flip + cos(swingAngle + 0.35f) * 24f * scale, sp.y - 10f * scale - bob + sin(swingAngle + 0.35f) * 24f * scale)
            drawLine(color = heroTint, start = handPos, end = tip1, strokeWidth = 4.2f * scale, cap = StrokeCap.Round)
            drawLine(color = Color.White, start = handPos, end = tip1, strokeWidth = 1.5f * scale, cap = StrokeCap.Round)
            drawLine(color = heroTint, start = Offset(sp.x - 12f * scale * flip, sp.y - 10f * scale - bob), end = tip2, strokeWidth = 3.8f * scale, cap = StrokeCap.Round)
        }
        else -> {
            // Sanguine Greatsword with Crossguard
            val tip = Offset(handPos.x + wCos * 40f * scale, handPos.y + wSin * 40f * scale)
            val guardA = Offset(handPos.x + wCos * 6f * scale + perpX * 8f * scale, handPos.y + wSin * 6f * scale + perpY * 8f * scale)
            val guardB = Offset(handPos.x + wCos * 6f * scale - perpX * 8f * scale, handPos.y + wSin * 6f * scale - perpY * 8f * scale)
            drawLine(color = heroTint, start = handPos, end = tip, strokeWidth = 6.2f * scale, cap = StrokeCap.Round)
            drawLine(color = Color.White.copy(alpha = 0.85f), start = handPos, end = tip, strokeWidth = 2f * scale, cap = StrokeCap.Round)
            drawLine(color = EmberGold, start = guardA, end = guardB, strokeWidth = 4f * scale, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawDetailedEnemy(
    en: ActiveEnemy,
    sp: Offset,
    elapsedTime: Float,
    scale: Float
) {
    val r = en.radius * scale
    val baseColor = parseHexSafe(en.tintHex, CrimsonBlood)
    val bodyColor = when {
        en.hitFlashTimer > 0f -> Color.White
        en.stunTimer > 0f -> Color(0xFF7D7054)
        else -> baseColor
    }
    val stride = if (en.stunTimer <= 0f) sin(en.walkPhase) else 0f
    val bob = kotlin.math.abs(cos(en.walkPhase)) * 3f * scale
    val flip = if (cos(en.facingAngle) < -0.1f) -1f else 1f

    // Ground shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.58f),
        topLeft = Offset(sp.x - r * 0.95f, sp.y + r * 0.1f),
        size = Size(r * 1.9f, r * 0.72f)
    )

    // Telegraph or stun ring
    if (en.telegraphTimer > 0f) {
        drawCircle(color = CrimsonBright.copy(alpha = 0.45f), radius = r * 2.0f, center = sp, style = Stroke(width = 3.5f))
    }
    if (en.stunTimer > 0f) {
        drawCircle(color = EmberGold.copy(alpha = 0.8f), radius = r * 1.32f, center = Offset(sp.x, sp.y - r * 1.25f), style = Stroke(width = 2.4f))
    }

    when (en.behavior) {
        "BOSS_WARDEN" -> {
            // Towering Dreadlord Boss with flapping demon wings, horned crown, animated legs & abyssal blade!
            val wingFlap = sin(elapsedTime * 5.2f) * r * 0.35f
            val wings = Path().apply {
                moveTo(sp.x, sp.y - r * 0.75f - bob)
                lineTo(sp.x - r * 1.75f, sp.y - r * 1.55f - wingFlap)
                lineTo(sp.x - r * 1.25f, sp.y - r * 0.25f)
                lineTo(sp.x, sp.y - r * 0.45f - bob)
                lineTo(sp.x + r * 1.25f, sp.y - r * 0.25f)
                lineTo(sp.x + r * 1.75f, sp.y - r * 1.55f - wingFlap)
                close()
            }
            drawPath(wings, color = CrimsonBlood.copy(alpha = 0.9f))
            drawPath(wings, color = EmberGold.copy(alpha = 0.65f), style = Stroke(width = 2.2f))

            // Animated Boss Legs
            drawLine(color = Color(0xFF1F1829), start = Offset(sp.x - r * 0.3f, sp.y - r * 0.2f), end = Offset(sp.x - r * 0.38f + stride * 7f * scale, sp.y + r * 0.45f), strokeWidth = 7f * scale, cap = StrokeCap.Round)
            drawLine(color = Color(0xFF2E233D), start = Offset(sp.x + r * 0.3f, sp.y - r * 0.2f), end = Offset(sp.x + r * 0.38f - stride * 7f * scale, sp.y + r * 0.45f), strokeWidth = 7f * scale, cap = StrokeCap.Round)

            // Armored Boss Torso
            val torso = Path().apply {
                moveTo(sp.x - r * 0.62f, sp.y - r * 1.08f - bob)
                lineTo(sp.x + r * 0.62f, sp.y - r * 1.08f - bob)
                lineTo(sp.x + r * 0.42f, sp.y - r * 0.15f - bob)
                lineTo(sp.x - r * 0.42f, sp.y - r * 0.15f - bob)
                close()
            }
            drawPath(torso, color = bodyColor)
            drawPath(torso, color = EmberGold, style = Stroke(width = 2.8f))

            // Spiked Boss Pauldrons
            drawCircle(color = Color(0xFF33263F), radius = r * 0.34f, center = Offset(sp.x - r * 0.64f, sp.y - r * 0.96f - bob))
            drawCircle(color = Color(0xFF33263F), radius = r * 0.34f, center = Offset(sp.x + r * 0.64f, sp.y - r * 0.96f - bob))

            // Boss Crowned Helm
            val headC = Offset(sp.x, sp.y - r * 1.38f - bob)
            drawCircle(color = EmberGold.copy(alpha = 0.55f), radius = r * 0.52f, center = headC, style = Stroke(width = 2.4f))
            drawCircle(color = Color(0xFF241B2D), radius = r * 0.36f, center = headC)
            drawLine(color = EmberGold, start = Offset(headC.x - r * 0.22f, headC.y - r * 0.25f), end = Offset(headC.x - r * 0.42f, headC.y - r * 0.65f), strokeWidth = 4f * scale, cap = StrokeCap.Round)
            drawLine(color = EmberGold, start = Offset(headC.x + r * 0.22f, headC.y - r * 0.25f), end = Offset(headC.x + r * 0.42f, headC.y - r * 0.65f), strokeWidth = 4f * scale, cap = StrokeCap.Round)
            drawCircle(color = EmberGold, radius = 3.5f * scale, center = Offset(headC.x - r * 0.13f, headC.y))
            drawCircle(color = EmberGold, radius = 3.5f * scale, center = Offset(headC.x + r * 0.13f, headC.y))

            // Boss Colossal Abyssal Greatsword
            val swAngle = en.facingAngle + (if (en.attackAnimTimer > 0f) -0.9f + (0.35f - en.attackAnimTimer) * 5f else 0.4f)
            val hand = Offset(sp.x + r * 0.6f * flip, sp.y - r * 0.68f - bob)
            val tip = Offset(hand.x + cos(swAngle) * r * 1.45f, hand.y + sin(swAngle) * r * 1.45f)
            drawLine(color = CrimsonBright, start = hand, end = tip, strokeWidth = 7f * scale, cap = StrokeCap.Round)
            drawLine(color = EmberGold, start = hand, end = tip, strokeWidth = 2.4f * scale, cap = StrokeCap.Round)
        }
        "CASTER" -> {
            // Floating Robed Cultist Warlock + Bone Staff & Arcane Orb
            val hover = sin(elapsedTime * 4.5f + en.x * 0.05f) * 5f * scale
            val robe = Path().apply {
                moveTo(sp.x - r * 0.38f, sp.y - r * 0.92f + hover)
                lineTo(sp.x + r * 0.38f, sp.y - r * 0.92f + hover)
                lineTo(sp.x + r * 0.68f, sp.y + r * 0.35f + hover)
                lineTo(sp.x, sp.y + r * 0.18f + hover)
                lineTo(sp.x - r * 0.68f, sp.y + r * 0.35f + hover)
                close()
            }
            drawPath(robe, color = bodyColor)
            drawPath(robe, color = SpectralTeal.copy(alpha = 0.8f), style = Stroke(width = 2.2f))

            // Hooded Cultist Head
            val headC = Offset(sp.x, sp.y - r * 1.12f + hover)
            drawCircle(color = Color(0xFF1A222B), radius = r * 0.34f, center = headC)
            drawCircle(color = SpectralTeal, radius = 2.6f * scale, center = Offset(headC.x - r * 0.1f, headC.y))
            drawCircle(color = SpectralTeal, radius = 2.6f * scale, center = Offset(headC.x + r * 0.1f, headC.y))

            // Bone Staff + Levitating Orb
            val staffTop = Offset(sp.x + r * 0.72f * flip, sp.y - r * 1.25f + hover)
            val staffBot = Offset(sp.x + r * 0.62f * flip, sp.y + r * 0.25f + hover)
            drawLine(color = Color(0xFFD5CCB8), start = staffBot, end = staffTop, strokeWidth = 3f * scale, cap = StrokeCap.Round)
            drawCircle(color = SpectralTeal, radius = r * 0.24f, center = Offset(staffTop.x, staffTop.y - 4f * scale))
            drawCircle(color = Color.White, radius = r * 0.10f, center = Offset(staffTop.x, staffTop.y - 4f * scale))
        }
        "STALKER" -> {
            // Nimble Hooded Shadow Assassin with Twin Claws & Sprinting Legs
            drawLine(color = Color(0xFF191522), start = Offset(sp.x - 4f * scale, sp.y - r * 0.25f), end = Offset(sp.x - 7f * scale + stride * 8f * scale, sp.y + r * 0.42f), strokeWidth = 4.6f * scale, cap = StrokeCap.Round)
            drawLine(color = Color(0xFF282136), start = Offset(sp.x + 4f * scale, sp.y - r * 0.25f), end = Offset(sp.x + 7f * scale - stride * 8f * scale, sp.y + r * 0.42f), strokeWidth = 4.6f * scale, cap = StrokeCap.Round)

            val cloak = Path().apply {
                moveTo(sp.x - r * 0.48f, sp.y - r * 0.95f - bob)
                lineTo(sp.x + r * 0.48f, sp.y - r * 0.95f - bob)
                lineTo(sp.x + r * 0.56f, sp.y - r * 0.1f - bob)
                lineTo(sp.x - r * 0.56f, sp.y - r * 0.1f - bob)
                close()
            }
            drawPath(cloak, color = bodyColor)
            drawPath(cloak, color = Color(0xFFB368E8).copy(alpha = 0.7f), style = Stroke(width = 1.8f))

            val headC = Offset(sp.x + 2f * scale * flip, sp.y - r * 1.15f - bob)
            drawCircle(color = Color(0xFF1C1629), radius = r * 0.34f, center = headC)
            drawCircle(color = Color.White, radius = 2.6f * scale, center = Offset(headC.x + r * 0.12f * flip, headC.y))

            // Dual Assassin Claws
            val clawEnd = Offset(sp.x + cos(en.facingAngle) * r * 1.1f, sp.y - r * 0.55f + sin(en.facingAngle) * r * 1.1f)
            drawLine(color = Color(0xFFE0D5FF), start = Offset(sp.x + r * 0.35f * flip, sp.y - r * 0.6f), end = clawEnd, strokeWidth = 3.2f * scale, cap = StrokeCap.Round)
        }
        else -> {
            // Horned CHARGER Abyssal Berserker Warrior with Heavy Cleaver
            drawLine(color = Color(0xFF1E1928), start = Offset(sp.x - 5f * scale, sp.y - r * 0.2f), end = Offset(sp.x - 7f * scale + stride * 7f * scale, sp.y + r * 0.44f), strokeWidth = 5.5f * scale, cap = StrokeCap.Round)
            drawLine(color = Color(0xFF2D253B), start = Offset(sp.x + 5f * scale, sp.y - r * 0.2f), end = Offset(sp.x + 7f * scale - stride * 7f * scale, sp.y + r * 0.44f), strokeWidth = 5.5f * scale, cap = StrokeCap.Round)

            // Muscular armored torso
            drawRoundRect(
                color = bodyColor,
                topLeft = Offset(sp.x - r * 0.56f, sp.y - r * 1.02f - bob),
                size = Size(r * 1.12f, r * 0.88f),
                cornerRadius = CornerRadius(6f * scale)
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.65f),
                topLeft = Offset(sp.x - r * 0.56f, sp.y - r * 1.02f - bob),
                size = Size(r * 1.12f, r * 0.88f),
                cornerRadius = CornerRadius(6f * scale),
                style = Stroke(width = 2f)
            )

            // Horned Warrior Helm
            val headC = Offset(sp.x, sp.y - r * 1.24f - bob)
            drawCircle(color = Color(0xFF2B2336), radius = r * 0.36f, center = headC)
            drawLine(color = Color(0xFFD7CFC5), start = Offset(headC.x - r * 0.22f, headC.y - r * 0.15f), end = Offset(headC.x - r * 0.58f, headC.y - r * 0.55f), strokeWidth = 3.4f * scale, cap = StrokeCap.Round)
            drawLine(color = Color(0xFFD7CFC5), start = Offset(headC.x + r * 0.22f, headC.y - r * 0.15f), end = Offset(headC.x + r * 0.58f, headC.y - r * 0.55f), strokeWidth = 3.4f * scale, cap = StrokeCap.Round)
            drawCircle(color = EmberGold, radius = 2.6f * scale, center = Offset(headC.x - r * 0.11f, headC.y))
            drawCircle(color = EmberGold, radius = 2.6f * scale, center = Offset(headC.x + r * 0.11f, headC.y))

            // Raised Battle Cleaver
            val cleaveAng = en.facingAngle + (if (en.attackAnimTimer > 0f) -0.8f else 0.35f)
            val hand = Offset(sp.x + r * 0.52f * flip, sp.y - r * 0.64f - bob)
            val tip = Offset(hand.x + cos(cleaveAng) * r * 1.05f, hand.y + sin(cleaveAng) * r * 1.05f)
            drawLine(color = Color(0xFFC9C0B5), start = hand, end = tip, strokeWidth = 4.6f * scale, cap = StrokeCap.Round)
        }
    }

    // Crisp Enemy Health Bar above Head
    val barW = r * 2.3f
    val hpPct = (en.hp.toFloat() / en.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
    val barY = sp.y - r * 1.88f - bob
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.78f),
        topLeft = Offset(sp.x - barW / 2f, barY),
        size = Size(barW, 5.5f * scale),
        cornerRadius = CornerRadius(2.5f * scale)
    )
    drawRoundRect(
        color = if (en.isBoss) EmberGold else CrimsonBright,
        topLeft = Offset(sp.x - barW / 2f, barY),
        size = Size(barW * hpPct, 5.5f * scale),
        cornerRadius = CornerRadius(2.5f * scale)
    )
}

private fun DrawScope.drawRemoteHumanoidAlly(
    ally: RaidPlayerState,
    sp: Offset,
    elapsedTime: Float,
    scale: Float
) {
    val bob = sin(elapsedTime * 4f) * 2f * scale
    drawOval(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(sp.x - 16f * scale, sp.y + 4f * scale), size = Size(32f * scale, 12f * scale))
    drawCircle(color = SpectralTeal.copy(alpha = 0.25f), radius = 32f * scale, center = Offset(sp.x, sp.y - 16f * scale))
    drawRoundRect(
        color = Color(0xFF1E3E44),
        topLeft = Offset(sp.x - 10f * scale, sp.y - 26f * scale - bob),
        size = Size(20f * scale, 24f * scale),
        cornerRadius = CornerRadius(5f * scale)
    )
    drawCircle(color = SpectralTeal, radius = 8.5f * scale, center = Offset(sp.x, sp.y - 34f * scale - bob))
    drawContext.canvas.nativeCanvas.drawText(
        ally.displayName.take(10),
        sp.x,
        sp.y - 48f * scale - bob,
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 23f * scale
            isFakeBoldText = true
            setShadowLayer(3f, 0f, 1.5f, android.graphics.Color.BLACK)
            textAlign = android.graphics.Paint.Align.CENTER
        }
    )
}
