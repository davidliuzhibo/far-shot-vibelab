package com.vrgames.sniper.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Vector4

/**
 * The rifle's scope. The SDK has no second camera, so the far scene itself is magnified: [pivot] sits
 * at the eye, turned along the line of sight, and scales only across it, which from the eye is what
 * a telescope shows. A dark mask with a round hole hides all but the lens. The reticle is drawn to
 * scale: at [POWER] a mil of the real world covers [POWER] mils of the view, so its marks read true.
 *
 * Hierarchy: pivot (eye, sight, scale) → inner (the inverse turn and move) → the far scene.
 */
class Scope {
    val pivot = Entity()
    val inner = Entity().also { pivot.addChild(it) }
    val mask: Entity = model(MeshResource.createBox(Vector3(MASK_W, MASK_W, 0.001f), 0f), maskMaterial()).also { it.enabled = false }
    val reticle: Entity = model(reticleMesh(), reticleMaterial()).also { it.enabled = false }
    /** Where the bullet will land for the machine under the crosshair: drop and wind worked out. */
    val mark: Entity = model(MeshResource.createTorus(MARK_R, MARK_R * 0.16f), glow(color(0.3f, 1f, 0.45f, 1f))).also { it.enabled = false }

    var power = 1.0; private set
    private var target = 1.0

    fun raise(up: Boolean) {
        target = if (up) POWER else 1.0
    }

    val raised get() = target > 1.0
    /** All the way up: only then is the reticle's scale true. */
    val ready get() = power > POWER - 0.2

    fun frame(dt: Double, eye: Vector3, sight: Quat) {
        val k = (dt / EASE_S).coerceAtMost(1.0)
        power += (target - power) * k
        if (kotlin.math.abs(power - target) < 0.01) power = target
        val p = power.toFloat()
        val t = pivot.transform()
        t.setPosition(eye)
        t.setQuaternion(sight)
        t.setScaleVector(Vector3(p, p, 1f))
        val back = sight.conjugate()
        val i = inner.transform()
        i.setQuaternion(back)
        i.setPosition(back.rotateVector(Vector3(-eye.x, -eye.y, -eye.z)))
        val up = power > 1.3
        if (mask.enabled != up) mask.enabled = up
        if (reticle.enabled != ready) reticle.enabled = ready
        val ahead = sight.rotateVector(Vector3(0f, 0f, -MASK_D))
        mask.setPos(Vector3(eye.x + ahead.x, eye.y + ahead.y, eye.z + ahead.z))
        mask.transform().setQuaternion(sight)
        val r = sight.rotateVector(Vector3(0f, 0f, -RETICLE_D))
        reticle.setPos(Vector3(eye.x + r.x, eye.y + r.y, eye.z + r.z))
        reticle.transform().setQuaternion(sight)
    }

    /** Places the landing mark [right] and [down] mils from the middle, or hides it when [show] is false. */
    fun landing(eye: Vector3, sight: Quat, show: Boolean, right: Double, down: Double) {
        val on = show && ready
        if (mark.enabled != on) mark.enabled = on
        if (!on) return
        val k = (0.001 * POWER * MARK_D).toFloat()
        val r = sight.rotateVector(Vector3(right.toFloat() * k, -down.toFloat() * k, -MARK_D))
        mark.setPos(Vector3(eye.x + r.x, eye.y + r.y, eye.z + r.z))
        // The torus lies in its xz plane: stand it up across the line of sight.
        mark.transform().setQuaternion(sight * Quat(0.70710677f, 0f, 0f, 0.70710677f))
    }

