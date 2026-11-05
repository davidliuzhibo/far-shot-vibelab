package com.vrgames.sniper.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.sniper.GUIDE_ID
import com.vrgames.sniper.LEVELS_ID
import com.vrgames.sniper.core.Stages
import com.vrgames.sniper.game.Game

const val PANEL_CARD = "card"
const val PANEL_INTRO = "intro"
const val PANEL_HUD = "hud"
const val PANEL_RESULT = "result"
const val PANEL_SHOW = "show"
const val PANEL_MENU = "menu"
const val PANEL_NOTE = "note"
const val PANEL_SCOPE = "scope_info"
const val PANEL_SCOPE_BUTTON = "scope_button"

val Ink = Color(0xFFF2EEE6)
val Amber = Color(0xFFFFB347)
val Sky = Color(0xFF9CD3E8)
val Mint = Color(0xFF9FE3A8)
val Dim = Color(0xFFB5B2AA)
val Alarm = Color(0xFFFF7B5C)
private val PanelBg = Color(0xEA14161A)
/** Window backdrop: the system glass can be light, and every text color here is meant for dark. */
val WindowBg = Color(0xF214161A)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

@Composable
private fun PanelBox(c: PhysicalLengthConverter, width: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.016f))
    Column(
        Modifier.width(c.m(width)).clip(shape).background(PanelBg).border(c.m(0.0015f), Color(0x88FFB347), shape).padding(c.m(0.016f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.008f)),
    ) { content() }
}

/** The step that moves the game on. */
@Composable
fun CallToAction(c: PhysicalLengthConverter, text: String, color: Color = Amber, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.014f))
    Text(
        text,
        Modifier.clip(shape).background(color).border(c.m(0.002f), Color(0xFFFFF4E0), shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.024f), vertical = c.m(0.013f)),
        fontSize = c.sz(0.024f),
        color = Color(0xFF1A1420),
    )
}

@Composable
fun Pill(c: PhysicalLengthConverter, text: String, color: Color = Dim, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        text,
        Modifier.clip(shape).border(c.m(0.0015f), color, shape).clickable { onClick() }.padding(horizontal = c.m(0.014f), vertical = c.m(0.008f)),
        fontSize = c.sz(0.017f),
        color = color,
    )
}

private fun stars(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

private fun plural(t: Int) = "$t 台"

/** The wind in words: which way and about how strong, never to the decimal: reading it is the player's job. */
fun windWords(w: Double): String {
    val a = kotlin.math.abs(w)
    val lo = a.toInt()
    if (a < 0.4) return "几乎没风"
    return (if (w > 0) "风向右 → " else "← 风向左 ") + "约 $lo~${lo + 1} 米/秒"
}

@Composable
fun CardPanel(c: PhysicalLengthConverter) {
    PanelBox(c, 0.62f) {
        Game.version // Read in here: the same arguments, so the box would skip its content.
        Text("千米之外", fontSize = c.sz(0.046f), color = Amber)
        Text("荒原上的机器兵站。一支电磁狙击枪，子弹会下坠，风会吹偏，机器还在走。", fontSize = c.sz(0.018f), color = Ink)
        Text("怎么打", fontSize = c.sz(0.02f), color = Mint)
        Text("· 用头瞄准。捏一下开镜，再捏一下屏息（瞄准晃动变小），再捏一下开枪。\n· 只有打中机器上发光的核心才算数。\n· 绿圈是子弹会落的地方：把绿圈压到核心上再开枪。想要硬核，关掉绿圈自己查射表。\n· 第一枪之后警报响起，要在警报结束前打完。", fontSize = c.sz(0.0165f), color = Ink)
        if (Game.stars > 0) Text("已得 ${Game.stars} / ${Game.maxStars} 颗星", fontSize = c.sz(0.016f), color = Mint)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            CallToAction(c, if (Game.stars > 0) "接着打 →" else "就位 →") { Game.start() }
            if (Game.gold) Pill(c, "收工烟花", Amber) { Game.show() }
        }
    }
}

