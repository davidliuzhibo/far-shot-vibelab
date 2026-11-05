package com.vrgames.sniper.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ShotTest {
    private fun run(s: Stage, error: Double, seed: Long): Play {
        val p = Play(s, seed)
        val m = Marksman(p, error, seed)
        var t = 0.0
        while (!p.over && t < 600) { p.step(Play.STEP) { m.update(Play.STEP) }; t += Play.STEP }
        return p
    }

    @Test
    fun aGoodMarksmanClearsEveryLevel() {
        val bad = ArrayList<String>()
        for (s in Stages.ALL) {
            val p = run(s, 0.15, 5)
            println("${s.id}: won=${p.won} stars=${p.stars()} shots=${p.shots} hits=${p.hits} time=${p.time.toInt()} alarm=${p.alarm.toInt()}")
            if (!p.won || p.stars() < 3) bad += "${s.id} won=${p.won} stars=${p.stars()} shots=${p.shots} hits=${p.hits}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun aMarksmanWithAnOrdinaryHandStillWinsMostLevels() {
        val wins = Stages.ALL.count { s -> (1L..5L).count { run(s, 0.8, it).won } >= 3 }
        println("ordinary hand wins $wins of ${Stages.ALL.size}")
        assertTrue("$wins", wins >= Stages.ALL.size - 3)
    }

    @Test
    fun aimingStraightAtFarMachinesMisses() {
        // No holdover at 800 m: it must not work, or the game has no point.
        val s = Stage("x", 0, "", "", listOf(TargetDef(Machine.SENTRY, 800.0, 0.0)), 0.0, 0.0, 5, 99.0)
        val p = Play(s)
        p.holdBreath()
        p.fire(0.0, 0.0)
        var hit = false
        repeat(200) { if (p.step(Play.STEP).any { it is Ev.Hit }) hit = true }
        assertFalse(hit)
    }

    @Test
    fun theTableIsRightAtZero() {
        assertEquals(0.0, Ballistics.dropM(300.0), 1e-9)
        assertTrue(Ballistics.dropMil(500.0) in 1.0..1.5)
        assertTrue(Ballistics.dropMil(1000.0) in 3.8..4.6)
        assertTrue("wind pushes the way it blows", Ballistics.windMil(800.0, 3.0) > 0)
    }

    @Test
    fun aHeldBreathSettlesThenItGetsWorse() {
        val p = Play(Stages.byId("a1"))
        var calm = 0.0
        repeat(600) { p.step(Play.STEP); calm = maxOf(calm, abs(p.swayX)) }
        p.holdBreath()
        var steady = 0.0
        repeat(240) { p.step(Play.STEP); steady = maxOf(steady, abs(p.swayX)) }
        assertTrue("$steady vs $calm", steady < calm / 3)
        repeat(100) { p.step(Play.STEP) }
        assertTrue(p.winded > 0)
        var shaky = 0.0
        repeat(120) { p.step(Play.STEP); shaky = maxOf(shaky, abs(p.swayX)) }
        assertTrue("$shaky vs $calm", shaky > calm)
    }

    @Test
    fun theBoltMustComeBackBetweenShots() {
        val p = Play(Stages.byId("a1"))
        p.fire(0.0, 0.0)
        p.fire(0.0, 0.0)
        assertEquals(1, p.shots)
    }

    @Test
    fun theFirstShotStartsTheAlarmAndItEndsTheLevel() {
        val p = Play(Stages.byId("c1"))
        assertTrue(p.alarm < 0)
        p.fire(-0.5, 0.5)
        assertTrue(p.alarm > 0)
        var lost = false
        repeat(60 * 60) { if (!p.over && p.step(Play.STEP).any { it is Ev.Lost }) lost = true }
        assertTrue(lost)
    }

    @Test
    fun aMovingMachineNeedsLead() {
        val s = Stage("x", 0, "", "", listOf(TargetDef(Machine.TRUCK, 800.0, 0.0, 0.0, 200.0)), 0.0, 0.0, 9, 99.0)
        val p = Play(s)
        // Straight at where it is now, with the drop right but no lead: 6 m/s for 0.9 s is more than half its length.
        p.step(0.5)
        val t = p.targets[0]
        p.fire(t.bearing, Ballistics.dropM(800.0) / 800 - p.swayY / 1000)
        var hit = false
        repeat(120) { if (p.step(Play.STEP).any { it is Ev.Hit }) hit = true }
        assertFalse("no lead, no hit", hit)
    }
}
