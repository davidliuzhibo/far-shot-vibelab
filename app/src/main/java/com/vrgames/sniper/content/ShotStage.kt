package com.vrgames.sniper.content

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureEncoding
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.sniper.GUIDE_ID
import com.vrgames.sniper.LEVELS_ID
import com.vrgames.sniper.core.Ballistics
import com.vrgames.sniper.core.Facing
import com.vrgames.sniper.core.Stages
import com.vrgames.sniper.game.AutoPilot
import com.vrgames.sniper.game.Game
import com.vrgames.sniper.render.Field
import com.vrgames.sniper.render.Music
import com.vrgames.sniper.render.Scope
import com.vrgames.sniper.render.Sound
import com.vrgames.sniper.render.color
import com.vrgames.sniper.render.model
import com.vrgames.sniper.render.pbr
import com.vrgames.sniper.render.setPos
import com.vrgames.sniper.render.setScale
import com.vrgames.sniper.render.shared
import com.vrgames.sniper.render.transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

private const val TAG = "ShotStage"

/**
 * The perch: the player sits behind the rifle above the plain, and aims with the head. Any pinch off
 * the panels works the rifle: scope, breath, shot. The far scene hangs under [Scope.inner]; the
 * near things and the panels hang under the room, turned once to the player's heading.
 */
@Composable
fun ShotStage() {
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val head = remember { HMDTrackingProvider() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var perch by remember { mutableStateOf<Perch?>(null) }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }

    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) {
            Game.windowShown(id)
            navigator.openWindowContainer(id)
        }
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.closeWindow = { id ->
            navigator.closeWindowContainer(id)
            Game.windowGone(id)
        }
        val hd = runCatching { head.start() }
        Log.i(TAG, "head ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            runCatching { head.stop() }
            subscription[0]?.cancel()
            Game.closeWindow(LEVELS_ID)
            Game.closeWindow(GUIDE_ID)
            Game.closeWindow = {}
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }
    LaunchedEffect(Game.playVersion, perch) {
        val w = perch ?: return@LaunchedEffect
        val p = Game.play ?: return@LaunchedEffect
        synchronized(Game.lock) { w.field.build(p) }
    }
    LaunchedEffect(Game.trophies, perch) { perch?.trophies(Game.trophies) }
    LaunchedEffect(Game.faceRequest) { if (Game.faceRequest > 0) perch?.faceAgain() }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (n.length > 30) 7000 else 3000)
        if (Game.note == n) Game.note = null
    }
    LaunchedEffect(Game.debugOpen) {
        when (Game.debugOpen ?: return@LaunchedEffect) {
            "levels" -> if (Game.window != LEVELS_ID) toggleWindow(LEVELS_ID)
            "guide" -> if (Game.window != GUIDE_ID) toggleWindow(GUIDE_ID)
            "menu" -> Game.menuOpen = true
            "close" -> Game.window?.let { Game.closeWindow(it) }
        }
        Game.debugOpen = null
    }
    SideEffect { perch?.showPanels() }

    SpatialView(
        Modifier.pointerInput(Unit) {
            detectSpatialPointerEvent(context, TargetEntity.any()) { infos ->
                val w = perch
                if (w != null) for (i in infos) {
                    if (i.isDownEvent() && w.isCatchAll(i.targetedEntity)) main.post { Game.pinch() }
                }
                false
            }
        },
        attachments = {
            AttachmentPanel(PANEL_CARD) { CardPanel(converter) }
            AttachmentPanel(PANEL_INTRO) { IntroPanel(converter) }
            AttachmentPanel(PANEL_HUD) { HudPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_SHOW) { ShowPanel(converter) }
            AttachmentPanel(PANEL_NOTE) { NotePanel(converter) }
            AttachmentPanel(PANEL_SCOPE) { ScopePanel(converter) }
            AttachmentPanel(PANEL_SCOPE_BUTTON) { ScopeButton(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, toggleWindow, faceMe = { Game.faceRequest++ }, quit = { context.findActivity()?.finishAndRemoveTask() }, version = appVersion)
            }
        },
        update = { _, attachments ->
            perch?.let {
                it.attach(attachments)
                it.showPanels()
            }
        },
        initial = { content, attachments ->
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFD9B0).toColor4(), 1400f, true))
                components[com.pico.spatial.core.ecs.TransformComponent::class.java]?.setEulerAngles(EulerAngles(-30f, 40f, 0f))
            })
            val tex = HashMap<String, TextureResource>()
            var ibl: TextureResource? = null
            withContext(Dispatchers.IO) {
                for ((key, file) in listOf("ground" to "ground", "sky" to "sky_base")) {
                    runCatching { TextureResource.load("textures/$file.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }
                        .onSuccess { tex[key] = it }
                        .onFailure { Log.i(TAG, "texture $file: ${it.message}") }
                }
                ibl = runCatching { TextureResource.load("textures/ibl_base.ktx", LoadType.FROM_ASSETS, TextureCreateOption().apply { textureEncoding = TextureEncoding.LINEAR }).shared() }.getOrNull()
            }
            val models = HashMap<String, Entity>()
            for (name in MODELS) {
                runCatching { Entity.loadSuspend("asset://models/$name.glb") }
                    .onSuccess { models[name] = it }
                    .onFailure { Log.i(TAG, "model $name: ${it.message}") }
            }
            val w = Perch(head, models, tex)
            content.addEntity(w.scope.pivot)
            content.addEntity(w.near)
            content.addEntity(w.scope.mask)
            content.addEntity(w.scope.reticle)
            content.addEntity(w.scope.mark)
            content.addEntity(w.headRoot)
            w.attach(attachments)
            ibl?.let { w.field.useIbl(it) }
            Game.field = w.field
            Game.sound = Sound(w.near).also { it.load() }
            perch = w
            Game.play?.let { w.field.build(it) } ?: run { Game.intro(Stages.ALL[0]); Game.toCard(); Game.play?.let { w.field.build(it) } }
            AutoPilot.ready()
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                main.post { Game.tick(dt) }
                synchronized(Game.lock) { w.frame(dt) }
            }
        },
    )
}

