package com.vrgames.sniper.render

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.ImageBasedLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightReceiverComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Vector4
import com.vrgames.sniper.core.Ballistics
import com.vrgames.sniper.core.Machine
import com.vrgames.sniper.core.Play
import com.vrgames.sniper.core.Stages
import com.vrgames.sniper.core.Target
import com.vrgames.sniper.core.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * The plain below the perch and the machines on it, under the scope's inner frame. Real meters are
 * drawn at [SCALE], which from the eye looks the same and keeps the depth buffer honest. Bearings run
 * clockwise from straight ahead (-z), as in the rules.
 */
class Field(private val models: Map<String, Entity>, private val tex: Map<String, TextureResource>) {
    val root = Entity()
    private var land = Entity().also { root.addChild(it) }
    private val iblSource = Entity().also { root.addChild(it) }
    private val views = HashMap<Int, Entity>()
    private val cores = HashMap<Int, Entity>()
    private val coreMat = glow(color(1f, 0.35f, 0.15f, 1f)).shared()
    private val socks = ArrayList<Entity>()
    private val sphere = MeshResource.createSphere(1f).shared()
    private val tracerMat = glow(color(1f, 0.85f, 0.5f, 1f)).shared()
    private val tracer = model(sphere, tracerMat).apply { setScale(0.25f); enabled = false }.also { root.addChild(it) }
    private val smoke = Sparks("field_smoke", 180, sphere, glow(color(0.32f, 0.3f, 0.28f, 0.55f)).shared()).also { root.addChild(it.entity) }
    private val fire = Sparks("field_fire", 120, sphere, glow(color(1f, 0.6f, 0.2f, 1f)).shared()).also { root.addChild(it.entity) }
    private val dust = Sparks("field_dust", 60, sphere, glow(color(0.75f, 0.62f, 0.45f, 0.6f)).shared()).also { root.addChild(it.entity) }
    private val parts = Sparks("field_parts", 90, MeshResource.createBox(Vector3(1f, 1f, 1f), 0f).shared(), pbr(color(0.25f, 0.26f, 0.28f), metallic = 0.6f, roughness = 0.5f).shared()).also { root.addChild(it.entity) }
    private val wreck = pbr(color(0.05f, 0.05f, 0.05f), roughness = 0.95f).shared()
    private val aimed = glow(color(1f, 0.3f, 0.2f, 0.5f)).shared()
    private var played: Play? = null
    private var clock = 0.0
    private val flying = ArrayList<Flight>()

    private class Flight(val from: Vec3, val to: Vec3, val total: Double) { var t = 0.0 }

    init {
        models["sky"]?.clone(Entity.CloneOptions(true, true))?.let { s ->
            s.setScale(SKY_R / 12f)
            tex["sky"]?.let { t -> setMaterials(s, UnlitMaterial.create().apply { setBaseColorTexture(t) }.shared()) }
            s.setPos(Vector3(0f, 0f, 0f))
            root.addChild(s)
        } ?: root.addChild(model(sphere, unlit(color(0.3f, 0.25f, 0.3f)).apply { setCullingMode(MaterialCullingMode.FRONT) }).apply { setScale(SKY_R) })
        val g = tex["ground"]?.let { t ->
            PhysicallyBasedMaterial.create().apply {
                setBaseColorTexture(t)
                setEmissiveTexture(t)
                setEmissiveColor(color(0.55f, 0.5f, 0.45f))
                setRoughness(1f)
            }.shared()
        } ?: pbr(color(0.42f, 0.34f, 0.26f), roughness = 1f).shared()
        root.addChild(model(groundMesh(), g).apply { setPos(Vector3(0f, (-Stages.PERCH * SCALE).toFloat(), 0f)) })
    }

    private fun groundMesh(): MeshResource {
        val r = 400f
        val rep = 25f
        return MeshResource.createWithMeshModel(
            MeshModel(
                listOf(Vector3(-r, 0f, r), Vector3(r, 0f, r), Vector3(r, 0f, -r), Vector3(-r, 0f, -r)), listOf(0, 1, 2, 0, 2, 3),
                List(4) { Vector3(0f, 1f, 0f) }, List(4) { Vector4(1f, 0f, 0f, 1f) }, listOf(Vector2(0f, rep), Vector2(rep, rep), Vector2(rep, 0f), Vector2(0f, 0f)),
            ),
            BoundingBox(Vector3(-r, -0.01f, -r), Vector3(r, 0.01f, r)), "ground",
        )
    }

