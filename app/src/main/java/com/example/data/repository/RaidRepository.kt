package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.GeneratedStage
import com.example.data.model.RaidEnemyState
import com.example.data.model.RaidLobby
import com.example.data.model.RaidPlayerState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class RaidRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing raids.")

    fun observePublicWaitingLobbies(): Flow<List<RaidLobby>> =
        db.collection("raid_lobbies")
            .whereEqualTo("isPublic", true)
            .whereEqualTo("status", "WAITING")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RaidLobby::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeLobby(lobbyId: String): Flow<RaidLobby?> =
        db.collection("raid_lobbies").document(lobbyId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    RaidLobby::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeLobbyPlayers(lobbyId: String, currentUserId: String): Flow<List<RaidPlayerState>> =
        db.collection("raid_lobbies").document(lobbyId)
            .collection("players")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RaidPlayerState::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeLobbyEnemies(lobbyId: String, currentUserId: String): Flow<List<RaidEnemyState>> =
        db.collection("raid_lobbies").document(lobbyId)
            .collection("enemies")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RaidEnemyState::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun createLobby(
        hostName: String,
        isPublic: Boolean,
        activeModIds: List<String>
    ): Result<RaidLobby> {
        val uid = requireUserId()
        val inviteCode = generateInviteCode()
        val docRef = db.collection("raid_lobbies").document(inviteCode)
        val payload = mapOf(
            "hostUid" to uid,
            "hostName" to hostName.trim().ifBlank { "Host" }.take(40),
            "inviteCode" to inviteCode,
            "isPublic" to isPublic,
            "status" to "WAITING",
            "stageDepth" to 1L,
            "stageTitle" to "Threshold of Ash",
            "biomeTheme" to "CRIMSON_CRYPT",
            "activeModIds" to activeModIds.take(5),
            "memberUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            val lobby = docRef.get().await().toObject(
                RaidLobby::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: RaidLobby(
                id = inviteCode,
                hostUid = uid,
                hostName = hostName,
                inviteCode = inviteCode,
                isPublic = isPublic,
                memberUids = listOf(uid)
            )
            Result.success(lobby)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun joinLobbyByCode(inviteCode: String): Result<RaidLobby> {
        val uid = requireUserId()
        val normalized = inviteCode.trim().uppercase()
        if (normalized.length < 4 || !normalized.matches(Regex("^[A-Z0-9]+$"))) {
            return Result.failure(IllegalArgumentException("Invalid invite code format."))
        }
        val docRef = db.collection("raid_lobbies").document(normalized)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("Sanctum code '$normalized' not found."))
            }
            val lobby = snap.toObject(
                RaidLobby::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Unable to read lobby."))

            if (uid !in lobby.memberUids) {
                if (lobby.status != "WAITING") {
                    return Result.failure(IllegalStateException("Raid is already underway."))
                }
                if (lobby.memberUids.size >= 4) {
                    return Result.failure(IllegalStateException("Sanctum party is full (4/4)."))
                }
                docRef.update(
                    mapOf(
                        "memberUids" to (lobby.memberUids + uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val updated = docRef.get().await().toObject(
                RaidLobby::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: lobby
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun findOrCreateGlobalMatch(
        playerName: String,
        activeModIds: List<String>
    ): Result<RaidLobby> {
        val uid = requireUserId()
        val colRef = db.collection("raid_lobbies")
        return try {
            val openSnap = colRef
                .whereEqualTo("isPublic", true)
                .whereEqualTo("status", "WAITING")
                .limit(10)
                .get()
                .await()

            val candidate = openSnap.toObjects(
                RaidLobby::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).firstOrNull { lobby ->
                uid in lobby.memberUids || lobby.memberUids.size < 4
            }

            if (candidate != null) {
                joinLobbyByCode(candidate.inviteCode)
            } else {
                createLobby(playerName, isPublic = true, activeModIds = activeModIds)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun publishStageToLobby(
        lobbyId: String,
        stage: GeneratedStage,
        status: String
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection("raid_lobbies").document(lobbyId)
        val stageMap = mapOf(
            "depth" to stage.depth.toLong(),
            "title" to stage.title,
            "omenWhisper" to stage.omenWhisper,
            "biomeTheme" to stage.biomeTheme,
            "ambientColorHex" to stage.ambientColorHex,
            "torchColorHex" to stage.torchColorHex,
            "fogIntensity" to stage.fogIntensity.toDouble(),
            "obstacles" to stage.obstacles.map {
                mapOf(
                    "xRatio" to it.xRatio.toDouble(),
                    "yRatio" to it.yRatio.toDouble(),
                    "radius" to it.radius.toDouble(),
                    "kind" to it.kind
                )
            },
            "enemies" to stage.enemies.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "behavior" to it.behavior,
                    "maxHp" to it.maxHp.toLong(),
                    "damage" to it.damage.toLong(),
                    "speed" to it.speed.toDouble(),
                    "radius" to it.radius.toDouble(),
                    "spawnXRatio" to it.spawnXRatio.toDouble(),
                    "spawnYRatio" to it.spawnYRatio.toDouble(),
                    "tintHex" to it.tintHex
                )
            }
        )
        return try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "stageDepth" to stage.depth.toLong(),
                    "stageTitle" to stage.title.take(100),
                    "biomeTheme" to stage.biomeTheme.take(50),
                    "stageData" to stageMap,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateLobbyStatus(lobbyId: String, status: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("raid_lobbies").document(lobbyId)
        return try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun upsertPlayerState(
        lobbyId: String,
        playerState: RaidPlayerState
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("raid_lobbies").document(lobbyId)
            .collection("players").document(uid)
        val payload = mapOf(
            "playerId" to uid,
            "displayName" to playerState.displayName.take(40).ifBlank { "Wanderer" },
            "archetype" to playerState.archetype,
            "posX" to playerState.posX,
            "posY" to playerState.posY,
            "facingAngle" to playerState.facingAngle,
            "hp" to playerState.hp.coerceAtLeast(0L),
            "maxHp" to playerState.maxHp.coerceAtLeast(1L),
            "isAttacking" to playerState.isAttacking,
            "pingText" to playerState.pingText.take(60),
            "memberUids" to playerState.memberUids.take(4),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun syncEnemyState(
        lobbyId: String,
        enemyState: RaidEnemyState
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection("raid_lobbies").document(lobbyId)
            .collection("enemies").document(enemyState.enemyId)
        val payload = mapOf(
            "enemyId" to enemyState.enemyId,
            "name" to enemyState.name.take(80),
            "hp" to enemyState.hp.coerceAtLeast(0L),
            "maxHp" to enemyState.maxHp.coerceAtLeast(1L),
            "posX" to enemyState.posX,
            "posY" to enemyState.posY,
            "isBoss" to enemyState.isBoss,
            "memberUids" to enemyState.memberUids.take(4),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun leaveLobby(lobby: RaidLobby): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("raid_lobbies").document(lobby.id)
        return try {
            if (lobby.hostUid == uid) {
                docRef.delete().await()
            } else if (uid in lobby.memberUids) {
                docRef.update(
                    mapOf(
                        "memberUids" to lobby.memberUids.filter { it != uid },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