private val MODELS = listOf("sky", "rifle", "sentry", "drone", "walker", "turret", "radar", "tank", "tower", "container", "truck", "trophy")

/** The player's place and everything the frame loop drives. */
private class Perch(val head: HMDTrackingProvider, val models: Map<String, Entity>, tex: Map<String, TextureResource>) {
    val scope = Scope()
    val field = Field(models, tex).also { scope.inner.addChild(it.root) }
    /** Near things and panels, in the room turned to the player's heading. */
    val near = Entity()
    /** The parapet, the rifle, the trophies: hidden through the scope, whose mask stands far beyond them. */
    private val bench = Entity().also { near.addChild(it) }
    val headRoot = Entity()
    private val panels = HashMap<String, Entity>()
    private val catchAll = HashSet<Entity>()
    private var eye = Vector3(0f, 1.2f, 0f)
    private var sight = Quat(0f, 0f, 0f, 1f)
    private var lazySight = Quat(0f, 0f, 0f, 1f)
    private var facing = 0f
    private val seat = Facing()
    private var waited = 0.0
    private var shown = false
    private var infoPanel: Entity? = null
    private var buttonPanel: Entity? = null
    private val trophyViews = ArrayList<Entity>()
    private val trophyMats = listOf(
        pbr(color(0.72f, 0.45f, 0.22f), metallic = 1f, roughness = 0.3f), pbr(color(0.8f, 0.82f, 0.86f), metallic = 1f, roughness = 0.22f), pbr(color(1f, 0.78f, 0.3f), metallic = 1f, roughness = 0.2f),
    ).map { it.shared() }
    private val trophyOff = pbr(color(0.08f, 0.08f, 0.1f), roughness = 0.8f).shared()

    init {
        // A big shell of catch-alls around the player: a pinch anywhere off the panels works the rifle.
        for ((at, size) in listOf(
            Vector3(0f, 1f, -8f) to Vector3(40f, 40f, 0.2f), Vector3(0f, 1f, 8f) to Vector3(40f, 40f, 0.2f), Vector3(-8f, 1f, 0f) to Vector3(0.2f, 40f, 40f),
            Vector3(8f, 1f, 0f) to Vector3(0.2f, 40f, 40f), Vector3(0f, -3f, 0f) to Vector3(40f, 0.2f, 40f), Vector3(0f, 9f, 0f) to Vector3(40f, 0.2f, 40f),
        )) {
            val e = Entity()
            e.setPos(at)
            e.components.set(CollisionComponent(listOf(ShapeResource.createBox(size)), PhysicsMaterialResource()))
            e.components.set(InteractableComponent())
            near.addChild(e)
            catchAll += e
        }
        buildBench()
    }

    fun isCatchAll(e: Entity?): Boolean {
        var cur = e
        while (cur != null) {
            if (cur in catchAll) return true
            cur = cur.getParent()
        }
        return false
    }

