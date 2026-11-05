package com.vrgames.sniper.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The rifle's ballistics. Everything is measured in meters and radians from the player: bearing to
 * the right of straight ahead, elevation up. A mil is a thousandth of a radian: one meter at 1000 m.
 * The rifle is zeroed at [ZERO] meters, so the reticle's middle is true there, and farther out the
 * bullet lands low by [dropMil] and off to the side by [windMil].
 */
object Ballistics {
    const val MUZZLE = 900.0
    const val G = 9.81
    const val ZERO = 300.0
    /** How much of a crosswind the bullet takes on: real bullets drift less than the air moves. */
    const val WIND_TAKE = 0.4

    fun flight(d: Double) = d / MUZZLE

    /** How far the bullet has fallen at [d] meters, relative to the zeroed line, in meters. */
    fun dropM(d: Double): Double {
        val t = flight(d)
        val tz = flight(ZERO)
        return 0.5 * G * t * t - 0.5 * G * tz * tz * d / ZERO
    }

    fun dropMil(d: Double) = dropM(d) / d * 1000

    /** Sideways at [d] meters in a wind of [w] m/s from the left (positive pushes right), in meters. */
    fun driftM(d: Double, w: Double) = w * flight(d) * WIND_TAKE * d / 1000

    fun windMil(d: Double, w: Double) = driftM(d, w) / d * 1000
}

/**
 * [coreW] and [coreT] are the half sizes of the glowing core, meters: only a hit there counts. The rest
 * of the armor shrugs a bullet off, or holding at the middle would do at any range.
 */
enum class Machine(val model: String, val title: String, val wide: Double, val tall: Double, val hp: Int, val speed: Double, val coreW: Double, val coreT: Double, val note: String) {
    SENTRY("sentry", "哨兵", 3.2, 6.0, 1, 0.0, 1.3, 1.5, "双足哨兵，站岗不动。"),
    PATROL("sentry", "巡逻哨兵", 3.2, 6.0, 1, 2.2, 1.3, 1.5, "来回走动：要提前量。子弹飞行也要时间。"),
    DRONE("drone", "无人机", 3.6, 1.6, 1, 4.0, 1.9, 0.9, "小，会飘，高度不定。"),
    WALKER("walker", "四足机甲", 7.5, 7.5, 2, 1.6, 1.8, 1.8, "装甲厚，要打两枪。"),
    TURRET("turret", "自动炮塔", 5.0, 4.0, 2, 0.0, 1.6, 1.2, "打完第一枪它就开始转动。"),
    RADAR("radar", "雷达站", 7.0, 9.0, 1, 0.0, 2.6, 2.8, "报警系统：打掉它，警报时间翻倍。"),
    TRUCK("truck", "无人卡车", 9.0, 3.6, 1, 6.0, 2.8, 1.3, "跑得快，提前量最大。"),
}

class TargetDef(val m: Machine, val distance: Double, val bearingMil: Double, val up: Double = 0.0, val sweepMil: Double = 0.0)

class Target(val id: Int, val def: TargetDef) {
    var hp = def.m.hp
    var bearing = def.bearingMil / 1000
    var dir = 1.0
    var phase = id * 1.7
    val dead get() = hp <= 0
    val distance get() = def.distance
    /** Height of its middle above the player's line of sight, meters. */
    var y = 0.0
    fun halfWide() = def.m.coreW
    fun halfTall() = def.m.coreT
}

/** One level as data. */
class Stage(
    val id: String,
    val act: Int,
    val title: String,
    val ask: String,
    val targets: List<TargetDef>,
    /** The wind, m/s, positive pushing right; it wanders between these. */
    val windLo: Double,
    val windHi: Double,
    val ammo: Int,
    /** Seconds from the first shot until the alarm has every machine in cover: the level is lost. */
    val alarm: Double,
)

class Bullet(val bearing: Double, val elevation: Double, val wind: Double, val target: Target?) {
    var left = 0.0
    val total: Double = 0.0
}

sealed interface Ev {
    data class Note(val text: String) : Ev
    data object Fired : Ev
    data object Dry : Ev
    data object BreathIn : Ev
    data object BreathOut : Ev
    /** Where the bullet landed, for the dust: relative to the machine it was meant for, meters, and how far off in mil. */
    data class Miss(val distance: Double, val bearing: Double, val offX: Double, val offY: Double) : Ev
    data class Hit(val id: Int, val destroyed: Boolean) : Ev
    data object AlarmOn : Ev
    data object Won : Ev
    data object Lost : Ev
}

