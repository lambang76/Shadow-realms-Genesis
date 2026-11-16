package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.catalog.GameCatalog
import com.example.data.model.AppLanguage
import com.example.data.model.CommunityMod
import com.example.data.model.RaidLobby
import com.example.data.model.UserProfile
import com.example.data.repository.DungeonAiRepository
import com.example.data.repository.ModAiReview
import com.example.data.repository.ModRepository
import com.example.data.repository.RaidRepository
import com.example.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiActionState {
    data object Idle : AiActionState
    data object Loading : AiActionState
    data class Success(val review: ModAiReview) : AiActionState
    data class Error(val message: String) : AiActionState
}

class SanctumViewModel(
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val userRepository: UserRepository,
    private val modRepository: ModRepository,
    private val raidRepository: RaidRepository,
    private val aiRepository: DungeonAiRepository
) : ViewModel() {

    val profileState: StateFlow<UiState<UserProfile>> =
        userRepository.observeProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile>> { profile ->
                if (profile != null) UiState.Success(profile)
                else UiState.Loading
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Error observing profile", root)
                emit(UiState.Error(root.message ?: "Failed to load sanctum profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val communityModsState: StateFlow<UiState<List<CommunityMod>>> =
        modRepository.observePublishedMods()
            .map<List<CommunityMod>, UiState<List<CommunityMod>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Error observing mods", root)
                emit(UiState.Error(root.message ?: "Failed to load community mods"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val publicLobbiesState: StateFlow<UiState<List<RaidLobby>>> =
        raidRepository.observePublicWaitingLobbies()
            .map<List<RaidLobby>, UiState<List<RaidLobby>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Error observing lobbies", root)
                emit(UiState.Error(root.message ?: "Failed to load global raids"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _activeModIds = MutableStateFlow<Set<String>>(emptySet())
    val activeModIds: StateFlow<Set<String>> = _activeModIds.asStateFlow()

    private val _activeLobby = MutableStateFlow<RaidLobby?>(null)
    val activeLobby: StateFlow<RaidLobby?> = _activeLobby.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _modAiState = MutableStateFlow<AiActionState>(AiActionState.Idle)
    val modAiState: StateFlow<AiActionState> = _modAiState.asStateFlow()

    private var lobbyObserveJob: Job? = null

    init {
        viewModelScope.launch {
            userRepository.ensureProfileExists(currentUserId, initialDisplayName)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun selectClass(classId: String) {
        viewModelScope.launch {
            userRepository.updateSelectedClass(currentUserId, classId)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun equipLoadout(weaponId: String, relicId: String) {
        viewModelScope.launch {
            userRepository.equipLoadout(currentUserId, weaponId, relicId)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun upgradeTalent(branchField: String, currentRank: Long, currentEssence: Long) {
        val cost = ((currentRank + 1L) * 40L)
        if (currentRank >= 10L || currentEssence < cost) return
        viewModelScope.launch {
            userRepository.upgradeTalent(
                userId = currentUserId,
                branchField = branchField,
                newRank = (currentRank + 1L).toInt(),
                newSoulEssence = currentEssence - cost
            ).onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun toggleModActive(modId: String) {
        val current = _activeModIds.value.toMutableSet()
        if (modId in current) current.remove(modId)
        else if (current.size < 5) current.add(modId)
        _activeModIds.value = current
    }

    fun reviewModWithAi(
        modType: String,
        name: String,
        effectTag: String,
        powerValue: Int,
        speedValue: Int,
        language: AppLanguage
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _modAiState.value = AiActionState.Loading
            aiRepository.reviewModBalanceAndLore(
                modType = modType,
                name = name,
                effectTag = effectTag,
                powerValue = powerValue,
                speedValue = speedValue,
                language = language
            ).onSuccess { review ->
                _modAiState.value = AiActionState.Success(review)
            }.onFailure { err ->
                _modAiState.value = AiActionState.Error(err.localizedMessage ?: "AI review failed.")
            }
        }
    }

    fun resetModAiState() {
        _modAiState.value = AiActionState.Idle
    }

    fun publishMod(
        authorName: String,
        modType: String,
        name: String,
        loreEn: String,
        loreId: String,
        effectTag: String,
        powerValue: Int,
        speedValue: Int,
        accentHex: String,
        onPublished: () -> Unit
    ) {
        viewModelScope.launch {
            modRepository.publishMod(
                authorName = authorName,
                modType = modType,
                name = name,
                loreEn = loreEn,
                loreId = loreId,
                effectTag = effectTag,
                powerValue = powerValue,
                speedValue = speedValue,
                accentHex = accentHex,
                isPublished = true
            ).onSuccess { modId ->
                toggleModActive(modId)
                _modAiState.value = AiActionState.Idle
                onPublished()
            }.onFailure {
                _statusBanner.value = it.localizedMessage
            }
        }
    }

    fun upvoteMod(mod: CommunityMod) {
        viewModelScope.launch {
            modRepository.upvoteMod(mod)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun createPrivateLobby(hostName: String) {
        viewModelScope.launch {
            raidRepository.createLobby(
                hostName = hostName,
                isPublic = false,
                activeModIds = _activeModIds.value.toList()
            ).onSuccess { lobby ->
                observeActiveLobby(lobby.id)
            }.onFailure {
                _statusBanner.value = it.localizedMessage
            }
        }
    }

    fun findGlobalMatch(playerName: String) {
        viewModelScope.launch {
            raidRepository.findOrCreateGlobalMatch(
                playerName = playerName,
                activeModIds = _activeModIds.value.toList()
            ).onSuccess { lobby ->
                observeActiveLobby(lobby.id)
            }.onFailure {
                _statusBanner.value = it.localizedMessage
            }
        }
    }

    fun joinLobbyByCode(code: String) {
        viewModelScope.launch {
            raidRepository.joinLobbyByCode(code)
                .onSuccess { lobby ->
                    observeActiveLobby(lobby.id)
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage
                }
        }
    }

    fun observeActiveLobby(lobbyId: String) {
        lobbyObserveJob?.cancel()
        lobbyObserveJob = viewModelScope.launch {
            raidRepository.observeLobby(lobbyId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    _statusBanner.value = (fsErr ?: error).localizedMessage
                }
                .collect { lobby ->
                    _activeLobby.value = lobby
                }
        }
    }

    fun leaveActiveLobby() {
        val lobby = _activeLobby.value ?: return
        lobbyObserveJob?.cancel()
        _activeLobby.value = null
        viewModelScope.launch {
            raidRepository.leaveLobby(lobby)
        }
    }

    fun allAvailableWeapons(communityMods: List<CommunityMod>): List<CommunityMod> =
        GameCatalog.starterWeapons + communityMods.filter { it.modType == "WEAPON" }

    fun allAvailableRelics(communityMods: List<CommunityMod>): List<CommunityMod> =
        GameCatalog.starterRelics + communityMods.filter { it.modType == "RELIC" }

    private companion object {
        const val TAG = "SanctumVM"
    }
}
