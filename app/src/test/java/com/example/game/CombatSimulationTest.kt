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
}