/** A level being played: the machines, the wind, the breath, the shots in the air. */
class Play(val stage: Stage, seed: Long = 7L) {
    private val rnd = java.util.Random(seed + stage.id.hashCode())
    val targets = stage.targets.mapIndexed { k, d -> Target(k, d).also { it.y = d.up } }
    var ammo = stage.ammo; private set
    var hits = 0; private set
    var shots = 0; private set
    var time = 0.0; private set
    var over = false; private set
    var won = false; private set
    /** The wind now, m/s (positive pushes the bullet right). */
    var wind = (stage.windLo + stage.windHi) / 2; private set
    private var windGoal = wind
    private var windClock = 0.0
    /** Seconds left of held breath, when it is held; then a time of getting it back. */
    var breath = 0.0; private set
    var winded = 0.0; private set
    /** The rifle's bolt being worked: no shot till it is back. */
    var bolt = 0.0; private set
    /** How long until the alarm finishes, once it has started. */
    var alarm = -1.0; private set
    private val flying = ArrayList<Flying>()
    private val events = ArrayList<Ev>()
    /** The aim's wander from breathing, mil. */
    var swayX = 0.0; private set
    var swayY = 0.0; private set
    private var clock = 0.0

    private class Flying(val bearing: Double, val elevation: Double, val wind: Double, val t: Target?, var left: Double)

    val alive get() = targets.count { !it.dead }
    val holding get() = breath > 0

    /** How far the aim wanders with this breath state, mil. */
    private fun swayAmp() = if (holding) STEADY else if (winded > 0) SHAKY else CALM

    /** Hold the breath: the aim settles for a few seconds; hold too long and it gets worse. */
    fun holdBreath() {
        if (over || winded > 0 || holding) return
        breath = BREATH_S
        events += Ev.BreathIn
    }

    /** Which machine a line of sight points at, or the nearest within a few mil: for the rangefinder. */
    fun under(bearing: Double, elevation: Double, withinMil: Double = 8.0): Target? {
        val best = targets.filter { !it.dead }.minByOrNull { offMil(it, bearing, elevation) } ?: return null
        return best.takeIf { offMil(it, bearing, elevation) <= withinMil }
    }

    private fun offMil(t: Target, bearing: Double, elevation: Double): Double =
        hypot(bearing - t.bearing, elevation - t.y / t.distance) * 1000

    /**
     * Fires along the reticle's middle (the line of sight plus the sway). The bullet is judged when
     * it arrives, against where the machine is by then.
     */
    fun fire(bearing: Double, elevation: Double) {
        if (over) return
        if (bolt > 0) return
        if (ammo <= 0) {
            events += Ev.Dry
            return
        }
        ammo--
        shots++
        bolt = BOLT_S
        if (holding) {
            breath = 0.0
            winded = 0.0
        }
        if (alarm < 0) {
            alarm = stage.alarm
            events += Ev.AlarmOn
        }
        val b = bearing + swayX / 1000
        val e = elevation + swayY / 1000
        val t = under(bearing, elevation, 60.0) ?: targets.firstOrNull { !it.dead }
        flying += Flying(b, e, wind, t, Ballistics.flight(t?.distance ?: 800.0))
        events += Ev.Fired
    }

    /** The radar's gone: the alarm has longer to run. */
    private fun radarDown() {
        if (alarm > 0) {
            alarm *= 2
            events += Ev.Note("雷达站毁了！警报时间翻倍。")
        }
    }

    fun step(dt0: Double, between: (() -> Unit)? = null): List<Ev> {
        var left = dt0
        while (left > 1e-9 && !over) {
            val dt = minOf(left, STEP)
            between?.invoke()
            tick(dt)
            left -= dt
        }
        return events.toList().also { events.clear() }
    }

