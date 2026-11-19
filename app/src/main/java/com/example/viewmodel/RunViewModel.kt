package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.GothicAudioEngine
import com.example.data.catalog.GameCatalog
import com.example.data.model.AppLanguage
import com.example.data.model.BranchingNarrativeEvent
import com.example.data.model.CommunityMod
import com.example.data.model.EnemyBlueprint
import com.example.data.model.GeneratedStage
import com.example.data.model.HeroArchetype
import com.example.data.model.NarrativeChoice
import com.example.data.model.RaidLobby
import com.example.data.model.RaidPlayerState
import com.example.data.model.StageObstacle
import com.example.data.model.UserProfile
import com.example.data.repository.DungeonAiRepository
import com.example.data.repository.RaidRepository
import com.example.data.repository.UserRepository
import com.example.game.CombatSimulation
import com.example.game.CombatSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface RunPhase {
    data object Idle : RunPhase
    data class SummoningStage(val depth: Int, val errorMessage: String? = null) : RunPhase
    data class InCombat(val snapshot: CombatSnapshot) : RunPhase
    data class NarrativeShrine(val depth: Int, val event: BranchingNarrativeEvent) : RunPhase
    data class RunDefeat(val depthReached: Int, val kills: Int, val soulEarned: Int) : RunPhase
}

