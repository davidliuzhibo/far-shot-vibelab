package com.vrgames.sniper.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class FacingTest {
    private fun run(f: Facing, head: Double, seconds: Double) {
        var t = 0.0
        while (t < seconds) { f.update(head, 1.0 / 72); t += 1.0 / 72 }
    }

    @Test
    fun theSceneEndsUpInFrontOfTheHead() {
        // A yaw of h turns -z to (-sin h, -cos h): that must be where the head looks.
        for (h in listOf(0.0, 30.0, -30.0, 90.0, 170.0, -170.0)) {
            val a = Math.toRadians(h)
            assertEquals(h, Facing.heading(-sin(a), -cos(a)), 1e-9)
        }
    }

    @Test
    fun turningTheHeadNeverMovesTheScene() {
        val f = Facing()
        run(f, 0.0, 3.0)
        assertTrue(f.placed)
        for (h in listOf(40.0, -90.0, 170.0)) {
            run(f, h, 5.0)
            assertEquals("the head went to $h", 0.0, f.yaw, 1e-6)
        }
    }

    @Test
    fun theFirstReadingsAfterLaunchAreNotUsed() {
        val f = Facing()
        run(f, 30.0, 0.9) // Wrong readings right after launch.
        run(f, 0.0, 3.0)
        assertTrue(f.placed)
        assertEquals(0.0, f.yaw, 1e-6)
    }

    @Test
    fun itWaitsForASteadySecond() {
        val f = Facing()
        var t = 0.0
        while (t < 3.0) { f.update(40 * sin(t * 6), 1.0 / 72); t += 1.0 / 72 }
        assertFalse(f.placed)
        run(f, 12.0, 1.1)
        assertTrue(f.placed)
        assertEquals(12.0, f.yaw, 1e-6)
    }

    @Test
    fun aHeadThatNeverSettlesStillGetsAScene() {
        val f = Facing()
        var t = 0.0
        while (t < Facing.GIVE_UP_S + 0.1) { f.update(40 * sin(t * 6), 1.0 / 72); t += 1.0 / 72 }
        assertTrue(f.placed)
    }

    @Test
    fun itAveragesAcrossTheBackOfTheCircle() {
        val f = Facing()
        var t = 0.0
        var flip = false
        while (t < 3.0) { f.update(if (flip) 179.0 else -179.0, 1.0 / 72); flip = !flip; t += 1.0 / 72 }
        assertTrue(f.placed)
        assertTrue("${f.yaw}", abs(abs(f.yaw) - 180) < 1.0)
    }

    @Test
    fun theMenuPlacesItAgain() {
        val f = Facing()
        run(f, 0.0, 3.0)
        f.again()
        assertFalse(f.placed)
        run(f, 50.0, 1.1)
        assertTrue(f.placed)
        assertEquals(50.0, f.yaw, 1e-6)
    }

    @Test
    fun itWorksAtALowFrameRate() {
        val f = Facing()
        repeat(4) { f.update(20.0, 1.0) }
        assertTrue(f.placed)
        assertEquals(20.0, f.yaw, 1e-6)
    }
}