    private fun tick(dt: Double) {
        time += dt
        clock += dt
        if (bolt > 0) bolt -= dt
        if (holding) {
            breath -= dt
            if (breath <= 0) {
                breath = 0.0
                winded = WINDED_S
                events += Ev.BreathOut
            }
        } else if (winded > 0) winded -= dt
        val a = swayAmp()
        swayX = a * sin(clock * 0.9) + a * 0.35 * sin(clock * 2.3 + 1.0)
        swayY = a * 0.8 * sin(clock * 1.35 + 0.6) + a * 0.3 * sin(clock * 3.1)
        // The wind wanders within the level's range.
        windClock -= dt
        if (windClock <= 0) {
            windClock = 6 + rnd.nextDouble() * 8
            windGoal = stage.windLo + (stage.windHi - stage.windLo) * rnd.nextDouble()
        }
        wind += (windGoal - wind) * (dt * 0.25).coerceAtMost(1.0)
        for (t in targets) if (!t.dead) move(t, dt)
        val it = flying.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.left -= dt
            if (f.left <= 0) {
                it.remove()
                land(f)
            }
        }
        if (alarm > 0) {
            alarm -= dt
            if (alarm <= 0 && alive > 0) {
                over = true
                events += Ev.Note("警报响完了，机器全部进了掩体。")
                events += Ev.Lost
                return
            }
        }
        if (alive == 0) {
            over = true
            won = true
            events += Ev.Won
        } else if (ammo <= 0 && flying.isEmpty()) {
            over = true
            events += Ev.Note("子弹打完了。")
            events += Ev.Lost
        }
    }

    private fun move(t: Target, dt: Double) {
        val m = t.def.m
        t.phase += dt
        if (m.speed > 0) {
            val sweep = t.def.sweepMil / 1000
            t.bearing += t.dir * m.speed / t.distance * dt
            if (abs(t.bearing - t.def.bearingMil / 1000) >= sweep) {
                t.dir = if (t.bearing > t.def.bearingMil / 1000) -1.0 else 1.0
            }
        }
        if (m == Machine.DRONE) t.y = t.def.up + 1.2 * sin(t.phase * 0.8)
        // The turret starts to swing once the alarm is on.
        if (m == Machine.TURRET && alarm >= 0) t.bearing = t.def.bearingMil / 1000 + 0.6 / t.distance * sin(t.phase * 1.2)
    }

    private fun land(f: Flying) {
        val t = f.t
        val d = t?.distance ?: 800.0
        // Where the bullet is at that range, as offsets from the zeroed line of sight.
        val x = f.bearing * d + Ballistics.driftM(d, f.wind)
        val y = f.elevation * d - Ballistics.dropM(d)
        if (t != null && !t.dead) {
            val dx = x - t.bearing * d
            val dy = y - t.y
            if (abs(dx) <= t.halfWide() && abs(dy) <= t.halfTall()) {
                t.hp--
                hits++
                events += Ev.Hit(t.id, t.dead)
                if (t.dead && t.def.m == Machine.RADAR) radarDown()
                return
            }
            events += Ev.Miss(d, x / d, dx, dy)
            return
        }
        events += Ev.Miss(d, x / d, 0.0, 0.0)
    }

    /** 3 stars: not a shot wasted. 2: at most two wasted. */
    fun stars(): Int {
        if (!won) return 0
        val wasted = shots - hits
        return if (wasted == 0) 3 else if (wasted <= 2) 2 else 1
    }

    companion object {
        const val STEP = 1.0 / 60
        const val BREATH_S = 5.0
        const val WINDED_S = 3.0
        const val BOLT_S = 1.3
        const val CALM = 0.9
        const val STEADY = 0.12
        const val SHAKY = 2.2
    }
}

/**
 * Shoots as a good marksman would: reads the machine's range and the wind, holds over and leads by
 * the numbers, holds his breath and fires. [errorMil] is his hand's error.
 */
class Marksman(private val p: Play, private val errorMil: Double = 0.2, seed: Long = 3L) {
    private val rnd = java.util.Random(seed)
    private var wait = 0.8

    fun update(dt: Double) {
        if (p.over) return
        wait -= dt
        if (wait > 0 || p.bolt > 0) return
        val t = p.targets.firstOrNull { !it.dead } ?: return
        if (!p.holding) {
            p.holdBreath()
            wait = 0.4
            return
        }
        val (b, e) = solution(p, t)
        p.fire(b + rnd.nextGaussian() * errorMil / 1000, e + rnd.nextGaussian() * errorMil / 1000)
        wait = 1.5
    }

    companion object {
        /** The bearing and elevation that put a bullet on [t], the sway taken out. */
        fun solution(p: Play, t: Target): Pair<Double, Double> {
            val d = t.distance
            val fl = Ballistics.flight(d)
            val moving = t.def.m.speed > 0 && !turning(t, fl)
            val lead = if (moving) t.dir * t.def.m.speed * fl / d else 0.0
            val b = t.bearing + lead - Ballistics.driftM(d, p.wind) / d - p.swayX / 1000
            val e = (t.y + Ballistics.dropM(d)) / d - p.swayY / 1000
            return b to e
        }

        private fun turning(t: Target, fl: Double): Boolean {
            val next = t.bearing + t.dir * t.def.m.speed * fl / t.distance
            return abs(next - t.def.bearingMil / 1000) >= t.def.sweepMil / 1000
        }
    }
}

fun bearingOf(x: Double, z: Double) = atan2(x, -z)
fun unused(a: Double) = cos(a)
