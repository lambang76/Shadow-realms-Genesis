package com.example.ui.sanctum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppStrings
import com.example.data.catalog.GameCatalog
import com.example.data.model.AppLanguage
import com.example.data.model.CommunityMod
import com.example.data.model.HeroArchetype
import com.example.data.model.RaidLobby
import com.example.data.model.UserProfile
import com.example.ui.theme.AbyssalObsidian
import com.example.ui.theme.CarvedStoneBorder
import com.example.ui.theme.CrimsonBlood
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CryptSlate
import com.example.ui.theme.EmberGold
import com.example.ui.theme.SpectralTeal
import com.example.ui.theme.WeatheredBasalt
import com.example.viewmodel.UiState

@Composable
fun DescendScreen(
    profileState: UiState<UserProfile>,
    publicLobbiesState: UiState<List<RaidLobby>>,
    activeLobby: RaidLobby?,
    activeModIds: Set<String>,
    currentUserId: String,
    strings: AppStrings,
    onStartSoloRun: (UserProfile) -> Unit,
    onGlobalMatchmaking: (String) -> Unit,
    onCreatePrivateSanctum: (String) -> Unit,
    onJoinByCode: (String) -> Unit,
    onLaunchCoopRaid: (UserProfile, RaidLobby) -> Unit,
    onLeaveLobby: () -> Unit
) {
    var inviteCodeInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CarvedStoneBorder),
                modifier = Modifier.fillMaxWidth().height(180.dp)
            ) {
                Box {
                    Image(
                        painter = painterResource(id = R.drawable.img_sanctum_hero),
                        contentDescription = "Sanctum Cathedral",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(AbyssalObsidian.copy(alpha = 0.25f), AbyssalObsidian.copy(alpha = 0.92f))
                                )
                            )
                            .padding(18.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            Text(strings.appTitle, style = MaterialTheme.typography.displayMedium, color = EmberGold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${strings.activeModsCount}: ${activeModIds.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = SpectralTeal
                            )
                        }
                    }
                }
            }
        }

        when (profileState) {
            is UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EmberGold)
                }
            }
            is UiState.Error -> item {
                Text(profileState.message, color = CrimsonBright, style = MaterialTheme.typography.bodyLarge)
            }
            is UiState.Success -> {
                val profile = profileState.data

                item {
                    Button(
                        onClick = { onStartSoloRun(profile) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmberGold, contentColor = AbyssalObsidian),
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("start_solo_run_button")
                    ) {
                        Icon(Icons.Default.Whatshot, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.enterSoloAbyss, style = MaterialTheme.typography.labelLarge)
                    }
                }

                // CO-OP WAR ROOM
                item {
                    Surface(
                        color = CryptSlate,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CarvedStoneBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = SpectralTeal)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.coopWarRoom, style = MaterialTheme.typography.titleLarge, color = SpectralTeal)
                            }

                            if (activeLobby != null) {
                                Surface(
                                    color = WeatheredBasalt,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, SpectralTeal.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "${strings.inviteCodeLabel}: ${activeLobby.inviteCode}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = EmberGold
                                        )
                                        Text(
                                            text = "${activeLobby.memberUids.size}/4 Allies • ${activeLobby.status}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SpectralTeal
                                        )
                                        if (activeLobby.hostUid == currentUserId) {
                                            Button(
                                                onClick = { onLaunchCoopRaid(profile, activeLobby) },
                                                colors = ButtonDefaults.buttonColors(containerColor = SpectralTeal, contentColor = AbyssalObsidian),
                                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("launch_coop_raid_button")
                                            ) {
                                                Text(strings.launchRaid, style = MaterialTheme.typography.labelLarge)
                                            }
                                        } else {
                                            Text(strings.waitingForAllies, style = MaterialTheme.typography.bodyMedium, color = EmberGold)
                                            if (activeLobby.status == "IN_GAME") {
                                                Button(
                                                    onClick = { onLaunchCoopRaid(profile, activeLobby) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SpectralTeal, contentColor = AbyssalObsidian),
                                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                                ) {
                                                    Text(strings.launchRaid)
                                                }
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = onLeaveLobby,
                                            border = BorderStroke(1.dp, CrimsonBright),
                                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("leave_lobby_button")
                                        ) {
                                            Text(strings.leaveLobby, color = CrimsonBright)
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { onGlobalMatchmaking(profile.displayName) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SpectralTeal, contentColor = AbyssalObsidian),
                                        modifier = Modifier.weight(1f).height(50.dp).testTag("global_matchmaking_button")
                                    ) {
                                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.globalMatchmaking, style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = { onCreatePrivateSanctum(profile.displayName) },
                                        border = BorderStroke(1.dp, EmberGold),
                                        modifier = Modifier.weight(1f).height(50.dp).testTag("create_private_lobby_button")
                                    ) {
                                        Text(strings.createPrivateSanctum, color = EmberGold, style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = inviteCodeInput,
                                        onValueChange = { inviteCodeInput = it.uppercase().take(8) },
                                        label = { Text(strings.enterInviteCode) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("invite_code_input")
                                    )
                                    Button(
                                        onClick = { onJoinByCode(inviteCodeInput) },
                                        enabled = inviteCodeInput.length >= 4,
                                        colors = ButtonDefaults.buttonColors(containerColor = EmberGold, contentColor = AbyssalObsidian),
                                        modifier = Modifier.height(52.dp).testTag("join_code_button")
                                    ) {
                                        Text(strings.joinAction)
                                    }
                                }

                                if (publicLobbiesState is UiState.Success && publicLobbiesState.data.isNotEmpty()) {
                                    Text(strings.globalMatchmaking, style = MaterialTheme.typography.labelLarge, color = EmberGold)
                                    publicLobbiesState.data.take(4).forEach { lobby ->
                                        Surface(
                                            color = WeatheredBasalt,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, CarvedStoneBorder),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onJoinByCode(lobby.inviteCode) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${lobby.hostName} • ${lobby.inviteCode}", color = MaterialTheme.colorScheme.onSurface)
                                                Text("${lobby.memberUids.size}/4", color = SpectralTeal, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SoulAltarScreen(
    profileState: UiState<UserProfile>,
    availableWeapons: List<CommunityMod>,
    availableRelics: List<CommunityMod>,
    language: AppLanguage,
    strings: AppStrings,
    onSelectClass: (String) -> Unit,
    onUpgradeTalent: (String, Long, Long) -> Unit,
    onEquipLoadout: (String, String) -> Unit
) {
    when (profileState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EmberGold)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(profileState.message, color = CrimsonBright)
        }
        is UiState.Success -> {
            val profile = profileState.data
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Surface(
                        color = CryptSlate,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, EmberGold.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatPill(strings.soulEssenceLabel, profile.soulEssence.toString(), SpectralTeal)
                            StatPill(strings.highestDepthLabel, "#${profile.highestDepth}", EmberGold)
                            StatPill(strings.totalKillsLabel, profile.totalKills.toString(), CrimsonBright)
                        }
                    }
                }

                item {
                    Text(strings.selectClassTitle, style = MaterialTheme.typography.titleLarge, color = EmberGold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(HeroArchetype.entries) { archetype ->
                            val selected = profile.selectedClass == archetype.id
                            val drawableId = when (archetype) {
                                HeroArchetype.BLOODBLADE -> R.drawable.img_class_bloodblade
                                HeroArchetype.NECROMANCER -> R.drawable.img_class_necromancer
                                HeroArchetype.STALKER -> R.drawable.img_class_stalker
                            }
                            Surface(
                                color = if (selected) WeatheredBasalt else CryptSlate,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) EmberGold else CarvedStoneBorder),
                                modifier = Modifier
                                    .width(210.dp)
                                    .clickable { onSelectClass(archetype.id) }
                                    .testTag("class_card_${archetype.id.lowercase()}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Image(
                                        painter = painterResource(id = drawableId),
                                        contentDescription = GameCatalog.classDisplayName(archetype, language),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = GameCatalog.classDisplayName(archetype, language),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (selected) EmberGold else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = GameCatalog.classLore(archetype, language),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(strings.talentAltarTitle, style = MaterialTheme.typography.titleLarge, color = EmberGold)
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TalentNodeCard(
                            title = if (language == AppLanguage.INDONESIAN) "Kekuatan Merah (Crimson Might)" else "Crimson Might",
                            bonusDesc = "+4 ATK / rank",
                            rank = profile.crimsonMightRank,
                            essence = profile.soulEssence,
                            icon = Icons.Default.Whatshot,
                            strings = strings,
                            tag = "upgrade_crimson_button",
                            onUpgrade = { onUpgradeTalent("crimsonMightRank", profile.crimsonMightRank, profile.soulEssence) }
                        )
                        TalentNodeCard(
                            title = if (language == AppLanguage.INDONESIAN) "Benteng Obsidian (Obsidian Ward)" else "Obsidian Ward",
                            bonusDesc = "+18 Max HP / rank",
                            rank = profile.obsidianWardRank,
                            essence = profile.soulEssence,
                            icon = Icons.Default.Security,
                            strings = strings,
                            tag = "upgrade_obsidian_button",
                            onUpgrade = { onUpgradeTalent("obsidianWardRank", profile.obsidianWardRank, profile.soulEssence) }
                        )
                        TalentNodeCard(
                            title = if (language == AppLanguage.INDONESIAN) "Arkana Terlarang (Forbidden Arcana)" else "Forbidden Arcana",
                            bonusDesc = "-5% Cooldown / rank",
                            rank = profile.forbiddenArcanaRank,
                            essence = profile.soulEssence,
                            icon = Icons.Default.AutoAwesome,
                            strings = strings,
                            tag = "upgrade_arcana_button",
                            onUpgrade = { onUpgradeTalent("forbiddenArcanaRank", profile.forbiddenArcanaRank, profile.soulEssence) }
                        )
                    }
                }

                item {
                    Text(strings.armoryTitle, style = MaterialTheme.typography.titleLarge, color = EmberGold)
                }

                items(availableWeapons) { weapon ->
                    val equipped = profile.equippedWeaponId == weapon.id
                    Surface(
                        color = CryptSlate,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (equipped) EmberGold else CarvedStoneBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(weapon.name, style = MaterialTheme.typography.titleMedium, color = EmberGold)
                                Text(
                                    text = if (language == AppLanguage.INDONESIAN) weapon.loreId else weapon.loreEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${weapon.effectTag} • PWR ${weapon.powerValue} • SPD ${weapon.speedValue}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpectralTeal
                                )
                            }
                            Button(
                                onClick = { onEquipLoadout(weapon.id, profile.equippedRelicId) },
                                enabled = !equipped,
                                colors = ButtonDefaults.buttonColors(containerColor = EmberGold, contentColor = AbyssalObsidian),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(if (equipped) strings.equippedTag else strings.equipAction)
                            }
                        }
                    }
                }

                items(availableRelics) { relic ->
                    val equipped = profile.equippedRelicId == relic.id
                    Surface(
                        color = CryptSlate,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (equipped) SpectralTeal else CarvedStoneBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(relic.name, style = MaterialTheme.typography.titleMedium, color = SpectralTeal)
                                Text(
                                    text = if (language == AppLanguage.INDONESIAN) relic.loreId else relic.loreEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onEquipLoadout(profile.equippedWeaponId, relic.id) },
                                enabled = !equipped,
                                colors = ButtonDefaults.buttonColors(containerColor = SpectralTeal, contentColor = AbyssalObsidian),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(if (equipped) strings.equippedTag else strings.equipAction)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TalentNodeCard(
    title: String,
    bonusDesc: String,
    rank: Long,
    essence: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    strings: AppStrings,
    tag: String,
    onUpgrade: () -> Unit
) {
    val cost = (rank + 1L) * 40L
    val isMax = rank >= 10L
    Surface(
        color = CryptSlate,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CarvedStoneBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = EmberGold, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("$title (Lv.$rank/10)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(bonusDesc, style = MaterialTheme.typography.labelMedium, color = SpectralTeal)
                }
            }
            Button(
                onClick = onUpgrade,
                enabled = !isMax && essence >= cost,
                colors = ButtonDefaults.buttonColors(containerColor = EmberGold, contentColor = AbyssalObsidian),
                modifier = Modifier.height(48.dp).testTag(tag)
            ) {
                Text(if (isMax) strings.maxRankLabel else "${strings.upgradeAction} ($cost)")
            }
        }
    }
}
