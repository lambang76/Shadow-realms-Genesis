package com.example.ui.forge

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.AppStrings
import com.example.core.UserSettings
import com.example.data.model.AppLanguage
import com.example.data.model.CommunityMod
import com.example.data.model.UserProfile
import com.example.ui.theme.AbyssalObsidian
import com.example.ui.theme.CarvedStoneBorder
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CryptSlate
import com.example.ui.theme.EmberGold
import com.example.ui.theme.SpectralTeal
import com.example.ui.theme.WeatheredBasalt
import com.example.viewmodel.AiActionState
import com.example.viewmodel.UiState

@Composable
fun GrimoireForgeScreen(
    modsState: UiState<List<CommunityMod>>,
    activeModIds: Set<String>,
    authorName: String,
    language: AppLanguage,
    strings: AppStrings,
    aiState: AiActionState,
    onToggleActiveMod: (String) -> Unit,
    onUpvoteMod: (CommunityMod) -> Unit,
    onReviewModWithAi: (String, String, String, Int, Int) -> Unit,
    onPublishMod: (String, String, String, String, String, Int, Int, String, () -> Unit) -> Unit,
    onResetAi: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(strings.forgeTitle, style = MaterialTheme.typography.headlineMedium, color = EmberGold)
            Text(strings.forgeSubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = CryptSlate,
            contentColor = EmberGold
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(strings.tabVault) },
                modifier = Modifier.testTag("mod_vault_tab")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(strings.tabCreateMod) },
                modifier = Modifier.testTag("create_mod_tab")
            )
        }

        if (selectedTab == 0) {
            CommunityVaultList(
                modsState = modsState,
                activeModIds = activeModIds,
                language = language,
                strings = strings,
                onToggleActive = onToggleActiveMod,
                onUpvote = onUpvoteMod
            )
        } else {
            ModCreatorForm(
                authorName = authorName,
                strings = strings,
                aiState = aiState,
                onReviewWithAi = onReviewModWithAi,
                onPublish = { modType, name, loreEn, loreId, effectTag, power, speed, hex ->
                    onPublishMod(modType, name, loreEn, loreId, effectTag, power, speed, hex) {
                        selectedTab = 0
                    }
                },
                onResetAi = onResetAi
            )
        }
    }
}