@Composable
fun IntroPanel(c: PhysicalLengthConverter) {
    PanelBox(c, 0.58f) {
        Game.version
        val s = Game.stage
        Text("第 ${Stages.ALL.indexOf(s) + 1} 关 · ${s.title}", fontSize = c.sz(0.034f), color = Amber)
        Text(Stages.ACTS[s.act] + "　子弹 ${s.ammo} 发　警报 ${s.alarm.toInt()} 秒", fontSize = c.sz(0.016f), color = Sky)
        Text(s.ask, fontSize = c.sz(0.0185f), color = Ink)
        for (t in s.targets) Text("○ ${t.m.title}　${t.distance.toInt()} 米", fontSize = c.sz(0.0175f), color = Mint)
        Text("三星：一发不浪费。", fontSize = c.sz(0.015f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            CallToAction(c, "开始 →") { Game.begin() }
            Pill(c, "返回") { Game.toCard() }
        }
    }
}

/** At the side, with the scope down. */
@Composable
fun HudPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    PanelBox(c, 0.3f) {
        Game.version
        val p = Game.play ?: return@PanelBox
        Text("第 ${Stages.ALL.indexOf(p.stage) + 1} 关 · ${p.stage.title}", fontSize = c.sz(0.02f), color = Amber)
        Text("还剩 ${plural(p.alive)}　子弹 ${p.ammo}", fontSize = c.sz(0.018f), color = Ink)
        Text(windWords(p.wind), fontSize = c.sz(0.0165f), color = Sky)
        if (p.alarm >= 0) Text("警报 ${p.alarm.toInt()} 秒", fontSize = c.sz(0.02f), color = Alarm)
        Text("捏一下开镜", fontSize = c.sz(0.018f), color = Mint)
        Pill(c, if (Game.assist) "落点绿圈：开" else "落点绿圈：关（硬核）", if (Game.assist) Mint else Dim) { Game.toggleAssist() }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "射表", Amber) { toggle(GUIDE_ID) }
            Pill(c, "关卡") { toggle(LEVELS_ID) }
            Pill(c, "重来") { Game.retry() }
        }
    }
}

/** Beside the lens, riding with the head: what the rangefinder and the spotter say, and the table. */
@Composable
fun ScopePanel(c: PhysicalLengthConverter) {
    val shape = RoundedCornerShape(c.m(0.006f))
    Column(Modifier.width(c.m(0.135f)).clip(shape).background(Color(0xE00A0B0D)).padding(c.m(0.006f)), verticalArrangement = Arrangement.spacedBy(c.m(0.002f))) {
        Game.version
        val p = Game.play ?: return@Column
        Text(Game.rangeText, fontSize = c.sz(0.0095f), color = Amber)
        Text(windWords(p.wind), fontSize = c.sz(0.0075f), color = Sky)
        Text("子弹 ${p.ammo}　剩 ${p.alive} 台", fontSize = c.sz(0.0075f), color = Ink)
        if (p.alarm >= 0) Text("警报 ${p.alarm.toInt()} 秒", fontSize = c.sz(0.0095f), color = Alarm)
        Text(
            when {
                p.holding -> "屏息 ${p.breath.toInt() + 1} 秒：捏一下开枪"
                p.winded > 0 -> "喘不上气，瞄准在晃"
                p.bolt > 0 -> "拉栓……"
                else -> "捏一下屏息"
            },
            fontSize = c.sz(0.0085f), color = if (p.holding) Mint else Dim,
        )
        Game.spotter?.let { Text(it, fontSize = c.sz(0.0085f), color = Mint) }
        if (Game.assist) Text("把绿圈压到核心上开枪", fontSize = c.sz(0.0085f), color = Mint)
        Text("距离→抬枪刻度", fontSize = c.sz(0.0065f), color = Dim)
        Text("400→0.6 500→1.2 600→1.8\n700→2.4 800→3.0 900→3.6\n1000→4.2", fontSize = c.sz(0.0068f), color = Ink)
        Text("每 1 米/秒风：500→0.2 800→0.36 1000→0.44", fontSize = c.sz(0.006f), color = Ink)
    }
}

/** Just off the reticle: puts the scope down. */
@Composable
fun ScopeButton(c: PhysicalLengthConverter) {
    val shape = RoundedCornerShape(c.m(0.006f))
    Text(
        "关镜",
        Modifier.clip(shape).background(Color(0xE00A0B0D)).border(c.m(0.0008f), Amber, shape).clickable { Game.setScope(false) }.padding(horizontal = c.m(0.008f), vertical = c.m(0.004f)),
        fontSize = c.sz(0.01f), color = Amber,
    )
}