    private fun clone(name: String): Entity? = models[name]?.clone(Entity.CloneOptions(true, true))

    private fun setMaterials(e: Entity, m: Material) {
        e.components[ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
        e.getChildren().forEach { setMaterials(it, m) }
    }

    /** A real position (range, bearing, height above the eye) in this frame. */
    private fun at(d: Double, bearing: Double, y: Double) = Vector3((sin(bearing) * d * SCALE).toFloat(), (y * SCALE).toFloat(), (-cos(bearing) * d * SCALE).toFloat())

    fun build(p: Play) {
        played = p
        land.destroy()
        land = Entity().also { root.addChild(it) }
        views.clear()
        cores.clear()
        socks.clear()
        flying.clear()
        tracer.enabled = false
        smoke.clear()
        fire.clear()
        dust.clear()
        parts.clear()
        for (t in p.targets) {
            val tall = t.def.m.tall * SCALE
            val e = clone(t.def.m.model) ?: model(MeshResource.createBox(Vector3(1f, 1f, 1f), 0f), wreck)
            e.setScale(tall.toFloat())
            // These models face +z, already toward the player.
            e.transform().eulerAngles = EulerAngles(0f, 0f, 0f)
            land.addChild(e)
            views[t.id] = e
            // The core: only a hit here counts, so it must be seen through the scope.
            val core = model(sphere, coreMat)
            core.setScale((minOf(t.def.m.coreW, t.def.m.coreT) * 0.55 * SCALE).toFloat())
            land.addChild(core)
            cores[t.id] = core
            place(t)
        }
        scenery(p)
        receive(land)
    }

    /** Industry around the machines, and a windsock beside each so the wind can be read through the scope. */
    private fun scenery(p: Play) {
        val r = java.util.Random(p.stage.id.hashCode().toLong())
        val g = -Stages.PERCH
        fun prop(name: String, d: Double, b: Double, tall: Double, yaw: Float) {
            val e = clone(name) ?: return
            e.setScale((tall * SCALE).toFloat())
            val q = at(d, b, g)
            e.setPos(q)
            e.transform().eulerAngles = EulerAngles(0f, yaw, 0f)
            land.addChild(e)
        }
        repeat(14) {
            val d = 240 + r.nextDouble() * 900
            val b = (r.nextDouble() - 0.5) * 0.5
            when (it % 4) {
                0 -> prop("container", d, b, 3.0, r.nextFloat() * 180)
                1 -> prop("tank", d, b, 9.0, 0f)
                2 -> prop("container", d, b, 3.0, r.nextFloat() * 180)
                else -> prop("tower", d, b, 16.0, 0f)
            }
        }
        for (d in listOf(320.0, 620.0, 920.0)) sock(d, 0.03 * (d / 320.0 - 2))
    }

    private fun sock(d: Double, b: Double) {
        val g = -Stages.PERCH
        val pole = pbr(color(0.7f, 0.7f, 0.72f), metallic = 0.6f).shared()
        val orange = pbr(color(1f, 0.45f, 0.1f), roughness = 0.7f, emissive = color(0.4f, 0.16f, 0.03f)).shared()
        val h = 7f * SCALE.toFloat()
        val base = at(d, b, g)
        land.addChild(model(MeshResource.createBox(Vector3(0.05f, h, 0.05f), 0f), pole).apply { setPos(Vector3(base.x, base.y + h / 2, base.z)) })
        val s = Entity().also { it.setPos(Vector3(base.x, base.y + h, base.z)) }
        // A sock long enough to read at range: it lifts and swings with the wind.
        s.addChild(model(MeshResource.createCone(1.1f, 0.12f), orange).apply {
            setPos(Vector3(0.55f, 0f, 0f))
            transform().eulerAngles = EulerAngles(0f, 0f, 90f)
        })
        land.addChild(s)
        socks += s
    }

    private fun place(t: Target) {
        val e = views[t.id] ?: return
        cores[t.id]?.setPos(at(t.distance - 3.0, t.bearing, t.y))
        val base = t.y - t.def.m.tall / 2
        e.setPos(at(t.distance, t.bearing, base))
        // Whatever moves crosses the view, side-on: facing the way it goes.
        if (t.def.m.speed > 0) e.transform().eulerAngles = EulerAngles(0f, if (t.dir > 0) 90f else -90f, 0f)
    }

    fun sync(p: Play, dt: Double) {
        if (p !== played) return
        clock += dt
        for (t in p.targets) if (!t.dead) place(t)
        // The sock streams out with the wind: limp when there is none, level at about 6 m/s.
        val lift = (kotlin.math.abs(p.wind) / 6.0).coerceIn(0.0, 1.0)
        val droop = (-80 + 75 * lift + 4 * sin(clock * 3)).toFloat()
        for (s in socks) s.transform().eulerAngles = EulerAngles(0f, if (p.wind >= 0) 0f else 180f, droop)
        val it = flying.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.t += dt
            val u = (f.t / f.total).coerceAtMost(1.0)
            tracer.setPos(Vector3((f.from.x + (f.to.x - f.from.x) * u).toFloat(), (f.from.y + (f.to.y - f.from.y) * u).toFloat(), (f.from.z + (f.to.z - f.from.z) * u).toFloat()))
            if (f.t >= f.total) it.remove()
        }
        if (tracer.enabled != flying.isNotEmpty()) tracer.enabled = flying.isNotEmpty()
        smoke.update(dt, 0.15)
        fire.update(dt)
        dust.update(dt, 0.5)
        parts.update(dt, 0.2)
    }

