package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.UserProfile
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

class UserRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun ensureProfileExists(userId: String, displayName: String): Result<UserProfile> {
        val docRef = db.collection("users").document(userId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = userId, displayName = displayName.take(40).ifBlank { "Wanderer" })
                Result.success(existing)
            } else {
                val safeName = displayName.trim().ifBlank { "Wanderer" }.take(40)
                val payload = mapOf(
                    "userId" to userId,
                    "displayName" to safeName,
                    "selectedClass" to "BLOODBLADE",
                    "soulEssence" to 120L,
                    "highestDepth" to 1L,
                    "totalRuns" to 0L,
                    "totalKills" to 0L,
                    "crimsonMightRank" to 0L,
                    "obsidianWardRank" to 0L,
                    "forbiddenArcanaRank" to 0L,
                    "equippedWeaponId" to "starter_bloodblade",
                    "equippedRelicId" to "starter_ash_signet",
                    "discoveredChronicles" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                val created = docRef.get().await().toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = userId, displayName = safeName)
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateSelectedClass(userId: String, classId: String): Result<Unit> {
        val uid = requireUserId()
        require(uid == userId) { "Cannot update another user's profile." }
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "selectedClass" to classId,
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

    suspend fun upgradeTalent(
        userId: String,
        branchField: String,
        newRank: Int,
        newSoulEssence: Long
    ): Result<Unit> {
        val uid = requireUserId()
        require(uid == userId) { "Cannot update another user's profile." }
        require(branchField in listOf("crimsonMightRank", "obsidianWardRank", "forbiddenArcanaRank"))
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    branchField to newRank.toLong(),
                    "soulEssence" to newSoulEssence.coerceAtLeast(0L),
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

    suspend fun equipLoadout(
        userId: String,
        weaponId: String,
        relicId: String
    ): Result<Unit> {
        val uid = requireUserId()
        require(uid == userId) { "Cannot update another user's profile." }
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "equippedWeaponId" to weaponId,
                    "equippedRelicId" to relicId,
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

    suspend fun recordRunOutcome(
        userId: String,
        soulEarned: Long,
        depthReached: Long,
        kills: Long,
        newChronicles: List<String>
    ): Result<Unit> {
        val uid = requireUserId()
        require(uid == userId) { "Cannot update another user's profile." }
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            val currentEssence = snap.getLong("soulEssence") ?: 0L
            val currentBest = snap.getLong("highestDepth") ?: 1L
            val currentRuns = snap.getLong("totalRuns") ?: 0L
            val currentKills = snap.getLong("totalKills") ?: 0L
            @Suppress("UNCHECKED_CAST")
            val existingChronicles = (snap.get("discoveredChronicles") as? List<String>).orEmpty()
            val mergedChronicles = (existingChronicles + newChronicles).distinct().take(30)

            docRef.update(
                mapOf(
                    "soulEssence" to (currentEssence + soulEarned.coerceAtLeast(0L)),
                    "highestDepth" to maxOf(currentBest, depthReached),
                    "totalRuns" to (currentRuns + 1L),
                    "totalKills" to (currentKills + kills.coerceAtLeast(0L)),
                    "discoveredChronicles" to mergedChronicles,
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
}
