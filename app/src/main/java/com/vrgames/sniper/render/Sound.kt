package com.vrgames.sniper.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.pico.spatial.core.math.Vector3

/** Effects, all made by tools/sfx_synth.py. */
enum class Sfx(val file: String, val volume: Float) {
    SHOT("shot", 0.95f),
    BOLT("bolt", 0.7f),
    DRY("dry", 0.7f),
    SCOPE("scope", 0.5f),
    BREATH("breath", 0.45f),
    EXHALE("exhale", 0.55f),
    HIT("hit", 0.7f),
    BLAST("blast", 0.95f),
    ALARM("alarm", 0.6f),
    FANFARE("fanfare", 0.9f),
    LOSE("lose", 0.8f),
}

/** One emitter per clip, on [parent]: the same sound again restarts it. Positions in the board's frame. */
class Sound(private val parent: Entity) {
    private val emitters = HashMap<Sfx, Entity>()
    private val controllers = HashMap<Sfx, AudioPlayerController>()

    fun load() {
        for (s in Sfx.entries) {
            runCatching {
                val res = AudioResource.load(s.file, "asset://audio/${s.file}.wav", LoadType.FROM_ASSETS)
                val e = Entity()
                e.components.set(ObjectAudioComponent(s.volume))
                parent.addChild(e)
                val c = e.prepareAudio(res)
                if (c.valid) {
                    emitters[s] = e
                    controllers[s] = c
                }
            }.onFailure { android.util.Log.w("ShotSound", "sfx ${s.file}: ${it.message}") }
        }
    }

    fun play(s: Sfx, at: Vector3 = Vector3(0f, 0.05f, 0f)) {
        val c = controllers[s] ?: return
        emitters[s]?.setPos(at)
        runCatching {
            if (c.isPlaying()) c.stop()
            c.seekTo(0)
            c.play()
        }
    }
}