    private fun setMaterials(e: Entity, m: com.pico.spatial.core.ecs.resource.Material) {
        e.components[com.pico.spatial.core.ecs.ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
        e.getChildren().forEach { setMaterials(it, m) }
    }

    /** The parapet in front of the player, with the rifle resting on it and the trophies at its end. */
    private fun buildBench() {
        val sand = pbr(color(0.32f, 0.27f, 0.2f), roughness = 0.95f).shared()
        val steel = pbr(color(0.16f, 0.17f, 0.19f), metallic = 0.7f, roughness = 0.5f).shared()
        bench.addChild(model(MeshResource.createBox(Vector3(1.9f, 0.82f, 0.5f), 0.02f), steel).apply { setPos(Vector3(0f, 0.41f, -0.75f)) })
        for (k in -3..3) bench.addChild(model(MeshResource.createBox(Vector3(0.26f, 0.09f, 0.34f), 0.03f), sand).apply { setPos(Vector3(k * 0.25f, 0.865f, -0.72f)) })
        models["rifle"]?.clone(Entity.CloneOptions(true, true))?.let { r ->
            r.setScale(Looks.RIFLE_SCALE)
            r.setPos(Vector3(0.08f, 0.91f, -0.62f))
            r.transform().eulerAngles = EulerAngles(0f, Looks.RIFLE_YAW, 0f)
            bench.addChild(r)
        }
        for (k in 0 until 3) {
            val t = models["trophy"]?.clone(Entity.CloneOptions(true, true)) ?: model(MeshResource.createCone(0.1f, 0.03f), steel)
            t.setScale(0.09f)
            t.setPos(Vector3(0.52f + k * 0.11f, 0.91f, -0.7f))
            t.transform().eulerAngles = EulerAngles(0f, -90f, 0f)
            bench.addChild(t)
            trophyViews += t
        }
        trophies(0)
    }

    fun trophies(earned: Int) = trophyViews.forEachIndexed { k, t -> setMaterials(t, if (k < earned) trophyMats[k] else trophyOff) }

    fun faceAgain() = seat.again()

    fun attach(a: SpatialViewAttachments) {
        fun put(id: String, pos: Vector3, pitch: Float, yaw: Float = 0f) {
            val e = a.entity(id) ?: return
            if (panels[id] === e) return
            panels[id] = e
            near.addChild(e)
            e.setPos(pos)
            e.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
        }
        put(PANEL_CARD, Vector3(0f, 1.32f, -1.05f), -4f)
        put(PANEL_INTRO, Vector3(0f, 1.32f, -1.05f), -4f)
        put(PANEL_RESULT, Vector3(0f, 1.32f, -1.05f), -4f)
        put(PANEL_SHOW, Vector3(0f, 1.32f, -1.05f), -4f)
        put(PANEL_NOTE, Vector3(0f, 1.05f, -1.0f), -4f)
        put(PANEL_HUD, Vector3(-0.64f, 1.14f, -0.72f), -8f, 40f)
        put(PANEL_MENU, Vector3(-0.66f, 0.93f, -0.52f), -30f, 42f)
        a.entity(PANEL_SCOPE)?.let { if (infoPanel !== it) { infoPanel = it; headRoot.addChild(it); it.setScale(FAR_PANEL) } }
        a.entity(PANEL_SCOPE_BUTTON)?.let { if (buttonPanel !== it) { buttonPanel = it; headRoot.addChild(it); it.setScale(FAR_PANEL) } }
    }

    fun showPanels() {
        fun show(id: String, on: Boolean) = panels[id]?.let { if (it.enabled != on) it.enabled = on }
        val ph = Game.phase
        val up = Game.scoped
        show(PANEL_CARD, ph == Game.Phase.CARD)
        show(PANEL_INTRO, ph == Game.Phase.INTRO)
        show(PANEL_RESULT, ph == Game.Phase.RESULT)
        show(PANEL_SHOW, ph == Game.Phase.SHOW)
        show(PANEL_HUD, ph == Game.Phase.PLAYING && !up)
        show(PANEL_MENU, !up)
        show(PANEL_NOTE, Game.note != null && !up)
        infoPanel?.let { if (it.enabled != up) it.enabled = up }
        buttonPanel?.let { if (it.enabled != up) it.enabled = up }
    }

    fun frame(dt: Double) {
        val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
        if (pose != null) {
            eye = pose.position
            place(pose.rotation, dt)
        }
        val turn = yawQuat(facing)
        // The test hook's aim stands in for a head; bearings go clockwise, yaw counterclockwise.
        val aim = Game.debugAim
        val wanted = aim?.let { (b, e) -> turn * yawQuat(-Math.toDegrees(b).toFloat()) * pitchQuat(Math.toDegrees(e).toFloat()) } ?: pose?.rotation ?: sight
        // Steadier the more the shooter is settled: holding the breath takes out the head's tremor too.
        val tau = if (Game.play?.holding == true && Game.scoped) 0.32 else if (Game.scoped) 0.16 else 0.04
        sight = if (aim != null) wanted else Quat.slerp(sight, wanted, (1 - exp(-dt / tau)).toFloat()).normalize()
        // The line of sight in the far scene's frame, for the rifle.
        val f = (turn.conjugate() * sight).rotateVector(Vector3(0f, 0f, -1f))
        val b = atan2(f.x, -f.z).toDouble()
        val el = asin(f.y.coerceIn(-1f, 1f)).toDouble()
        if (aim == null) {
            Game.bearing = b
            Game.elevation = el
        }
        // What the player sees sways with the breath: the bullet follows the same sway.
        val p = Game.play
        val swayed = if (p != null && Game.scoped && Game.phase == Game.Phase.PLAYING) sight * yawQuat(-(p.swayX * 0.0573).toFloat()) * pitchQuat((p.swayY * 0.0573).toFloat()) else sight
        scope.raise(Game.scoped)
        scope.frame(dt, eye, swayed)
        if (bench.enabled == Game.scoped) bench.enabled = !Game.scoped
        // The landing mark, when asked for: the machine under the crosshair, and that range's drop and wind.
        val under = if (Game.assist && p != null && Game.scoped) p.under(Game.bearing, Game.elevation, 30.0) else null
        scope.landing(eye, swayed, under != null, under?.let { Ballistics.windMil(it.distance, p!!.wind) } ?: 0.0, under?.let { Ballistics.dropMil(it.distance) } ?: 0.0)
        lazySight = Quat.slerp(lazySight, sight, (1 - exp(-dt / 0.2)).toFloat()).normalize()
        infoPanel?.let {
            val at = lazySight.rotateVector(INFO_AT)
            it.setPos(Vector3(eye.x + at.x, eye.y + at.y, eye.z + at.z))
            it.transform().setQuaternion(lazySight)
        }
        buttonPanel?.let {
            val at = lazySight.rotateVector(BUTTON_AT)
            it.setPos(Vector3(eye.x + at.x, eye.y + at.y, eye.z + at.z))
            it.transform().setQuaternion(lazySight)
        }
        if (p != null) field.sync(p, dt)
        Game.rangefinder()
    }

    /** The scene is placed once and then stays, whatever the head does. It stays hidden until then. */
    private fun place(rot: Quat, dt: Double) {
        if (!shown) {
            waited += dt
            if (seat.placed || waited >= Facing.GIVE_UP_S) shown = true
            near.enabled = shown
            field.root.enabled = shown
        }
        val f = rot.rotateVector(Vector3(0f, 0f, -1f))
        val head = if (hypot(f.x, f.z) < 0.2f) null else Facing.heading(f.x.toDouble(), f.z.toDouble())
        if (seat.update(head, dt)) {
            shown = true
            near.enabled = true
            field.root.enabled = true
            facing = seat.yaw.toFloat()
            near.setPos(Vector3(eye.x, 0f, eye.z))
            near.transform().eulerAngles = EulerAngles(0f, facing, 0f)
            field.root.transform().eulerAngles = EulerAngles(0f, facing, 0f)
            field.root.setPos(Vector3(eye.x, eye.y, eye.z))
            Log.i(TAG, "facing placed at $facing, eye $eye")
        }
    }

    companion object {
        /**
         * Beside the lens at the left, and just off its lower right: 1.5 m out, a distance the eyes can
         * come to without losing the target, at three and a third times the size they were laid out for.
         */
        val INFO_AT = Vector3(-0.72f, -0.03f, -1.5f)
        val BUTTON_AT = Vector3(0.33f, -0.33f, -1.5f)
        const val FAR_PANEL = 3.33f

        fun yawQuat(deg: Float): Quat {
            val h = Math.toRadians(deg / 2.0)
            return Quat(0f, sin(h).toFloat(), 0f, cos(h).toFloat())
        }

        fun pitchQuat(deg: Float): Quat {
            val h = Math.toRadians(deg / 2.0)
            return Quat(sin(h).toFloat(), 0f, 0f, cos(h).toFloat())
        }
    }
}

private object Looks {
    const val RIFLE_SCALE = 0.14f
    const val RIFLE_YAW = 90f
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

