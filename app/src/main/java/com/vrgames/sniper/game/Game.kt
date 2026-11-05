package com.vrgames.sniper.game

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.sniper.core.Ev
import com.vrgames.sniper.core.Marksman
import com.vrgames.sniper.core.Play
import com.vrgames.sniper.core.Stage
import com.vrgames.sniper.core.Stages
import com.vrgames.sniper.render.Field
import com.vrgames.sniper.render.Music
import com.vrgames.sniper.render.Sfx
import com.vrgames.sniper.render.Sound
import kotlin.math.abs

/** State the stage, its panels and the windows share; see [Play] for the rules. */
object Game {
    private const val PREFS = "sniper"
    private const val TAG = "ShotGame"

    enum class Phase { CARD, INTRO, PLAYING, RESULT, SHOW }

    val lock = Any()
    private var prefs: android.content.SharedPreferences? = null
    val best = LinkedHashMap<String, Int>()
    var play: Play? = null; private set
    var playVersion by mutableIntStateOf(0); private set
    var stage by mutableStateOf(Stages.ALL[0]); private set
    var phase by mutableStateOf(Phase.CARD)
    var version by mutableIntStateOf(0); private set
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    var window by mutableStateOf<String?>(null); private set
    private var windowCopy: Any? = null
    var closeWindow: (String) -> Unit = {}
    var menuOpen by mutableStateOf(false)
    var confirmRestart by mutableStateOf(false)
    var musicOn by mutableStateOf(true); private set
    var faceRequest by mutableIntStateOf(0)
    var field: Field? = null
    var sound: Sound? = null
    var music: Music? = null
    var trophiesBefore = 0
    var bot: Marksman? = null
    var timeScale = 1.0
    var debugView by mutableStateOf<String?>(null)
    var debugOpen by mutableStateOf<String?>(null)
    /** The test hook's fixed aim: bearing and elevation in radians. */
    var debugAim: Pair<Double, Double>? = null
    /** Where the head points now, set by the stage each frame. */
    @Volatile var bearing = 0.0
    @Volatile var elevation = 0.0
    var scoped by mutableStateOf(false); private set
    /** The green ring showing where the bullet will land. Off is the hard way, by the table. */
    var assist by mutableStateOf(true); private set
    var rangeText by mutableStateOf("—")
    var spotter by mutableStateOf<String?>(null)
    private var lastSecond = -1

