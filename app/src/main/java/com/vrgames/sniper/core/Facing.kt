package com.vrgames.sniper.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Which way the scene faces, in degrees about y. It is decided once at the start and never moves again
 * by itself: turning the head does not move the scene. Only the menu's 摆到我正前方 places it anew.
 *
 * The first readings after launch are not used, and it waits for a steady second of them. The one
 * time placement that averaged the very first second put ThunderPinball about 30 degrees off on the
 * device, with the player facing straight ahead. Exact cause not found; see docs/开发经验清单.md.
 */
class Facing {
    var yaw = 0.0; private set
    var placed = false; private set
    private val times = ArrayList<Double>()
    private val heads = ArrayList<Double>()
    private var clock = 0.0
    private var wait = WAIT_S

    /** The flat direction the scene faces, as (x, z) in room coordinates. */
    val forwardX get() = -sin(Math.toRadians(yaw))
    val forwardZ get() = -cos(Math.toRadians(yaw))

    /** The menu asked for it: the player is settled by now, so there is no need to wait. */
    fun again() {
        placed = false
        times.clear()
        heads.clear()
        clock = 0.0
        wait = 0.0
    }

    /** One frame. [head] is the head's heading, or null if it cannot be read. True on the frame it is placed. */
    fun update(head: Double?, dt: Double): Boolean {
        if (placed || head == null) return false
        clock += dt
        if (clock < wait) return false
        times += clock
        heads += head
        while (times.first() < clock - WINDOW_S) { times.removeAt(0); heads.removeAt(0) }
        val mean = Math.toDegrees(atan2(heads.sumOf { sin(Math.toRadians(it)) }, heads.sumOf { cos(Math.toRadians(it)) }))
        val steady = clock - times.first() >= WINDOW_S * 0.9 && heads.all { abs(wrap(it - mean)) <= STEADY_DEG }
        if (!steady && clock < GIVE_UP_S) return false
        yaw = wrap(mean)
        placed = true
        return true
    }

    companion object {
        const val WAIT_S = 1.0
        const val WINDOW_S = 1.0
        const val STEADY_DEG = 3.0
        const val GIVE_UP_S = 5.0

        /** The heading of a forward vector (x, z in the room): the yaw that turns -z onto it. */
        fun heading(fx: Double, fz: Double) = Math.toDegrees(atan2(-fx, -fz))

        fun wrap(deg: Double): Double {
            var d = deg % 360.0
            if (d > 180) d -= 360
            if (d <= -180) d += 360
            return d
        }
    }
}