@Composable
private fun CommunityVaultList(
    modsState: UiState<List<CommunityMod>>,
    activeModIds: Set<String>,
    language: AppLanguage,
    strings: AppStrings,
    onToggleActive: (String) -> Unit,
    onUpvote: (CommunityMod) -> Unit
) {
    when (modsState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EmberGold)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(modsState.message, color = CrimsonBright)
        }
        is UiState.Success -> {
            val mods = modsState.data
            if (mods.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (language == AppLanguage.INDONESIAN)
                            "Brankas masih kosong. Jadilah Penempa Mod pertama!"
                        else
                            "The Vault awaits its first Forgemaster creation!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mods, key = { it.id }) { mod ->
                        val active = mod.id in activeModIds
                        Surface(
                            color = CryptSlate,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(if (active) 2.dp else 1.dp, if (active) SpectralTeal else CarvedStoneBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mod.name, style = MaterialTheme.typography.titleLarge, color = EmberGold)
                                        Text(
                                            text = "${mod.modType} • ${mod.effectTag} • by ${mod.authorName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SpectralTeal
                                        )
                                    }
                                    Text(
                                        text = "PWR ${mod.powerValue} / SPD ${mod.speedValue}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = if (language == AppLanguage.INDONESIAN) mod.loreId else mod.loreEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onUpvote(mod) },
                                        border = BorderStroke(1.dp, CarvedStoneBorder),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmberGold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("${strings.upvoteAction} (${mod.upvotes})", color = EmberGold)
                                    }
                                    Button(
                                        onClick = { onToggleActive(mod.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (active) SpectralTeal else WeatheredBasalt,
                                            contentColor = if (active) AbyssalObsidian else EmberGold
                                        ),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        if (active) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(strings.toggleActiveMod)
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
private fun ModCreatorForm(
    authorName: String,
    strings: AppStrings,
    aiState: AiActionState,
    onReviewWithAi: (String, String, String, Int, Int) -> Unit,
    onPublish: (String, String, String, String, String, Int, Int, String) -> Unit,
    onResetAi: () -> Unit
) {
    var modType by remember { mutableStateOf("WEAPON") }
    var name by remember { mutableStateOf("") }
    var effectTag by remember { mutableStateOf("VAMPIRIC") }
    var powerSlider by remember { mutableFloatStateOf(45f) }
    var speedSlider by remember { mutableFloatStateOf(115f) }
    var accentHex by remember { mutableStateOf("#D4A359") }
    var loreEn by remember { mutableStateOf("") }
    var loreId by remember { mutableStateOf("") }

    val effectOptions = when (modType) {
        "WEAPON" -> listOf("VAMPIRIC", "SOULBURN", "VOID_CRIT", "FROSTBITE")
        "RELIC" -> listOf("DASH_NOVA", "FROSTBITE", "SOUL_GREED")
        else -> listOf("CURSED_FOG", "BLOOD_FRENZY", "IRONMAN_TRIAL")
    }

    val colorChoices = listOf("#D4A359", "#9E2A3B", "#4EA8A8", "#B37FEB")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "WEAPON" to strings.modTypeWeapon,
                    "RELIC" to strings.modTypeRelic,
                    "RULESET" to strings.modTypeRuleset
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = modType == key,
                        onClick = {
                            modType = key
                            effectTag = when (key) {
                                "WEAPON" -> "VAMPIRIC"
                                "RELIC" -> "DASH_NOVA"
                                else -> "CURSED_FOG"
                            }
                            onResetAi()
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmberGold,
                            selectedLabelColor = AbyssalObsidian
                        )
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(50) },
                label = { Text(strings.modNameLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("mod_name_input")
            )
        }

        item {
            Text(strings.effectTagLabel, style = MaterialTheme.typography.labelLarge, color = EmberGold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                effectOptions.forEach { tag ->
                    FilterChip(
                        selected = effectTag == tag,
                        onClick = { effectTag = tag },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpectralTeal,
                            selectedLabelColor = AbyssalObsidian
                        )
                    )
                }
            }
        }

        item {
            Text("${strings.powerSliderLabel}: ${powerSlider.toInt()}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = powerSlider,
                onValueChange = { powerSlider = it },
                valueRange = if (modType == "RULESET") 60f..280f else 15f..150f,
                colors = SliderDefaults.colors(thumbColor = EmberGold, activeTrackColor = EmberGold)
            )
            Text("${strings.speedSliderLabel}: ${speedSlider.toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = speedSlider,
                onValueChange = { speedSlider = it },
                valueRange = 75f..200f,
                colors = SliderDefaults.colors(thumbColor = SpectralTeal, activeTrackColor = SpectralTeal)
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                colorChoices.forEach { hex ->
                    val c = Color(android.graphics.Color.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c)
                            .clickable { accentHex = hex }
                    ) {
                        if (accentHex == hex) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = AbyssalObsidian, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    onReviewWithAi(modType, name, effectTag, powerSlider.toInt(), speedSlider.toInt())
                },
                enabled = name.length >= 2 && aiState !is AiActionState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = SpectralTeal, contentColor = AbyssalObsidian),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("ai_mod_review_button")
            ) {
                if (aiState is AiActionState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AbyssalObsidian, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.aiBalanceCheck, style = MaterialTheme.typography.labelLarge)
                }
            }

            when (aiState) {
                is AiActionState.Success -> {
                    loreEn = aiState.review.loreEn
                    loreId = aiState.review.loreId
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = WeatheredBasalt,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SpectralTeal),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("EN: ${aiState.review.loreEn}", style = MaterialTheme.typography.bodyMedium)
                            Text("ID: ${aiState.review.loreId}", style = MaterialTheme.typography.bodyMedium)
                            Text("AI Verdict: ${aiState.review.balanceVerdict}", style = MaterialTheme.typography.labelMedium, color = EmberGold)
                        }
                    }
                }
                is AiActionState.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(aiState.message, color = CrimsonBright, style = MaterialTheme.typography.bodyMedium)
                }
                else -> {}
            }
        }

        item {
            Button(
                onClick = {
                    val finalEn = loreEn.ifBlank { "Forged by $authorName with $effectTag resonance." }
                    val finalId = loreId.ifBlank { "Ditempa oleh $authorName dengan resonansi $effectTag." }
                    onPublish(
                        modType,
                        name,
                        finalEn,
                        finalId,
                        effectTag,
                        powerSlider.toInt(),
                        speedSlider.toInt(),
                        accentHex
                    )
                },
                enabled = name.trim().length >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = EmberGold, contentColor = AbyssalObsidian),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("publish_mod_button")
            ) {
                Text(strings.publishToVault, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun ChronicleAndSettingsScreen(
    profileState: UiState<UserProfile>,
    settings: UserSettings,
    strings: AppStrings,
    onLanguageSelected: (AppLanguage) -> Unit,
    onBgmVolumeChange: (Float) -> Unit,
    onSfxVolumeChange: (Float) -> Unit,
    onFloatingStickChange: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val chronicles = (profileState as? UiState.Success)?.data?.discoveredChronicles.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(strings.chronicleTitle, style = MaterialTheme.typography.headlineMedium, color = EmberGold)
        }

        if (chronicles.isEmpty()) {
            item {
                Surface(
                    color = CryptSlate,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CarvedStoneBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Book, contentDescription = null, tint = SpectralTeal)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(strings.emptyChronicles, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(chronicles) { title ->
                Surface(
                    color = CryptSlate,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmberGold.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Book, contentDescription = null, tint = EmberGold)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        item {
            Text(strings.settingsTitle, style = MaterialTheme.typography.headlineMedium, color = EmberGold)
        }

        item {
            Surface(
                color = CryptSlate,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CarvedStoneBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium, color = EmberGold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = settings.language == AppLanguage.INDONESIAN,
                            onClick = { onLanguageSelected(AppLanguage.INDONESIAN) },
                            label = { Text("Bahasa Indonesia") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmberGold,
                                selectedLabelColor = AbyssalObsidian
                            ),
                            modifier = Modifier.testTag("settings_lang_id")
                        )
                        FilterChip(
                            selected = settings.language == AppLanguage.ENGLISH,
                            onClick = { onLanguageSelected(AppLanguage.ENGLISH) },
                            label = { Text("English") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmberGold,
                                selectedLabelColor = AbyssalObsidian
                            ),
                            modifier = Modifier.testTag("settings_lang_en")
                        )
                    }

                    Text("${strings.bgmVolumeLabel}: ${(settings.bgmVolume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = settings.bgmVolume,
                        onValueChange = onBgmVolumeChange,
                        colors = SliderDefaults.colors(thumbColor = EmberGold, activeTrackColor = EmberGold)
                    )

                    Text("${strings.sfxVolumeLabel}: ${(settings.sfxVolume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = settings.sfxVolume,
                        onValueChange = onSfxVolumeChange,
                        colors = SliderDefaults.colors(thumbColor = SpectralTeal, activeTrackColor = SpectralTeal)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.floatingStickLabel, style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = settings.floatingJoystick, onCheckedChange = onFloatingStickChange)
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        border = BorderStroke(1.dp, CrimsonBright),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = CrimsonBright)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.signOutAction, color = CrimsonBright)
                    }
                }
            }
        }
    }
}