    val stars get() = best.values.sum()
    val maxStars get() = Stages.ALL.size * 3
    val bronze get() = Stages.ALL.all { (best[it.id] ?: 0) > 0 }
    val silver get() = stars >= maxStars * 2 / 3
    val gold get() = stars >= maxStars
    val trophies get() = if (gold) 3 else if (silver) 2 else if (bronze) 1 else 0

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            musicOn = p.getBoolean("music", true)
            assist = p.getBoolean("assist", true)
            p.getString("best", "")!!.split(",").filter { it.contains(":") }.forEach {
                val (k, v) = it.split(":")
                best[k] = v.toIntOrNull() ?: 0
            }
        }
        Log.i(TAG, "stars $stars/$maxStars")
        AutoPilot.register(context)
    }

    fun say(text: String) {
        note = text
        noteVersion++
    }

    fun open(s: Stage): Boolean {
        val k = Stages.ALL.indexOf(s)
        return k == 0 || (best[Stages.ALL[k - 1].id] ?: 0) > 0 || (best[s.id] ?: 0) > 0
    }

    fun start() = intro(Stages.ALL.firstOrNull { (best[it.id] ?: 0) == 0 } ?: Stages.ALL.last())

    private fun fresh(s: Stage) {
        stage = s
        synchronized(lock) { play = Play(s, System.nanoTime() and 0xFFFF) }
        bot = null
        debugAim = null
        spotter = null
        scoped = false
        playVersion++
    }

    fun intro(s: Stage) {
        if (!open(s)) {
            say("这一关还没开放：先过前面的。")
            return
        }
        fresh(s)
        phase = Phase.INTRO
        version++
    }

    fun begin() {
        fresh(stage)
        phase = Phase.PLAYING
        music?.resume()
        Log.i(TAG, "begin ${stage.id}")
        say("捏一下开镜。")
        version++
    }

    fun retry() = begin()

    fun next() {
        val k = Stages.ALL.indexOf(stage)
        if (k + 1 < Stages.ALL.size) intro(Stages.ALL[k + 1]) else toCard()
    }

    fun toCard() {
        scoped = false
        phase = Phase.CARD
        version++
    }

    fun toggleAssist() {
        assist = !assist
        prefs?.edit()?.putBoolean("assist", assist)?.apply()
        version++
    }

    fun setScope(on: Boolean) {
        if (phase != Phase.PLAYING && on) return
        if (scoped != on) sound?.play(Sfx.SCOPE)
        scoped = on
        version++
    }

    /** Any pinch off the panels: raise the scope, then hold the breath, then fire. */
    fun pinch() {
        val p = play ?: return
        if (phase != Phase.PLAYING) return
        if (!scoped) {
            setScope(true)
            return
        }
        synchronized(lock) {
            if (p.holding || p.winded > 0) p.fire(bearing, elevation) else p.holdBreath()
        }
        version++
    }

    fun tick(dt0: Double) {
        val dt = dt0 * timeScale
        val p = play ?: return
        if (phase == Phase.SHOW) field?.show(dt)
        if (phase != Phase.PLAYING) return
        val b = bot
        val evs = synchronized(lock) {
            if (b != null) p.step(dt) {
                // The bot's aim is the scope's: on the emulator the view shows the shot it takes.
                p.targets.firstOrNull { !it.dead }?.let { t -> debugAim = Marksman.solution(p, t); bearing = debugAim!!.first; elevation = debugAim!!.second }
                b.update(Play.STEP)
            } else p.step(dt)
        }
        var changed = false
        for (e in evs) {
            changed = true
            when (e) {
                is Ev.Note -> say(e.text)
                Ev.Fired -> {
                    sound?.play(Sfx.SHOT)
                    val t = p.under(bearing, elevation, 60.0)
                    field?.tracer(bearing, elevation, t?.distance ?: 800.0)
                }
                Ev.Dry -> sound?.play(Sfx.DRY)
                Ev.BreathIn -> sound?.play(Sfx.BREATH)
                Ev.BreathOut -> { sound?.play(Sfx.EXHALE); say("憋不住了！缓一会儿。") }
                is Ev.Miss -> onMiss(e)
                is Ev.Hit -> onHit(p, e)
                Ev.AlarmOn -> { sound?.play(Sfx.ALARM); say("警报！抓紧打完。") }
                Ev.Won -> finish(true)
                Ev.Lost -> finish(false)
            }
        }
        val second = p.time.toInt()
        if (changed || second != lastSecond) version++
        lastSecond = second
    }

    /** Refreshes the rangefinder's reading, from the stage's frame. */
    fun rangefinder() {
        val p = play ?: return
        val t = p.under(bearing, elevation)
        val text = if (t != null) "${t.def.m.title}　${t.distance.toInt()} 米" else "—"
        if (text != rangeText) rangeText = text
    }

    private fun onMiss(e: Ev.Miss) {
        sound?.play(Sfx.BOLT)
        field?.missAt(e.distance, e.bearing)
        val mx = e.offX / e.distance * 1000
        val my = e.offY / e.distance * 1000
        val a = if (abs(mx) >= 0.5) (if (mx > 0) "偏右" else "偏左") + " %.1f 格".format(abs(mx)) else ""
        val b = if (abs(my) >= 0.5) (if (my > 0) "偏高" else "偏低") + " %.1f 格".format(abs(my)) else ""
        spotter = "观察员：" + listOf(a, b).filter { it.isNotEmpty() }.joinToString("，").ifEmpty { "擦边了" }
    }

    private fun onHit(p: Play, e: Ev.Hit) {
        val t = p.targets[e.id]
        if (e.destroyed) {
            sound?.play(Sfx.BLAST)
            field?.destroy(t)
            spotter = "观察员：${t.def.m.title}，摧毁。"
        } else {
            sound?.play(Sfx.HIT)
            field?.spark(t)
            spotter = "观察员：打中了，还没倒。"
        }
    }

    private fun finish(won: Boolean) {
        val p = play ?: return
        trophiesBefore = trophies
        if (won) {
            val s = p.stars()
            if (s > (best[p.stage.id] ?: 0)) best[p.stage.id] = s
        }
        save()
        scoped = false
        phase = Phase.RESULT
        sound?.play(if (won) Sfx.FANFARE else Sfx.LOSE)
        Log.i(TAG, "finish ${p.stage.id} won=$won stars=${p.stars()} shots=${p.shots} hits=${p.hits} time=${"%.1f".format(p.time)}")
        version++
    }

    fun show() {
        if (!gold && !AutoPilot.debug) return
        scoped = false
        phase = Phase.SHOW
        sound?.play(Sfx.FANFARE)
        say("收工！")
        version++
    }

    fun endShow() = toCard()

    private fun save() {
        prefs?.edit()?.putString("best", best.entries.joinToString(",") { "${it.key}:${it.value}" })?.apply()
    }

    fun restart() {
        best.clear()
        save()
        fresh(Stages.ALL[0])
        phase = Phase.CARD
        version++
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean("music", musicOn)?.apply()
    }

    fun grant(n: Int) {
        for (s in Stages.ALL) best[s.id] = n
        save()
        version++
    }

    fun windowShown(id: String, copy: Any? = null) {
        window = id
        if (copy != null) windowCopy = copy
        version++
    }

    fun windowGone(id: String, copy: Any? = null) {
        if (copy != null && copy !== windowCopy) return
        if (window == id) window = null
        version++
    }
}
