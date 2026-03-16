package com.rpgenerator.core.domain

import kotlin.test.*

/**
 * Bug 5/6: XP/Level desync.
 *
 * Tests that CharacterSheet.gainXP() produces correct levels and that
 * the level calculation is consistent with xpToNextLevel().
 *
 * The root cause was that RulesEngine predicted level-ups differently
 * from CharacterSheet.gainXP(). These tests verify the canonical
 * level calculation in CharacterSheet is consistent.
 */
class XPLevelSyncTest {

    private fun createSheet(level: Int = 1, xp: Long = 0): CharacterSheet {
        return CharacterSheet(
            level = level,
            xp = xp,
            baseStats = Stats(),
            resources = Resources(100, 100, 50, 50, 100, 100)
        )
    }

    @Test
    fun `level 1 to 2 requires 100 XP`() {
        val sheet = createSheet(level = 1, xp = 0)
        // xpToNextLevel and calculateLevel now use the same formula: (level+1)*50 for levels < 5.
        // At level 1: (1+1)*50 = 100.
        assertEquals(100L, sheet.xpToNextLevel(), "xpToNextLevel at level 1 returns (1+1)*50=100")

        val at99 = sheet.gainXP(99)
        assertEquals(1, at99.level, "99 XP should not level up from level 1")

        val at100 = sheet.gainXP(100)
        assertEquals(2, at100.level, "100 XP should level up to level 2")
    }

    @Test
    fun `gaining exact threshold XP levels up correctly`() {
        // Level 1 needs 100 XP to level up (threshold is level*100 = 100)
        val sheet = createSheet(level = 1, xp = 0)
        val leveled = sheet.gainXP(100)
        assertEquals(2, leveled.level)
        assertEquals(100, leveled.xp)
    }

    @Test
    fun `multiple level ups in single XP gain`() {
        // If we gain enough XP to skip levels, all should be applied
        val sheet = createSheet(level = 1, xp = 0)
        // Level 1->2 needs 100 XP, level 2->3 needs 200 more (accumulated: 300)
        val multiLevel = sheet.gainXP(300)
        assertEquals(3, multiLevel.level, "300 XP from level 1 should reach level 3")
    }

    @Test
    fun `XP total is preserved correctly after gain`() {
        val sheet = createSheet(level = 1, xp = 50)
        val after = sheet.gainXP(25)
        assertEquals(75, after.xp, "XP should accumulate correctly")
        assertEquals(1, after.level, "Should still be level 1")
    }

    @Test
    fun `level up increases stats`() {
        val sheet = createSheet(level = 1, xp = 0)
        val leveled = sheet.gainXP(100) // Level up to 2

        assertTrue(leveled.baseStats.strength > sheet.baseStats.strength,
            "Strength should increase on level up")
        assertTrue(leveled.resources.maxHP > sheet.resources.maxHP,
            "Max HP should increase on level up")
    }

    @Test
    fun `XP change struct should use post-mutation state`() {
        // This simulates the fix: XP applied before building XPChange
        val sheet = createSheet(level = 1, xp = 90)

        val xpGain = 50L
        val levelBefore = sheet.level
        val afterGain = sheet.gainXP(xpGain)
        val levelAfter = afterGain.level
        val didLevelUp = levelAfter > levelBefore

        // With 90+50=140 XP, level 1->2 threshold is at 100 XP
        assertEquals(2, levelAfter, "Should have leveled up")
        assertTrue(didLevelUp, "didLevelUp flag should be true")
        assertEquals(140L, afterGain.xp, "Total XP should be 140")
    }

    @Test
    fun `xpToNextLevel agrees with calculateLevel - bug is fixed`() {
        // Both xpToNextLevel() and calculateLevel() now use the same formula:
        // (level+1)*50 for levels < 5. At level 1, that's 100.
        // The old inconsistency (Bug 5/6) has been fixed.
        val sheet = createSheet(level = 1, xp = 0)

        val xpGain = 100L
        // Prediction using xpToNextLevel: 100 >= 100 = true
        val prediction = (sheet.xp + xpGain) >= sheet.xpToNextLevel()
        // Actual result from gainXP (uses calculateLevel)
        val actual = sheet.gainXP(xpGain)
        val actualLevelUp = actual.level > sheet.level

        assertTrue(prediction, "xpToNextLevel-based prediction says level-up")
        assertTrue(actualLevelUp, "gainXP also levels up")
        // They now agree — the bug is fixed.
        assertEquals(prediction, actualLevelUp,
            "xpToNextLevel prediction and gainXP should agree now that the bug is fixed")
    }

    @Test
    fun `fix verification - XP applied before building XPChange avoids desync`() {
        // This simulates the fixed code path in GameOrchestrator:
        // 1. Apply gainXP FIRST
        // 2. Read level from the RESULT
        // 3. Build XPChange from post-mutation state
        val sheet = createSheet(level = 1, xp = 50)
        val xpGain = 60L // 50+60=110, exceeds level 1→2 threshold of 100

        val levelBefore = sheet.level
        val afterGain = sheet.gainXP(xpGain)
        val levelAfter = afterGain.level
        val didLevelUp = levelAfter > levelBefore

        assertTrue(didLevelUp, "Should detect level-up from post-mutation state")
        assertEquals(2, levelAfter)
        assertEquals(110L, afterGain.xp)
    }
}