@Composable
fun ResultPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    PanelBox(c, 0.58f) {
        Game.version
        val p = Game.play ?: return@PanelBox
        if (p.won) {
            Text("${p.stage.title}　${stars(p.stars())}", fontSize = c.sz(0.034f), color = Amber)
            Text("开了 ${p.shots} 枪，打中 ${p.hits} 枪。", fontSize = c.sz(0.018f), color = Ink)
            if (p.stars() < 3) Text("三星：一发不浪费。", fontSize = c.sz(0.015f), color = Dim)
        } else {
            Text("${p.stage.title}　任务失败", fontSize = c.sz(0.034f), color = Alarm)
            Text("还剩 ${plural(p.alive)}。看看射表和风，再来一次。", fontSize = c.sz(0.018f), color = Ink)
        }
        val t = Game.trophies
        if (t > Game.trophiesBefore) Text(
            when (t) {
                1 -> "得到铜奖杯！十二关全过。"
                2 -> "得到银奖杯！"
                else -> "得到金奖杯！解锁「收工烟花」。"
            },
            fontSize = c.sz(0.019f), color = Mint,
        )
        Text("总星数 ${Game.stars} / ${Game.maxStars}", fontSize = c.sz(0.015f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            if (p.won && Stages.ALL.indexOf(p.stage) < Stages.ALL.size - 1) CallToAction(c, "下一关 →") { Game.next() }
            Pill(c, "再来一次", Amber) { Game.retry() }
            Pill(c, "关卡", Sky) { toggle(LEVELS_ID) }
            if (Game.gold) Pill(c, "收工烟花", Amber) { Game.show() }
        }
    }
}

@Composable
fun ShowPanel(c: PhysicalLengthConverter) {
    PanelBox(c, 0.4f) {
        Text("收工烟花", fontSize = c.sz(0.034f), color = Amber)
        Text("十二关一发不浪费。这片荒原今晚放烟花。", fontSize = c.sz(0.018f), color = Ink)
        Pill(c, "结束") { Game.endShow() }
    }
}

@Composable
fun NotePanel(c: PhysicalLengthConverter) {
    val n = Game.note ?: return
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(n, Modifier.widthIn(max = c.m(0.7f)).clip(shape).background(PanelBg).padding(c.m(0.012f)), fontSize = c.sz(0.024f), color = Amber)
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    Game.version
    if (!Game.menuOpen) {
        Pill(c, "菜单", Amber) { Game.menuOpen = true }
        return
    }
    PanelBox(c, 0.28f) {
        Game.version
        Pill(c, if (Game.window == LEVELS_ID) "收起关卡" else "关卡（选关、奖杯）", Amber) { toggle(LEVELS_ID) }
        Pill(c, if (Game.window == GUIDE_ID) "收起射表" else "射表和机器") { toggle(GUIDE_ID) }
        if (Game.gold) Pill(c, "收工烟花", Amber) { Game.menuOpen = false; Game.show() }
        Pill(c, "把靶场摆到我正前方") { faceMe() }
        Pill(c, if (Game.musicOn) "音乐：开" else "音乐：关") { Game.toggleMusic() }
        RestartPill(c)
        Pill(c, "退出") { quit() }
        Text("进度自动保存。版本 $version", fontSize = c.sz(0.0125f), color = Dim)
        Pill(c, "收起菜单") { Game.menuOpen = false }
    }
}

@Composable
private fun RestartPill(c: PhysicalLengthConverter) {
    if (Game.confirmRestart) {
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "确定清空进度", Alarm) { Game.confirmRestart = false; Game.menuOpen = false; Game.restart() }
            Pill(c, "算了") { Game.confirmRestart = false }
        }
    } else Pill(c, "清空进度从头开始") { Game.confirmRestart = true }
}

@Composable
fun WindowHeader(id: String, title: String, size: TextUnit) {
    val copy = remember { Any() }
    DisposableEffect(copy) {
        Game.windowShown(id, copy)
        onDispose { Game.windowGone(id, copy) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = size, color = Amber)
        Spacer(Modifier.weight(1f))
        Button(onClick = { Game.closeWindow(id) }) { Text("关闭", fontSize = 22.sp) }
    }
}