    /** A tracer from the perch along the line of the shot. */
    fun tracer(bearing: Double, elevation: Double, d: Double) {
        val y = elevation * d - Ballistics.dropM(d)
        val to = at(d, bearing, y)
        flying.clear()
        flying += Flight(Vec3(0.0, -0.2, 0.0), Vec3(to.x.toDouble(), to.y.toDouble(), to.z.toDouble()), Ballistics.flight(d))
    }

    fun missAt(d: Double, bearing: Double) {
        val q = at(d, bearing, -Stages.PERCH + 0.5)
        dust.emit(Vec3(q.x.toDouble(), q.y.toDouble(), q.z.toDouble()), 10, 0.8, 1.6, 0.35f, Vec3(0.0, 0.7, 0.0))
    }

    /** A machine bursts into smoke and parts, with no blood, and a dark hull is left. */
    fun destroy(t: Target) {
        cores[t.id]?.enabled = false
        val q = at(t.distance, t.bearing, t.y)
        val v = Vec3(q.x.toDouble(), q.y.toDouble(), q.z.toDouble())
        fire.emit(v, 40, 1.6, 0.9, 0.32f)
        smoke.emit(v, 50, 0.6, 4.5, 0.9f, Vec3(0.0, 0.9, 0.0))
        parts.emit(v, 26, 2.4, 2.4, 0.16f, Vec3(0.0, -1.2, 0.0))
        views[t.id]?.let { e -> setMaterials(e, wreck); e.transform().setScaleVector(Vector3((t.def.m.tall * SCALE).toFloat(), (t.def.m.tall * SCALE * 0.45).toFloat(), (t.def.m.tall * SCALE).toFloat())) }
    }

    fun spark(t: Target) {
        val q = at(t.distance, t.bearing, t.y)
        fire.emit(Vec3(q.x.toDouble(), q.y.toDouble(), q.z.toDouble()), 14, 1.2, 0.5, 0.18f)
    }

    /** The show: rockets over the plain. */
    fun show(dt: Double) {
        val before = clock - dt
        if ((clock * 1.5).toInt() != (before * 1.5).toInt()) {
            val k = (clock * 1.5).toInt()
            val q = at(500.0 + (k * 73 % 300), ((k * 37) % 100) / 100.0 * 0.5 - 0.25, 60.0 + (k * 11 % 80))
            fire.emit(Vec3(q.x.toDouble(), q.y.toDouble(), q.z.toDouble()), 60, 6.0, 1.6, 0.4f)
        }
    }

    fun useIbl(t: TextureResource) {
        iblSource.components.set(ImageBasedLightComponent(ImageBasedLightSource.Single(t), 7f))
        receive(root)
    }

    private fun receive(e: Entity) {
        if (iblSource.components[ImageBasedLightComponent::class.java] == null) return
        if (e.components[ModelComponent::class.java] != null && e.components[ImageBasedLightReceiverComponent::class.java] == null) {
            e.components.set(ImageBasedLightReceiverComponent(iblSource))
            e.components.set(EnvironmentLightingSettingsComponent(0f))
        }
        e.getChildren().forEach { receive(it) }
    }

    @Suppress("unused") private val keep = Machine.SENTRY

    companion object {
        /** Real meters to scene meters. */
        const val SCALE = 0.2
        const val SKY_R = 420f
    }
}
