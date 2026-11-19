package com.example.ui.combat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AppStrings
import com.example.data.catalog.GameCatalog
import com.example.data.model.AppLanguage
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
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
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
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(AbyssalObsidian.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrimsonBright,
                            textAlign = TextAlign.Start
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
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
                        Text("${strings.offlineDescent} (#$depth)", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onCancelToSanctum,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("cancel_summon_button")
                    ) {
                        Text(strings.returnToSanctum, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onSendPing: (String) -> Unit,
    onSwapWeapon: () -> Unit = {},
    language: AppLanguage = AppLanguage.INDONESIAN
) {
    BackHandler { onTogglePause() }

    val ambientColor = remember(snapshot.stage.ambientColorHex) {
        parseSafeColor(snapshot.stage.ambientColorHex, CryptSlate)
    }
    val torchColor = remember(snapshot.stage.torchColorHex) {
        parseSafeColor(snapshot.stage.torchColorHex, EmberGold)
    }
    val skill1Def = remember(snapshot.skill1Id) { GameCatalog.skillById(snapshot.skill1Id) }
    val skill2Def = remember(snapshot.skill2Id) { GameCatalog.skillById(snapshot.skill2Id) }
    val activeBoss = remember(snapshot.enemies) { snapshot.enemies.firstOrNull { it.isBoss } }

    var portalAutoTriggered by remember(snapshot.stage.depth) { mutableStateOf(false) }
    LaunchedEffect(snapshot.stageCleared, snapshot.playerX, snapshot.playerY) {
        if (snapshot.stageCleared && !portalAutoTriggered) {
            if (hypot(snapshot.playerX - 500f, snapshot.playerY - 190f) < 55f) {
                portalAutoTriggered = true
                onMoveInput(0f, 0f)
                onEnterPortal()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AbyssalObsidian)
    ) {
        // 60FPS DETAILED 2.5D HUMANOID & GOTHIC CANVAS ARENA
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (snapshot.screenShake > 0.2f) {
                        translationX = sin(snapshot.elapsedTime * 72f) * snapshot.screenShake * 1.4f
                        translationY = cos(snapshot.elapsedTime * 61f) * snapshot.screenShake * 1.1f
                    }
                }
        ) {
            renderDetailedArena(
                snapshot = snapshot,
                ambientColor = ambientColor,
                torchColor = torchColor
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
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "+${snapshot.soulEarnedThisRun}",
                                style = MaterialTheme.typography.labelMedium,
                                color = SpectralTeal
                            )
                            if (snapshot.comboCount >= 2) {
                                Text(
                                    text = "${snapshot.comboCount}x ${strings.comboLabel}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = EmberGold
                                )
                            }
                        }
                        IconButton(
                            onClick = onTogglePause,
                            modifier = Modifier.size(48.dp).testTag("pause_combat_button")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = strings.pauseRun, tint = EmberGold)
                        }
                    }
                }
            }

            if (activeBoss != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = CryptSlate.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CrimsonBright.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(activeBoss.name, style = MaterialTheme.typography.labelMedium, color = CrimsonBright)
                            Text("${activeBoss.hp}/${activeBoss.maxHp}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (activeBoss.hp.toFloat() / activeBoss.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            color = EmberGold,
                            trackColor = AbyssalObsidian,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
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

        // BOTTOM CONTROLS: LEFT VIRTUAL STICK + RIGHT WEAPON SWAP & SKILL/ACTION CLUSTER
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
                if (snapshot.secondaryWeaponName != null) {
                    val weaponColor = parseSafeColor(snapshot.weaponAccentHex, EmberGold)
                    Surface(
                        color = CryptSlate.copy(alpha = 0.90f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, weaponColor),
                        modifier = Modifier
                            .clickable(onClick = onSwapWeapon)
                            .testTag("swap_weapon_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = strings.swapWeaponBtn, tint = weaponColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${snapshot.weaponName.take(14)} • ${strings.swapWeaponBtn}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TacticalActionButton(
                        label = (if (language == AppLanguage.INDONESIAN) skill1Def.nameId else skill1Def.nameEn).take(8),
                        cooldown = snapshot.skill1Cooldown,
                        accent = parseSafeColor(skill1Def.accentHex, SpectralTeal),
                        sizeDp = 62,
                        tag = "skill_1_button",
                        onClick = onSkill1
                    )
                    TacticalActionButton(
                        label = (if (language == AppLanguage.INDONESIAN) skill2Def.nameId else skill2Def.nameEn).take(8),
                        cooldown = snapshot.skill2Cooldown,
                        accent = parseSafeColor(skill2Def.accentHex, EmberGold),
                        sizeDp = 62,
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
                        sizeDp = 78,
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
                val center = Offset(size.width / 2f, size.height / 2f)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val updateFromPos: (Offset) -> Unit = { pos ->
                        val delta = pos - center
                        val dist = hypot(delta.x, delta.y)
                        val clamped = if (dist > maxRadiusPx) {
                            Offset(delta.x / dist * maxRadiusPx, delta.y / dist * maxRadiusPx)
                        } else {
                            delta
                        }
                        knobOffset = clamped
                        onMoveInput(clamped.x / maxRadiusPx, clamped.y / maxRadiusPx)
                    }
                    updateFromPos(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        if (change.positionChange() != Offset.Zero) {
                            updateFromPos(change.position)
                        }
                    }
                    knobOffset = Offset.Zero
                    onMoveInput(0f, 0f)
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
