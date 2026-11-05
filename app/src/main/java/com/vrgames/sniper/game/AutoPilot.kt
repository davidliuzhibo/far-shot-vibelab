package com.vrgames.sniper.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.sniper.core.Marksman
import com.vrgames.sniper.core.Stages

/**
 * The test hook (debug builds only): adb shell am broadcast -a com.vrgames.sniper.TEST -p
 * com.vrgames.sniper --es cmd walk. The walk and the [Solver] call the game directly, so they prove
 * the rules and the rendering, not that a pointing hand can reach a cell (ShotTest checks that).
 */
object AutoPilot {
    const val ACTION = "com.vrgames.sniper.TEST"
    const val TAG = "ShotTest"
    private val main = Handler(Looper.getMainLooper())
    var debug = false; private set
    private var at = 0

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        debug = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    fun ready() = Log.i(TAG, "table ready")

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "start" -> Game.start()
            "level" -> id?.let { Stages.ALL.firstOrNull { s -> s.id == it } }?.let { Game.grant(1); Game.intro(it) }
            "begin" -> Game.begin()
            "bot" -> Game.play?.let { Game.bot = Marksman(it) }
            "pinch" -> Game.pinch()
            "scope" -> Game.setScope(id != "off")
            "aim" -> Game.debugAim = id?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
            "walk" -> { main.removeCallbacksAndMessages(null); at = 0; main.postDelayed(::walkStep, 300) }
            "grant" -> Game.grant(id?.toIntOrNull() ?: 3)
            "show" -> Game.show()
            "speed" -> Game.timeScale = id?.toDoubleOrNull() ?: 1.0
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            "open" -> Game.debugOpen = id
            "reset" -> { main.removeCallbacksAndMessages(null); Game.restart() }
            "stop" -> main.removeCallbacksAndMessages(null)
            else -> Log.w(TAG, "unknown command $cmd")
        }
    }

    private fun walkStep() {
        if (at >= Stages.ALL.size) {
            Log.i(TAG, "walk done stars=${Game.stars}/${Game.maxStars} bronze=${Game.bronze} silver=${Game.silver} gold=${Game.gold}")
            return
        }
        val s = Stages.ALL[at++]
        Game.grant(1)
        Game.intro(s)
        main.postDelayed({
            Game.begin()
            Game.setScope(true); Game.play?.let { Game.bot = Marksman(it) }
            waitResult(s.id)
        }, 800)
    }

    private fun waitResult(id: String) {
        if (Game.phase == Game.Phase.RESULT) {
            val p = Game.play
            Log.i(TAG, "result $id won=${p?.won} stars=${p?.stars()} time=${"%.0f".format(p?.time ?: 0.0)} shots=${p?.shots} hits=${p?.hits}")
            main.postDelayed(::walkStep, 1000)
        } else main.postDelayed({ waitResult(id) }, 500)
    }
}
