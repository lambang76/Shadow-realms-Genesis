package com.example.ui.combat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AppStrings
import com.example.data.model.BranchingNarrativeEvent
import com.example.data.model.NarrativeChoice
import com.example.game.ArenaBounds
import com.example.game.CombatSnapshot
import com.example.ui.theme.AbyssalObsidian
import com.example.ui.theme.CarvedStoneBorder
import com.example.ui.theme.CrimsonBlood
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CryptSlate
import com.example.ui.theme.EmberGold
import com.example.ui.theme.SpectralTeal
import com.example.ui.theme.WeatheredBasalt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private fun parseSafeColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun SummoningStageScreen(
    depth: Int,
    errorMessage: String?,
    strings: AppStrings,
    onRetry: () -> Unit,
    onUseOfflineBlueprint: () -> Unit,
    onCancelToSanctum: () -> Unit
) {
    BackHandler { onCancelToSanctum() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AbyssalObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = CryptSlate,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CarvedStoneBorder),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (errorMessage == null) {
                    CircularProgressIndicator(
                        color = EmberGold,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "${strings.summoningStage} (#$depth)",
                        style = MaterialTheme.typography.headlineMedium,
                        color = EmberGold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = strings.aiThinkingHint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "AI Summoning Error",
                        tint = CrimsonBright,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CrimsonBright,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = EmberGold, contentColor = AbyssalObsidian),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("retry_ai_stage_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.retryAi)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onUseOfflineBlueprint,
                        border = BorderStroke(1.dp, CarvedStoneBorder),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("fallback_stage_button")
                    ) {
                        Text("Cryptic Descent (Floor #$depth)", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun NarrativeShrineScreen(
    event: BranchingNarrativeEvent,
    strings: AppStrings,
    onChoose: (NarrativeChoice) -> Unit
) {
    BackHandler {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(AbyssalObsidian, Color(0xFF171322), AbyssalObsidian)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Ancient Shrine",
                tint = SpectralTeal,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.narrativeShrineTitle,
                style = MaterialTheme.typography.labelLarge,
                color = SpectralTeal
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = event.chronicleTitle,
                style = MaterialTheme.typography.displayMedium,
                color = EmberGold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = CryptSlate,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CarvedStoneBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = event.speakerName,
                        style = MaterialTheme.typography.titleMedium,
                        color = EmberGold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = event.storyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            event.choices.forEachIndexed { index, choice ->
                Surface(
                    color = WeatheredBasalt,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmberGold.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onChoose(choice) }
                        .testTag("narrative_choice_$index")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = choice.label,
                            style = MaterialTheme.typography.titleLarge,
                            color = EmberGold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = choice.consequenceHint,
                            style = MaterialTheme.typography.labelMedium,
                            color = SpectralTeal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RunDefeatScreen(
    depthReached: Int,
    kills: Int,
    soulEarned: Int,
    strings: AppStrings,
    onReturn: () -> Unit
) {
    BackHandler { onReturn() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AbyssalObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = CryptSlate,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CrimsonBlood),
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.fallInAbyssTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = CrimsonBright,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "${strings.highestDepthLabel}: #$depthReached",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${strings.totalKillsLabel}: $kills",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "+$soulEarned ${strings.soulEssenceLabel}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SpectralTeal
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onReturn,
                    colors = ButtonDefaults.buttonColors(containerColor = EmberGold, contentColor = AbyssalObsidian),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("return_sanctum_button")
                ) {
                    Text(strings.returnToSanctum, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun CombatArenaScreen(
    snapshot: CombatSnapshot,
    strings: AppStrings,
    isPaused: Boolean,
    isCoop: Boolean,
    onMoveInput: (Float, Float) -> Unit,
    onAttack: () -> Unit,
    onDash: () -> Unit,
    onSkill1: () -> Unit,
    onSkill2: () -> Unit,
    onEnterPortal: () -> Unit,
    onTogglePause: () -> Unit,
    onAbandon: () -> Unit,
    onSendPing: (String) -> Unit
) {
    BackHandler { onTogglePause() }

    val ambientColor = remember(snapshot.stage.ambientColorHex) {
        parseSafeColor(snapshot.stage.ambientColorHex, CryptSlate)
    }
    val torchColor = remember(snapshot.stage.torchColorHex) {
        parseSafeColor(snapshot.stage.torchColorHex, EmberGold)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AbyssalObsidian)
    ) {
        // 60FPS GOTHIC CANVAS ARENA
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = minOf(size.width, size.height) / ArenaBounds.SIZE
            val offsetX = (size.width - ArenaBounds.SIZE * scale) * 0.5f
            val offsetY = (size.height - ArenaBounds.SIZE * scale) * 0.5f

            fun toScreen(vx: Float, vy: Float) = Offset(offsetX + vx * scale, offsetY + vy * scale)

            // Stone chamber floor
            drawRect(
                color = ambientColor,
                topLeft = Offset(offsetX, offsetY),
                size = Size(ArenaBounds.SIZE * scale, ArenaBounds.SIZE * scale)
            )

            // Gothic flagstone grid
            val step = 100f * scale
            for (i in 0..10) {
                val p = i * step
                drawLine(
                    color = Color(0xFF282436).copy(alpha = 0.45f),
                    start = Offset(offsetX + p, offsetY),
                    end = Offset(offsetX + p, offsetY + ArenaBounds.SIZE * scale),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color(0xFF282436).copy(alpha = 0.45f),
                    start = Offset(offsetX, offsetY + p),
                    end = Offset(offsetX + ArenaBounds.SIZE * scale, offsetY + p),
                    strokeWidth = 1.5f
                )
            }

            // Etched ritual sigil in center
            val center = toScreen(500f, 500f)
            drawCircle(
                color = torchColor.copy(alpha = 0.16f),
                radius = 190f * scale,
                center = center,
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = torchColor.copy(alpha = 0.10f),
                radius = 120f * scale,
                center = center,
                style = Stroke(width = 2f)
            )

            // Dramatic flickering corner torches
            val flicker = 0.85f + 0.15f * sin(snapshot.elapsedTime * 7.5f)
            val torchPositions = listOf(
                Offset(110f, 110f), Offset(890f, 110f),
                Offset(110f, 890f), Offset(890f, 890f)
            )
            torchPositions.forEach { tp ->
                val sp = toScreen(tp.x, tp.y)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(torchColor.copy(alpha = 0.34f * flicker), Color.Transparent),
                        center = sp,
                        radius = 220f * scale * flicker
                    ),
                    radius = 220f * scale * flicker,
                    center = sp
                )
                drawCircle(color = torchColor, radius = 7f * scale, center = sp)
            }

            // Obstacles with directional shadow wedges
            snapshot.stage.obstacles.forEach { obs ->
                val ox = obs.xRatio * ArenaBounds.SIZE
                val oy = obs.yRatio * ArenaBounds.SIZE
                val sp = toScreen(ox, oy)
                val r = obs.radius * scale
                drawCircle(
                    color = Color.Black.copy(alpha = 0.55f),
                    radius = r * 1.18f,
                    center = Offset(sp.x + 8f * scale, sp.y + 10f * scale)
                )
                drawCircle(color = Color(0xFF2B2638), radius = r, center = sp)
                drawCircle(
                    color = if (obs.kind == "ALTAR") SpectralTeal.copy(alpha = 0.65f) else EmberGold.copy(alpha = 0.35f),
                    radius = r * 0.82f,
                    center = sp,
                    style = Stroke(width = 2.5f)
                )
            }

            // Descent Portal when chamber is cleared
            if (snapshot.stageCleared) {
                val portalCenter = toScreen(500f, 190f)
                val pulse = 44f + 6f * sin(snapshot.elapsedTime * 4f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(SpectralTeal.copy(alpha = 0.85f), Color.Transparent),
                        center = portalCenter,
                        radius = pulse * 1.9f * scale
                    ),
                    radius = pulse * 1.9f * scale,
                    center = portalCenter
                )
                drawCircle(
                    color = SpectralTeal,
                    radius = pulse * scale,
                    center = portalCenter,
                    style = Stroke(width = 4f)
                )
            }

            // Soul Orb pickups
            snapshot.soulOrbs.forEach { orb ->
                val sp = toScreen(orb.x, orb.y)
                drawCircle(color = SpectralTeal.copy(alpha = 0.35f), radius = 16f * scale, center = sp)
                drawCircle(color = SpectralTeal, radius = 7f * scale, center = sp)
            }

            // Slash Arcs
            snapshot.slashes.forEach { s ->
                val sp = toScreen(s.x, s.y)
                val c = parseSafeColor(s.colorHex, EmberGold)
                val alpha = (s.ttl / s.maxTtl).coerceIn(0f, 1f)
                val sweepDeg = 110f
                val startDeg = Math.toDegrees(s.angle.toDouble()).toFloat() - sweepDeg / 2f
                drawArc(
                    color = c.copy(alpha = alpha * 0.85f),
                    startAngle = startDeg,
                    sweepAngle = sweepDeg,
                    useCenter = false,
                    topLeft = Offset(sp.x - s.radius * scale, sp.y - s.radius * scale),
                    size = Size(s.radius * 2f * scale, s.radius * 2f * scale),
                    style = Stroke(width = 14f * scale, cap = StrokeCap.Round)
                )
            }

            // Enemies + Telegraphs + HP Bars
            snapshot.enemies.forEach { en ->
                val sp = toScreen(en.x, en.y)
                val r = en.radius * scale
                val eColor = if (en.hitFlashTimer > 0f) Color.White else parseSafeColor(en.tintHex, CrimsonBlood)

                if (en.telegraphTimer > 0f) {
                    drawCircle(
                        color = CrimsonBright.copy(alpha = 0.35f),
                        radius = r * 2.1f,
                        center = sp,
                        style = Stroke(width = 3f)
                    )
                }
                drawCircle(color = eColor, radius = r, center = sp)
                drawCircle(
                    color = Color.Black.copy(alpha = 0.6f),
                    radius = r,
                    center = sp,
                    style = Stroke(width = 2.5f)
                )

                // Enemy HP bar
                val barW = r * 2.2f
                val hpPct = (en.hp.toFloat() / en.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
                drawRect(
                    color = Color.Black.copy(alpha = 0.7f),
                    topLeft = Offset(sp.x - barW / 2f, sp.y - r - 12f * scale),
                    size = Size(barW, 5f * scale)
                )
                drawRect(
                    color = if (en.isBoss) EmberGold else CrimsonBright,
                    topLeft = Offset(sp.x - barW / 2f, sp.y - r - 12f * scale),
                    size = Size(barW * hpPct, 5f * scale)
                )
            }

            // Remote Co-op Allies
            snapshot.remoteAllies.forEach { ally ->
                val sp = toScreen(ally.posX.toFloat(), ally.posY.toFloat())
                drawCircle(color = SpectralTeal.copy(alpha = 0.30f), radius = 34f * scale, center = sp)
                drawCircle(color = SpectralTeal, radius = 19f * scale, center = sp)
                drawContext.canvas.nativeCanvas.drawText(
                    ally.displayName.take(10),
                    sp.x,
                    sp.y - 28f * scale,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 24f * scale
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }

            // Projectiles
            snapshot.projectiles.forEach { p ->
                val sp = toScreen(p.x, p.y)
                val c = parseSafeColor(p.colorHex, SpectralTeal)
                drawCircle(color = c.copy(alpha = 0.35f), radius = p.radius * 1.8f * scale, center = sp)
                drawCircle(color = c, radius = p.radius * scale, center = sp)
            }

            // Player Hero + Lantern Aura
            val pCenter = toScreen(snapshot.playerX, snapshot.playerY)
            val lanternRadius = 290f * scale * snapshot.fogRadiusMultiplier
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(EmberGold.copy(alpha = 0.22f), Color.Transparent),
                    center = pCenter,
                    radius = lanternRadius
                ),
                radius = lanternRadius,
                center = pCenter
            )

            val heroColor = when {
                snapshot.hitFlashTimer > 0f -> CrimsonBright
                snapshot.invulnTimer > 0f -> Color.White.copy(alpha = 0.7f)
                else -> parseSafeColor(snapshot.weaponAccentHex, EmberGold)
            }
            drawCircle(color = heroColor, radius = 21f * scale, center = pCenter)
            // Facing indicator tip
            val tip = Offset(
                pCenter.x + cos(snapshot.facingAngle) * 28f * scale,
                pCenter.y + sin(snapshot.facingAngle) * 28f * scale
            )
            drawLine(color = EmberGold, start = pCenter, end = tip, strokeWidth = 5f * scale, cap = StrokeCap.Round)

            // Floating ash particles
            snapshot.particles.forEach { pt ->
                val sp = toScreen(pt.x, pt.y)
                drawCircle(
                    color = EmberGold.copy(alpha = pt.alpha),
                    radius = pt.size * scale,
                    center = sp
                )
            }

            // Floating Damage / Heal Numbers
            snapshot.floatingNumbers.forEach { fn ->
                val sp = toScreen(fn.x, fn.y)
                drawContext.canvas.nativeCanvas.drawText(
                    fn.text,
                    sp.x,
                    sp.y,
                    android.graphics.Paint().apply {
                        color = try {
                            android.graphics.Color.parseColor(fn.colorHex)
                        } catch (_: Exception) {
                            android.graphics.Color.WHITE
                        }
                        textSize = 30f * scale
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }

            // Arena stone wall frame
            drawRect(
                color = CarvedStoneBorder,
                topLeft = Offset(offsetX, offsetY),
                size = Size(ArenaBounds.SIZE * scale, ArenaBounds.SIZE * scale),
                style = Stroke(width = 6f)
            )
        }

        // TOP HUD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = CryptSlate.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CarvedStoneBorder),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "F${snapshot.stage.depth} • ${snapshot.stage.title}",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmberGold,
                                maxLines = 1
                            )
                            Text(
                                text = "${snapshot.hp}/${snapshot.maxHp}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { (snapshot.hp.toFloat() / snapshot.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            color = CrimsonBright,
                            trackColor = AbyssalObsidian,
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                }

                Surface(
                    color = CryptSlate.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CarvedStoneBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+${snapshot.soulEarnedThisRun}",
                            style = MaterialTheme.typography.labelMedium,
                            color = SpectralTeal
                        )
                        IconButton(
                            onClick = onTogglePause,
                            modifier = Modifier.size(48.dp).testTag("pause_combat_button")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = strings.pauseRun, tint = EmberGold)
                        }
                    }
                }
            }

            if (isCoop) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(strings.pingHelp, strings.pingBoss, strings.pingHeal).forEach { ping ->
                        Surface(
                            color = WeatheredBasalt.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SpectralTeal.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable { onSendPing(ping) }
                        ) {
                            Text(
                                text = ping,
                                style = MaterialTheme.typography.labelSmall,
                                color = SpectralTeal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (snapshot.stageCleared) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onEnterPortal,
                    colors = ButtonDefaults.buttonColors(containerColor = SpectralTeal, contentColor = AbyssalObsidian),
                    modifier = Modifier.align(Alignment.CenterHorizontally).height(50.dp).testTag("enter_portal_button")
                ) {
                    Text("${strings.stageClearedTitle} — ${strings.enterPortal}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // BOTTOM CONTROLS: LEFT VIRTUAL STICK + RIGHT ACTION CLUSTER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            VirtualJoystick(onMoveInput = onMoveInput)

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TacticalActionButton(
                        label = strings.skill1Btn,
                        cooldown = snapshot.skill1Cooldown,
                        accent = SpectralTeal,
                        sizeDp = 58,
                        tag = "skill_1_button",
                        onClick = onSkill1
                    )
                    TacticalActionButton(
                        label = strings.skill2Btn,
                        cooldown = snapshot.skill2Cooldown,
                        accent = EmberGold,
                        sizeDp = 58,
                        tag = "skill_2_button",
                        onClick = onSkill2
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TacticalActionButton(
                        label = strings.dashBtn,
                        cooldown = snapshot.dashCooldown,
                        accent = Color(0xFFA59C94),
                        sizeDp = 60,
                        tag = "dash_button",
                        onClick = onDash
                    )
                    TacticalActionButton(
                        label = strings.attackBtn,
                        cooldown = 0f,
                        accent = CrimsonBright,
                        sizeDp = 76,
                        tag = "attack_button",
                        onClick = onAttack
                    )
                }
            }
        }

        // PAUSE OVERLAY
        if (isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AbyssalObsidian.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = CryptSlate,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, EmberGold),
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(strings.pauseRun, style = MaterialTheme.typography.headlineMedium, color = EmberGold)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onTogglePause,
                            colors = ButtonDefaults.buttonColors(containerColor = EmberGold, contentColor = AbyssalObsidian),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("resume_combat_button")
                        ) {
                            Text(strings.resumeRun, style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onAbandon,
                            border = BorderStroke(1.dp, CrimsonBright),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("abandon_combat_button")
                        ) {
                            Text(strings.abandonRun, color = CrimsonBright)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualJoystick(onMoveInput: (Float, Float) -> Unit) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusPx = 95f

    Box(
        modifier = Modifier
            .size(134.dp)
            .clip(CircleShape)
            .background(CryptSlate.copy(alpha = 0.55f))
            .border(1.5.dp, EmberGold.copy(alpha = 0.45f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onMoveInput(0f, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onMoveInput(0f, 0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val candidate = knobOffset + dragAmount
                    val dist = hypot(candidate.x, candidate.y)
                    val clamped = if (dist > maxRadiusPx) {
                        Offset(candidate.x / dist * maxRadiusPx, candidate.y / dist * maxRadiusPx)
                    } else {
                        candidate
                    }
                    knobOffset = clamped
                    onMoveInput(clamped.x / maxRadiusPx, clamped.y / maxRadiusPx)
                }
            }
            .testTag("virtual_joystick"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = EmberGold.copy(alpha = 0.8f),
                radius = 24.dp.toPx(),
                center = c + knobOffset
            )
            drawCircle(
                color = AbyssalObsidian,
                radius = 12.dp.toPx(),
                center = c + knobOffset
            )
        }
    }
}

@Composable
private fun TacticalActionButton(
    label: String,
    cooldown: Float,
    accent: Color,
    sizeDp: Int,
    tag: String,
    onClick: () -> Unit
) {
    val ready = cooldown <= 0.05f
    Surface(
        color = if (ready) accent.copy(alpha = 0.25f) else CryptSlate.copy(alpha = 0.7f),
        shape = CircleShape,
        border = BorderStroke(2.dp, if (ready) accent else CarvedStoneBorder),
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .clickable(enabled = ready, onClick = onClick)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (ready) label else String.format("%.1f", cooldown),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = if (ready) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
