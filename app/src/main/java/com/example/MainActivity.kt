package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.audio.GothicAudioEngine
import com.example.core.PreferencesRepository
import com.example.core.UserSettings
import com.example.core.configureAppCheck
import com.example.core.stringsFor
import com.example.data.model.AppLanguage
import com.example.data.repository.DungeonAiRepository
import com.example.data.repository.ModRepository
import com.example.data.repository.RaidRepository
import com.example.data.repository.UserRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.combat.CombatArenaScreen
import com.example.ui.combat.NarrativeShrineScreen
import com.example.ui.combat.RunDefeatScreen
import com.example.ui.combat.SummoningStageScreen
import com.example.ui.forge.ChronicleAndSettingsScreen
import com.example.ui.forge.GrimoireForgeScreen
import com.example.ui.sanctum.DescendScreen
import com.example.ui.sanctum.SoulAltarScreen
import com.example.ui.theme.AbyssalObsidian
import com.example.ui.theme.CryptSlate
import com.example.ui.theme.EmberGold
import com.example.ui.theme.NocturneAbyssTheme
import com.example.viewmodel.RunPhase
import com.example.viewmodel.RunViewModel
import com.example.viewmodel.SanctumViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val audioEngine = GothicAudioEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        val prefsRepo = PreferencesRepository(applicationContext)

        setContent {
            NocturneAbyssTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        prefsRepo = prefsRepo,
                        audioEngine = audioEngine
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        audioEngine.startMusic()
    }

    override fun onPause() {
        super.onPause()
        audioEngine.stopMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.stopMusic()
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    prefsRepo: PreferencesRepository,
    audioEngine: GothicAudioEngine,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val settings by prefsRepo.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
    val strings = remember(settings.language) { stringsFor(settings.language) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(settings.bgmVolume, settings.sfxVolume) {
        audioEngine.setVolumes(settings.bgmVolume, settings.sfxVolume)
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            strings = strings,
            currentLanguage = settings.language,
            onLanguageSelected = { lang -> scope.launch { prefsRepo.setLanguage(lang) } },
            onAuthSuccess = {}
        )
    } else {
        androidx.compose.runtime.key(user.uid) {
            AuthenticatedSanctumShell(
                currentUserId = user.uid,
                displayName = user.displayName ?: "Wanderer",
                settings = settings,
                prefsRepo = prefsRepo,
                audioEngine = audioEngine
            )
        }
    }
}

