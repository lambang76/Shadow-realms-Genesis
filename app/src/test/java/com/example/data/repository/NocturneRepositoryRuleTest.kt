package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.RaidPlayerState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NocturneRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun userRepository_authenticatedOwner_createsAndUpdatesProfile() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repo = UserRepository(firestore, auth)

        val profileResult = withTimeout(TIMEOUT_MS) {
            repo.ensureProfileExists(uid, "Vespera")
        }
        assertTrue(profileResult.isSuccess)
        assertEquals("Vespera", profileResult.getOrThrow().displayName)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProfile(uid).first { it != null }
        }
        assertNotNull(observed)
        assertEquals(120L, observed?.soulEssence)
    }

    @Test
    fun userRepository_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = UserRepository(firestore, auth)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeProfile("some_user_123").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        assertNotNull("Expected PERMISSION_DENIED exception", caught)
        val fsEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    @Test
    fun modRepository_publishAndUpvote_succeedsAcrossUsers() = runBlocking {
        signInTestUser("modder_${UUID.randomUUID()}@test.com")
        val modderRepo = ModRepository(firestore, auth)
        val modName = "Ashen Blade ${UUID.randomUUID().toString().take(5)}"

        val modId = withTimeout(TIMEOUT_MS) {
            modderRepo.publishMod(
                authorName = "Kael",
                modType = "WEAPON",
                name = modName,
                loreEn = "Tempered in cinders.",
                loreId = "Ditempa dalam bara.",
                effectTag = "SOULBURN",
                powerValue = 40,
                speedValue = 115,
                accentHex = "#D4A359",
                isPublished = true
            ).getOrThrow()
        }

        val published = withTimeout(FLOW_TIMEOUT_MS) {
            modderRepo.observePublishedMods().first { list -> list.any { it.id == modId } }
        }
        val createdMod = published.first { it.id == modId }
        assertTrue(createdMod.isPublished)

        // Second user upvotes
        signInTestUser("player_${UUID.randomUUID()}@test.com")
        val playerRepo = ModRepository(firestore, auth)
        val upvoteResult = withTimeout(TIMEOUT_MS) { playerRepo.upvoteMod(createdMod) }
        assertTrue(upvoteResult.isSuccess)
    }

    @Test
    fun raidRepository_createJoinAndSyncTelemetry_succeedsForMembersAndBlocksOutsiders() = runBlocking {
        val hostUid = signInTestUser("host_${UUID.randomUUID()}@test.com")
        val hostRepo = RaidRepository(firestore, auth)

        val lobby = withTimeout(TIMEOUT_MS) {
            hostRepo.createLobby("HostVespera", isPublic = true, activeModIds = emptyList()).getOrThrow()
        }

        val guestUid = signInTestUser("guest_${UUID.randomUUID()}@test.com")
        val guestRepo = RaidRepository(firestore, auth)
        val joined = withTimeout(TIMEOUT_MS) {
            guestRepo.joinLobbyByCode(lobby.inviteCode).getOrThrow()
        }
        assertTrue(guestUid in joined.memberUids)

        val playerSync = withTimeout(TIMEOUT_MS) {
            guestRepo.upsertPlayerState(
                lobby.id,
                RaidPlayerState(
                    playerId = guestUid,
                    displayName = "GuestKael",
                    archetype = "STALKER",
                    posX = 320.0,
                    posY = 420.0,
                    facingAngle = 0.5,
                    hp = 120L,
                    maxHp = 120L,
                    isAttacking = true,
                    pingText = "Boss!",
                    memberUids = joined.memberUids
                )
            )
        }
        assertTrue(playerSync.isSuccess)

        val players = withTimeout(FLOW_TIMEOUT_MS) {
            guestRepo.observeLobbyPlayers(lobby.id, guestUid).first { it.isNotEmpty() }
        }
        assertEquals(guestUid, players.first().playerId)

        // Outsider cannot observe lobby players
        val outsiderUid = signInTestUser("outsider_${UUID.randomUUID()}@test.com")
        val outsiderRepo = RaidRepository(firestore, auth)
        var denied: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                outsiderRepo.observeLobbyPlayers(lobby.id, hostUid).first()
            }
        } catch (e: Exception) {
            denied = e
        }
        assertNotNull("Expected outsider to be denied", denied)
        val fsEx = checkNotNull(denied?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $denied"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        assertTrue(hostUid.isNotEmpty())
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