class RunViewModel(
    private val currentUserId: String,
    private val userRepository: UserRepository,
    private val raidRepository: RaidRepository,
    private val aiRepository: DungeonAiRepository,
    private val audioEngine: GothicAudioEngine
) : ViewModel() {

    private val _phase = MutableStateFlow<RunPhase>(RunPhase.Idle)
    val phase: StateFlow<RunPhase> = _phase.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private var simulation: CombatSimulation? = null
    private var gameLoopJob: Job? = null
    private var coopSyncJob: Job? = null
    private var coopLobbyJob: Job? = null

    private var activeProfile: UserProfile? = null
    private var activeWeapon: CommunityMod? = null
    private var activeSecondaryWeapon: CommunityMod? = null
    private var activeRelic: CommunityMod? = null
    private var activeSkill1Id: String = "skill1_crimson_cleave"
    private var activeSkill2Id: String = "skill2_judgement"
    private var activeRulesets: List<CommunityMod> = emptyList()
    private var activeLanguage: AppLanguage = AppLanguage.INDONESIAN
    private var currentLobby: RaidLobby? = null

    private var currentDepth = 1
    private var totalKills = 0
    private var totalSouls = 0
    private var bonusDmgPct = 0
    private var bonusSpdPct = 0
    private var carryOverHp: Int? = null
    private val pastChoiceTags = mutableListOf<String>()
    private val discoveredChronicles = mutableListOf<String>()

    fun startRun(
        profile: UserProfile,
        equippedWeapon: CommunityMod?,
        equippedRelic: CommunityMod?,
        activeRulesets: List<CommunityMod>,
        language: AppLanguage,
        coopLobby: RaidLobby?,
        secondaryWeapon: CommunityMod? = null,
        skill1Id: String = "skill1_crimson_cleave",
        skill2Id: String = "skill2_judgement"
    ) {
        activeProfile = profile
        activeWeapon = equippedWeapon
        activeSecondaryWeapon = secondaryWeapon
        activeRelic = equippedRelic
        activeSkill1Id = skill1Id
        activeSkill2Id = skill2Id
        this.activeRulesets = activeRulesets
        activeLanguage = language
        currentLobby = coopLobby

        currentDepth = 1
        totalKills = 0
        totalSouls = 0
        bonusDmgPct = 0
        bonusSpdPct = 0
        carryOverHp = null
        pastChoiceTags.clear()
        discoveredChronicles.clear()
        _isPaused.value = false

        if (coopLobby != null) {
            startCoopObservers(coopLobby)
        }
        summonStage(1)
    }

    fun summonStage(depth: Int, forceFallback: Boolean = false) {
        val profile = activeProfile ?: return
        currentDepth = depth
        gameLoopJob?.cancel()
        audioEngine.setCombatIntensity(0.25f)

        val lobby = currentLobby
        val isHostOrSolo = lobby == null || lobby.hostUid == currentUserId

        if (!isHostOrSolo && !forceFallback) {
            _phase.value = RunPhase.SummoningStage(depth)
            return
        }

        viewModelScope.launch {
            _phase.value = RunPhase.SummoningStage(depth)
            val hpPct = if (carryOverHp != null) {
                val maxHp = HeroArchetype.fromId(profile.selectedClass).baseHp + profile.obsidianWardRank.toInt() * 18
                ((carryOverHp!! * 100f) / maxHp).toInt().coerceIn(5, 100)
            } else 100

            val stageResult = if (forceFallback) {
                Result.success(GameCatalog.fallbackStage(depth, activeLanguage))
            } else {
                aiRepository.generateStage(
                    depth = depth,
                    archetype = HeroArchetype.fromId(profile.selectedClass),
                    hpPercent = hpPct,
                    activeMods = activeRulesets + listOfNotNull(activeWeapon, activeRelic),
                    pastChoices = pastChoiceTags,
                    language = activeLanguage,
                    isCoop = lobby != null
                )
            }

            stageResult.onSuccess { stage ->
                if (lobby != null) {
                    raidRepository.publishStageToLobby(lobby.id, stage, "IN_GAME")
                }
                beginCombat(stage)
            }.onFailure { err ->
                _phase.value = RunPhase.SummoningStage(
                    depth = depth,
                    errorMessage = err.localizedMessage ?: "Failed to summon dungeon stage."
                )
            }
        }
    }

    private fun beginCombat(stage: GeneratedStage) {
        val profile = activeProfile ?: return
        val sim = CombatSimulation(
            stage = stage,
            profile = profile,
            equippedWeapon = activeWeapon,
            equippedRelic = activeRelic,
            activeRulesets = activeRulesets,
            bonusDamagePct = bonusDmgPct,
            bonusSpeedPct = bonusSpdPct,
            currentHpOverride = carryOverHp,
            onSlashSfx = { audioEngine.playSlashSfx() },
            onDashSfx = { audioEngine.playDashSfx() },
            onHitSfx = { audioEngine.playHitSfx() },
            secondaryWeapon = activeSecondaryWeapon,
            skill1Id = activeSkill1Id,
            skill2Id = activeSkill2Id
        )
        simulation = sim
        val hasBoss = stage.enemies.any { it.behavior == "BOSS_WARDEN" }
        audioEngine.setCombatIntensity(if (hasBoss) 1.0f else 0.7f)
        _phase.value = RunPhase.InCombat(sim.snapshot())
        startCombatLoop()
    }

    private fun startCombatLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            var lastTime = System.nanoTime()
            var lastCoopPush = 0L
            while (isActive) {
                val now = System.nanoTime()
                val dt = ((now - lastTime) / 1_000_000_000.0f).coerceIn(0.008f, 0.05f)
                lastTime = now

                val sim = simulation ?: break
                if (!_isPaused.value) {
                    sim.step(dt)
                }
                val snap = sim.snapshot()
                _phase.value = RunPhase.InCombat(snap)

                val lobby = currentLobby
                if (lobby != null && now - lastCoopPush > 95_000_000L) {
                    lastCoopPush = now
                    pushCoopTelemetry(lobby, snap)
                }

                if (snap.playerDead) {
                    handleDefeat(snap)
                    break
                }
                delay(16L)
            }
        }
    }

    private fun pushCoopTelemetry(lobby: RaidLobby, snap: CombatSnapshot) {
        val profile = activeProfile ?: return
        viewModelScope.launch {
            raidRepository.upsertPlayerState(
                lobbyId = lobby.id,
                playerState = RaidPlayerState(
                    playerId = currentUserId,
                    displayName = profile.displayName,
                    archetype = profile.selectedClass,
                    posX = snap.playerX.toDouble(),
                    posY = snap.playerY.toDouble(),
                    facingAngle = snap.facingAngle.toDouble(),
                    hp = snap.hp.toLong(),
                    maxHp = snap.maxHp.toLong(),
                    isAttacking = false,
                    pingText = snap.activePingBanner.orEmpty(),
                    memberUids = lobby.memberUids
                )
            )
        }
    }

    private fun startCoopObservers(lobby: RaidLobby) {
        coopLobbyJob?.cancel()
        coopSyncJob?.cancel()

        coopLobbyJob = viewModelScope.launch {
            raidRepository.observeLobby(lobby.id)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "Coop lobby observer error", fsErr ?: error)
                }
                .collect { updatedLobby ->
                    if (updatedLobby == null) return@collect
                    currentLobby = updatedLobby
                    if (updatedLobby.hostUid != currentUserId &&
                        updatedLobby.status == "IN_GAME" &&
                        updatedLobby.stageData != null &&
                        _phase.value is RunPhase.SummoningStage
                    ) {
                        val parsed = parseStageFromMap(updatedLobby.stageData)
                        if (parsed != null) beginCombat(parsed)
                    }
                }
        }

        coopSyncJob = viewModelScope.launch {
            raidRepository.observeLobbyPlayers(lobby.id, currentUserId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "Coop player sync error", fsErr ?: error)
                }
                .collect { players ->
                    val allies = players.filter { it.playerId != currentUserId }
                    simulation?.updateRemoteAllies(allies)
                }
        }
    }

    fun onEnterPortal() {
        if (_phase.value !is RunPhase.InCombat) return
        val sim = simulation ?: return
        val snap = sim.snapshot()
        if (!snap.stageCleared) return

        gameLoopJob?.cancel()
        totalKills += snap.killsThisRun
        totalSouls += snap.soulEarnedThisRun
        carryOverHp = snap.hp
        audioEngine.playShrineChimeSfx()
        audioEngine.setCombatIntensity(0.25f)

        val profile = activeProfile ?: return
        viewModelScope.launch {
            _phase.value = RunPhase.SummoningStage(currentDepth + 1)
            val hpPct = ((snap.hp * 100f) / snap.maxHp).toInt().coerceIn(5, 100)
            val narrativeResult = aiRepository.generateNarrativeEvent(
                depth = currentDepth,
                archetype = HeroArchetype.fromId(profile.selectedClass),
                hpPercent = hpPct,
                pastChoices = pastChoiceTags,
                language = activeLanguage
            )
            val event = narrativeResult.getOrElse {
                GameCatalog.fallbackNarrative(currentDepth, activeLanguage)
            }
            discoveredChronicles.add(event.chronicleTitle)
            _phase.value = RunPhase.NarrativeShrine(currentDepth, event)
        }
    }

    fun chooseNarrativeOption(choice: NarrativeChoice) {
        val currentHp = carryOverHp ?: 100
        val maxHp = activeProfile?.let {
            HeroArchetype.fromId(it.selectedClass).baseHp + it.obsidianWardRank.toInt() * 18
        } ?: 150
        carryOverHp = (currentHp + choice.hpDelta).coerceIn(1, maxHp)
        bonusDmgPct += choice.damageBonusPct
        bonusSpdPct += choice.speedBonusPct
        totalSouls += choice.soulEssenceDelta
        pastChoiceTags.add(choice.curseTag)

        audioEngine.playShrineChimeSfx()
        summonStage(currentDepth + 1)
    }

    private fun handleDefeat(snap: CombatSnapshot) {
        totalKills += snap.killsThisRun
        totalSouls += snap.soulEarnedThisRun + (currentDepth * 15)
        audioEngine.setCombatIntensity(0.15f)
        _phase.value = RunPhase.RunDefeat(
            depthReached = currentDepth,
            kills = totalKills,
            soulEarned = totalSouls
        )
        viewModelScope.launch {
            userRepository.recordRunOutcome(
                userId = currentUserId,
                soulEarned = totalSouls.toLong(),
                depthReached = currentDepth.toLong(),
                kills = totalKills.toLong(),
                newChronicles = discoveredChronicles
            )
            currentLobby?.let { lobby ->
                if (lobby.hostUid == currentUserId) {
                    raidRepository.updateLobbyStatus(lobby.id, "COMPLETED")
                }
            }
        }
    }

    fun abandonRun() {
        val snap = simulation?.snapshot()
        if (snap != null) {
            totalKills += snap.killsThisRun
            totalSouls += snap.soulEarnedThisRun
            viewModelScope.launch {
                userRepository.recordRunOutcome(
                    userId = currentUserId,
                    soulEarned = totalSouls.toLong(),
                    depthReached = currentDepth.toLong(),
                    kills = totalKills.toLong(),
                    newChronicles = discoveredChronicles
                )
            }
        }
        stopRun()
    }

    fun stopRun() {
        gameLoopJob?.cancel()
        coopSyncJob?.cancel()
        coopLobbyJob?.cancel()
        simulation = null
        currentLobby = null
        _isPaused.value = false
        audioEngine.setCombatIntensity(0.15f)
        _phase.value = RunPhase.Idle
    }

    fun togglePause() {
        _isPaused.value = !_isPaused.value
    }

    fun setMoveInput(dx: Float, dy: Float) {
        simulation?.setMoveInput(dx, dy)
    }

    fun onAttackPressed() = simulation?.triggerPrimaryAttack()
    fun onDashPressed() = simulation?.triggerDash()
    fun onSkill1Pressed() = simulation?.triggerSkill1()
    fun onSkill2Pressed() = simulation?.triggerSkill2()
    fun onSwapWeaponPressed() = simulation?.triggerWeaponSwap()

    fun sendCoopPing(pingText: String) {
        val lobby = currentLobby ?: return
        val snap = simulation?.snapshot() ?: return
        val profile = activeProfile ?: return
        viewModelScope.launch {
            raidRepository.upsertPlayerState(
                lobby.id,
                RaidPlayerState(
                    playerId = currentUserId,
                    displayName = profile.displayName,
                    archetype = profile.selectedClass,
                    posX = snap.playerX.toDouble(),
                    posY = snap.playerY.toDouble(),
                    facingAngle = snap.facingAngle.toDouble(),
                    hp = snap.hp.toLong(),
                    maxHp = snap.maxHp.toLong(),
                    isAttacking = false,
                    pingText = pingText,
                    memberUids = lobby.memberUids
                )
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStageFromMap(map: Map<String, Any>): GeneratedStage? {
        return runCatching {
            val obstaclesRaw = map["obstacles"] as? List<Map<String, Any>> ?: emptyList()
            val enemiesRaw = map["enemies"] as? List<Map<String, Any>> ?: emptyList()
            GeneratedStage(
                depth = (map["depth"] as? Number)?.toInt() ?: 1,
                title = map["title"] as? String ?: "Abyssal Chamber",
                omenWhisper = map["omenWhisper"] as? String ?: "",
                biomeTheme = map["biomeTheme"] as? String ?: "CRIMSON_CRYPT",
                ambientColorHex = map["ambientColorHex"] as? String ?: "#14121A",
                torchColorHex = map["torchColorHex"] as? String ?: "#D4A359",
                fogIntensity = (map["fogIntensity"] as? Number)?.toFloat() ?: 0.45f,
                obstacles = obstaclesRaw.map { o ->
                    StageObstacle(
                        xRatio = (o["xRatio"] as? Number)?.toFloat() ?: 0.5f,
                        yRatio = (o["yRatio"] as? Number)?.toFloat() ?: 0.5f,
                        radius = (o["radius"] as? Number)?.toFloat() ?: 30f,
                        kind = o["kind"] as? String ?: "PILLAR"
                    )
                },
                enemies = enemiesRaw.map { e ->
                    EnemyBlueprint(
                        id = e["id"] as? String ?: "e_${e.hashCode()}",
                        name = e["name"] as? String ?: "Wraith",
                        behavior = e["behavior"] as? String ?: "CHARGER",
                        maxHp = (e["maxHp"] as? Number)?.toInt() ?: 80,
                        damage = (e["damage"] as? Number)?.toInt() ?: 14,
                        speed = (e["speed"] as? Number)?.toFloat() ?: 110f,
                        radius = (e["radius"] as? Number)?.toFloat() ?: 24f,
                        spawnXRatio = (e["spawnXRatio"] as? Number)?.toFloat() ?: 0.5f,
                        spawnYRatio = (e["spawnYRatio"] as? Number)?.toFloat() ?: 0.25f,
                        tintHex = e["tintHex"] as? String ?: "#9E2A3B"
                    )
                }
            )
        }.getOrNull()
    }

    private companion object {
        const val TAG = "RunVM"
    }
}