    companion object {
        const val MARK_D = 29f
        /** About a mil across on the far scene. */
        const val MARK_R = (0.001f * 6f * 29f) * 1.1f
        const val POWER = 6.0
        const val EASE_S = 0.12
        /**
         * Both stand far out. A headset shows each eye its own picture: with the eyes on a machine far
         * away, a reticle half a meter from the face is seen double and off the target.
         */
        const val MASK_D = 12f
        const val MASK_W = 57.6f
        const val LENS = 0.16f
        const val RETICLE_D = 30f
        /** The reticle covers this many mils from its middle to each edge. */
        const val RETICLE_MILS = 24f
        val RETICLE_W = (2 * RETICLE_MILS * 0.001f * POWER.toFloat() * RETICLE_D)

        private fun maskMaterial(): UnlitMaterial {
            val n = 1024
            val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(0xF4060708.toInt())
            val r = n * LENS / 2
            val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }
            c.drawCircle(n / 2f, n / 2f, r, clear)
            val vignette = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(n / 2f, n / 2f, r, intArrayOf(0x00000000, 0x00000000, 0x77000000), floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawCircle(n / 2f, n / 2f, r, vignette)
            c.drawCircle(n / 2f, n / 2f, r + 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = 0xFF3A3F44.toInt()
            })
            return UnlitMaterial.create(BlendingMode.TRANSPARENT).apply { setBaseColorTexture(TextureResource.create(bmp)) }
        }

        /** A plain quad: a texture made from a bitmap has its top row at v = 1, so the numbers read upright and below the middle. */
        private fun reticleMesh(): MeshResource {
            val h = RETICLE_W / 2
            return MeshResource.createWithMeshModel(
                MeshModel(
                    listOf(Vector3(-h, -h, 0f), Vector3(h, -h, 0f), Vector3(h, h, 0f), Vector3(-h, h, 0f)), listOf(0, 1, 2, 0, 2, 3),
                    List(4) { Vector3(0f, 0f, 1f) }, List(4) { Vector4(1f, 0f, 0f, 1f) }, listOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f)),
                ),
                BoundingBox(Vector3(-h, -h, -0.001f), Vector3(h, h, 0.001f)), "reticle",
            )
        }

        /**
         * On the headset a mil of the real world is only about 7 pixels at this power, so everything is
         * drawn heavy: lines about 1.5 pixels wide, thick posts from 8 mils out to lead the eye in,
         * numbers 2 mils tall on the even marks.
         */
        private fun reticleMaterial(): UnlitMaterial {
            val n = 2048
            val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val mil = n / (2 * RETICLE_MILS)
            val mid = n / 2f
            fun lines(p: Paint, post: Paint) {
                c.drawLine(mid - 8 * mil, mid, mid - 0.6f * mil, mid, p)
                c.drawLine(mid + 0.6f * mil, mid, mid + 8 * mil, mid, p)
                c.drawLine(mid, mid - 8 * mil, mid, mid - 0.6f * mil, p)
                c.drawLine(mid, mid + 0.6f * mil, mid, mid + 8 * mil, p)
                c.drawLine(mid - RETICLE_MILS * mil, mid, mid - 8 * mil, mid, post)
                c.drawLine(mid + 8 * mil, mid, mid + RETICLE_MILS * mil, mid, post)
                c.drawLine(mid, mid - RETICLE_MILS * mil, mid, mid - 8 * mil, post)
                c.drawLine(mid, mid + 8 * mil, mid, mid + RETICLE_MILS * mil, post)
                for (k in 1..7) {
                    val len = if (k % 2 == 0) 0.75f else 0.4f
                    c.drawLine(mid - len * mil, mid + k * mil, mid + len * mil, mid + k * mil, p)
                    c.drawLine(mid + k * mil, mid - len * mil, mid + k * mil, mid + len * mil, p)
                    c.drawLine(mid - k * mil, mid - len * mil, mid - k * mil, mid + len * mil, p)
                }
            }
            lines(
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE0FFE9B0.toInt(); strokeWidth = 16f; strokeCap = Paint.Cap.ROUND },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE0FFE9B0.toInt(); strokeWidth = 44f },
            )
            lines(
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0C0C0C.toInt(); strokeWidth = 9f; strokeCap = Paint.Cap.ROUND },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0C0C0C.toInt(); strokeWidth = 32f },
            )
            c.drawCircle(mid, mid, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF4A30.toInt() })
            val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFE9B0.toInt()
                textSize = 2.1f * mil
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(6f, 0f, 0f, 0xFF000000.toInt())
            }
            // The marks below the middle are the hold-over: they carry the numbers of the table.
            for (k in listOf(2, 4, 6)) c.drawText("$k", mid + 1.1f * mil, mid + k * mil + 0.75f * mil, num)
            return UnlitMaterial.create(BlendingMode.TRANSPARENT).apply { setBaseColorTexture(TextureResource.create(bmp)) }
        }
    }
}