@Composable
private fun AuthenticatedSanctumShell(
    currentUserId: String,
    displayName: String,
    settings: UserSettings,
    prefsRepo: PreferencesRepository,
    audioEngine: GothicAudioEngine
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = remember(settings.language) { stringsFor(settings.language) }
    val credentialManager = remember { CredentialManager.create(context) }

    val sanctumVm: SanctumViewModel = viewModel(
        key = "sanctum_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                SanctumViewModel(
                    currentUserId = currentUserId,
                    initialDisplayName = displayName,
                    userRepository = UserRepository(db),
                    modRepository = ModRepository(db),
                    raidRepository = RaidRepository(db),
                    aiRepository = DungeonAiRepository()
                )
            }
        }
    )

    val runVm: RunViewModel = viewModel(
        key = "run_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                RunViewModel(
                    currentUserId = currentUserId,
                    userRepository = UserRepository(db),
                    raidRepository = RaidRepository(db),
                    aiRepository = DungeonAiRepository(),
                    audioEngine = audioEngine
                )
            }
        }
    )

    val profileState by sanctumVm.profileState.collectAsStateWithLifecycle()
    val modsState by sanctumVm.communityModsState.collectAsStateWithLifecycle()
    val publicLobbiesState by sanctumVm.publicLobbiesState.collectAsStateWithLifecycle()
    val activeLobby by sanctumVm.activeLobby.collectAsStateWithLifecycle()
    val activeModIds by sanctumVm.activeModIds.collectAsStateWithLifecycle()
    val bannerMessage by sanctumVm.statusBanner.collectAsStateWithLifecycle()
    val modAiState by sanctumVm.modAiState.collectAsStateWithLifecycle()

    val runPhase by runVm.phase.collectAsStateWithLifecycle()
    val isPaused by runVm.isPaused.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(bannerMessage) {
        bannerMessage?.let {
            snackbarHostState.showSnackbar(it)
            sanctumVm.clearBanner()
        }
    }

    val communityMods = (modsState as? UiState.Success)?.data.orEmpty()
    val availableWeapons = remember(communityMods) { sanctumVm.allAvailableWeapons(communityMods) }
    val availableRelics = remember(communityMods) { sanctumVm.allAvailableRelics(communityMods) }
    val activeRulesets = remember(communityMods, activeModIds) {
        communityMods.filter { it.id in activeModIds && it.modType == "RULESET" }
    }

    // Active Dungeon Run takes full-screen precedence
    when (val phase = runPhase) {
        is RunPhase.SummoningStage -> {
            SummoningStageScreen(
                depth = phase.depth,
                errorMessage = phase.errorMessage,
                strings = strings,
                onRetry = { runVm.summonStage(phase.depth, forceFallback = false) },
                onUseOfflineBlueprint = { runVm.summonStage(phase.depth, forceFallback = true) },
                onCancelToSanctum = { runVm.stopRun() }
            )
            return
        }
        is RunPhase.NarrativeShrine -> {
            NarrativeShrineScreen(
                event = phase.event,
                strings = strings,
                onChoose = { choice -> runVm.chooseNarrativeOption(choice) }
            )
            return
        }
        is RunPhase.InCombat -> {
            CombatArenaScreen(
                snapshot = phase.snapshot,
                strings = strings,
                isPaused = isPaused,
                isCoop = activeLobby != null,
                onMoveInput = { dx, dy -> runVm.setMoveInput(dx, dy) },
                onAttack = { runVm.onAttackPressed() },
                onDash = { runVm.onDashPressed() },
                onSkill1 = { runVm.onSkill1Pressed() },
                onSkill2 = { runVm.onSkill2Pressed() },
                onEnterPortal = { runVm.onEnterPortal() },
                onTogglePause = { runVm.togglePause() },
                onAbandon = { runVm.abandonRun() },
                onSendPing = { ping -> runVm.sendCoopPing(ping) }
            )
            return
        }
        is RunPhase.RunDefeat -> {
            RunDefeatScreen(
                depthReached = phase.depthReached,
                kills = phase.kills,
                soulEarned = phase.soulEarned,
                strings = strings,
                onReturn = { runVm.stopRun() }
            )
            return
        }
        RunPhase.Idle -> Unit
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val navItems = listOf(
        Triple(0, strings.tabDescend, Icons.Default.Whatshot),
        Triple(1, strings.tabAltar, Icons.Default.AutoAwesome),
        Triple(2, strings.tabForge, Icons.Default.Build),
        Triple(3, strings.tabChronicle, Icons.Default.Book)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = CryptSlate,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEach { (idx, label, icon) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AbyssalObsidian,
                                    selectedTextColor = EmberGold,
                                    indicatorColor = EmberGold
                                ),
                                modifier = Modifier.testTag("nav_tab_$idx")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = CryptSlate) {
                        navItems.forEach { (idx, label, icon) ->
                            NavigationRailItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                when (selectedTab) {
                    0 -> DescendScreen(
                        profileState = profileState,
                        publicLobbiesState = publicLobbiesState,
                        activeLobby = activeLobby,
                        activeModIds = activeModIds,
                        currentUserId = currentUserId,
                        strings = strings,
                        onStartSoloRun = { profile ->
                            val weapon = availableWeapons.firstOrNull { it.id == profile.equippedWeaponId }
                            val relic = availableRelics.firstOrNull { it.id == profile.equippedRelicId }
                            runVm.startRun(
                                profile = profile,
                                equippedWeapon = weapon,
                                equippedRelic = relic,
                                activeRulesets = activeRulesets,
                                language = settings.language,
                                coopLobby = null
                            )
                        },
                        onGlobalMatchmaking = { name -> sanctumVm.findGlobalMatch(name) },
                        onCreatePrivateSanctum = { name -> sanctumVm.createPrivateLobby(name) },
                        onJoinByCode = { code -> sanctumVm.joinLobbyByCode(code) },
                        onLaunchCoopRaid = { profile, lobby ->
                            val weapon = availableWeapons.firstOrNull { it.id == profile.equippedWeaponId }
                            val relic = availableRelics.firstOrNull { it.id == profile.equippedRelicId }
                            runVm.startRun(
                                profile = profile,
                                equippedWeapon = weapon,
                                equippedRelic = relic,
                                activeRulesets = activeRulesets,
                                language = settings.language,
                                coopLobby = lobby
                            )
                        },
                        onLeaveLobby = { sanctumVm.leaveActiveLobby() }
                    )

                    1 -> SoulAltarScreen(
                        profileState = profileState,
                        availableWeapons = availableWeapons,
                        availableRelics = availableRelics,
                        language = settings.language,
                        strings = strings,
                        onSelectClass = { classId -> sanctumVm.selectClass(classId) },
                        onUpgradeTalent = { branch, rank, essence ->
                            sanctumVm.upgradeTalent(branch, rank, essence)
                        },
                        onEquipLoadout = { weaponId, relicId ->
                            sanctumVm.equipLoadout(weaponId, relicId)
                        }
                    )

                    2 -> GrimoireForgeScreen(
                        modsState = modsState,
                        activeModIds = activeModIds,
                        authorName = (profileState as? UiState.Success)?.data?.displayName ?: displayName,
                        language = settings.language,
                        strings = strings,
                        aiState = modAiState,
                        onToggleActiveMod = { id -> sanctumVm.toggleModActive(id) },
                        onUpvoteMod = { mod -> sanctumVm.upvoteMod(mod) },
                        onReviewModWithAi = { type, name, tag, pwr, spd ->
                            sanctumVm.reviewModWithAi(type, name, tag, pwr, spd, settings.language)
                        },
                        onPublishMod = { type, name, lEn, lId, tag, pwr, spd, hex, done ->
                            val author = (profileState as? UiState.Success)?.data?.displayName ?: displayName
                            sanctumVm.publishMod(author, type, name, lEn, lId, tag, pwr, spd, hex, done)
                        },
                        onResetAi = { sanctumVm.resetModAiState() }
                    )

                    3 -> ChronicleAndSettingsScreen(
                        profileState = profileState,
                        settings = settings,
                        strings = strings,
                        onLanguageSelected = { lang -> scope.launch { prefsRepo.setLanguage(lang) } },
                        onBgmVolumeChange = { vol -> scope.launch { prefsRepo.setBgmVolume(vol) } },
                        onSfxVolumeChange = { vol -> scope.launch { prefsRepo.setSfxVolume(vol) } },
                        onFloatingStickChange = { enabled -> scope.launch { prefsRepo.setFloatingJoystick(enabled) } },
                        onSignOut = {
                            signOutUser(credentialManager, scope)
                        }
                    )
                }
            }
        }
    }
}
