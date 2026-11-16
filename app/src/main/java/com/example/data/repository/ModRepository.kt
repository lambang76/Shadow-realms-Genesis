package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.CommunityMod
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
import java.util.UUID

class ModRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google before accessing mods.")

    fun observePublishedMods(): Flow<List<CommunityMod>> =
        db.collection("community_mods")
            .whereEqualTo("isPublished", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CommunityMod::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.upvotes }
            }

    fun observeMyMods(userId: String): Flow<List<CommunityMod>> =
        db.collection("community_mods")
            .whereEqualTo("authorUid", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CommunityMod::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun publishMod(
        authorName: String,
        modType: String,
        name: String,
        loreEn: String,
        loreId: String,
        effectTag: String,
        powerValue: Int,
        speedValue: Int,
        accentHex: String,
        isPublished: Boolean
    ): Result<String> {
        val uid = requireUserId()
        val modId = "mod_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("community_mods").document(modId)
        val payload = mapOf(
            "authorUid" to uid,
            "authorName" to authorName.trim().ifBlank { "Forgemaster" }.take(40),
            "modType" to modType,
            "name" to name.trim().take(50),
            "loreEn" to loreEn.trim().ifBlank { "Forged in the Abyss." }.take(400),
            "loreId" to loreId.trim().ifBlank { "Ditempa di dalam Jurang." }.take(400),
            "effectTag" to effectTag.trim().take(40),
            "powerValue" to powerValue.coerceIn(1, 500).toLong(),
            "speedValue" to speedValue.coerceIn(1, 300).toLong(),
            "accentHex" to accentHex.trim().take(12),
            "isPublished" to isPublished,
            "upvotes" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(modId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun upvoteMod(mod: CommunityMod): Result<Unit> {
        requireUserId()
        require(mod.id.isNotBlank()) { "Mod ID cannot be blank" }
        val docRef = db.collection("community_mods").document(mod.id)
        return try {
            docRef.update(
                mapOf(
                    "upvotes" to (mod.upvotes + 1L),
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

    suspend fun deleteMod(modId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("community_mods").document(modId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }
}
