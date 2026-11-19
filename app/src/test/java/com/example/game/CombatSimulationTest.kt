package com.example.game

import com.example.data.catalog.GameCatalog
import com.example.data.model.AppLanguage
import com.example.data.model.UserProfile
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatSimulationTest {

    @Test
    fun playerMovementAndSkillClearsStage() {
        val stage = GameCatalog.fallbackStage(1, AppLanguage.ENGLISH)
        val profile = UserProfile(
            userId = "test_u",
            displayName = "Vespera",
            selectedClass = "BLOODBLADE",
            crimsonMightRank = 10L
        )
        val sim = CombatSimulation(
            stage = stage,
            profile = profile,
            equippedWeapon = GameCatalog.starterWeapons.first(),
            equippedRelic = GameCatalog.starterRelics.first(),
            activeRulesets = emptyList(),
            bonusDamagePct = 200,
            bonusSpeedPct = 20,
            currentHpOverride = null,
            onSlashSfx = {},
            onDashSfx = {},
            onHitSfx = {}
        )

        val startY = sim.snapshot().playerY
        sim.setMoveInput(0f, -1f)
        repeat(10) { sim.step(0.03f) }
        assertTrue(sim.snapshot().playerY < startY)

        // Move toward enemies and unleash attacks/skills
        repeat(20) {
            sim.setMoveInput(0f, -1f)
            sim.step(0.04f)
            sim.triggerPrimaryAttack()
            sim.triggerSkill1()
            sim.triggerSkill2()
        }
        assertTrue(sim.snapshot().elapsedTime > 0f)
    }

    @Test
    fun weaponSwapAndCustomSkillsWork() {
        val stage = GameCatalog.fallbackStage(1, AppLanguage.ENGLISH)
        val profile = UserProfile(userId = "test_u2", displayName = "Nyx", selectedClass = "REAPER")
        val primary = GameCatalog.starterWeapons[0]
        val secondary = GameCatalog.starterWeapons[1]
        val sim = CombatSimulation(
            stage = stage,
            profile = profile,
            equippedWeapon = primary,
            equippedRelic = GameCatalog.starterRelics.last(),
            activeRulesets = emptyList(),
            bonusDamagePct = 0,
            bonusSpeedPct = 0,
            currentHpOverride = null,
            onSlashSfx = {},
            onDashSfx = {},
            onHitSfx = {},
            secondaryWeapon = secondary,
            skill1Id = "skill1_soul_barrage",
            skill2Id = "skill2_abyssal_vortex"
        )

        assertTrue(!sim.snapshot().usingSecondaryWeapon)
        assertTrue(sim.snapshot().weaponName == primary.name)
        sim.triggerWeaponSwap()
        assertTrue(sim.snapshot().usingSecondaryWeapon)
        assertTrue(sim.snapshot().weaponName == secondary.name)

        sim.triggerSkill1()
        sim.triggerSkill2()
        sim.step(0.05f)
        assertTrue(sim.snapshot().skill1Cooldown > 0f)
        assertTrue(sim.snapshot().skill2Cooldown > 0f)
        assertTrue(sim.snapshot().projectiles.isNotEmpty())
        assertTrue(sim.snapshot().rings.isNotEmpty())
    }

    @Test
    fun playerCanMoveAndAnimateAfterStageCleared() {
        val baseStage = GameCatalog.fallbackStage(1, AppLanguage.ENGLISH)
        val stage = baseStage.copy(
            obstacles = emptyList(),
            enemies = listOf(
                baseStage.enemies.first().copy(
                    spawnXRatio = 0.50f,
                    spawnYRatio = 0.72f,
                    maxHp = 20
                )
            )
        )
        val profile = UserProfile(
            userId = "test_u3",
            displayName = "Vespera",
            selectedClass = "STALKER",
            crimsonMightRank = 50L
        )
        val sim = CombatSimulation(
            stage = stage,
            profile = profile,
            equippedWeapon = GameCatalog.starterWeapons.first(),
            equippedRelic = GameCatalog.starterRelics.first(),
            activeRulesets = emptyList(),
            bonusDamagePct = 500,
            bonusSpeedPct = 0,
            currentHpOverride = null,
            onSlashSfx = {},
            onDashSfx = {},
            onHitSfx = {},
            skill2Id = "skill2_abyssal_vortex"
        )

        sim.triggerSkill2()
        sim.step(0.05f)
        assertTrue("Stage should be cleared", sim.snapshot().stageCleared)

        // Verify player can STILL move smoothly after stageCleared == true
        val clearedX = sim.snapshot().playerX
        val clearedY = sim.snapshot().playerY
        val clearedPhase = sim.snapshot().movePhase
        sim.setMoveInput(1f, -0.5f)
        repeat(8) { sim.step(0.03f) }
        assertTrue(sim.snapshot().playerX != clearedX || sim.snapshot().playerY != clearedY)
        assertTrue(sim.snapshot().isMoving)
        assertTrue(sim.snapshot().movePhase > clearedPhase)
    }
}
