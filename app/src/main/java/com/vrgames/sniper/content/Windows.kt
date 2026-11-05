package com.vrgames.sniper.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.vrgames.sniper.GUIDE_ID
import com.vrgames.sniper.LEVELS_ID
import com.vrgames.sniper.core.Ballistics
import com.vrgames.sniper.core.Machine
import com.vrgames.sniper.core.Stages
import com.vrgames.sniper.game.Game

private val CardBg = Color(0x26F2EEE6)

/** 关卡: every level with its stars (pick one to play), and the three trophies. */
@Composable
fun LevelsWindow() {
    Game.version
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WindowHeader(LEVELS_ID, "关卡", 32.sp)
        Text("总星数 ${Game.stars} / ${Game.maxStars}。点一关就能玩。", fontSize = 22.sp, color = Ink)
        Text(
            (if (Game.bronze) "🥉 铜奖杯：十二关全过　" else "铜奖杯：十二关全过（未得）　") +
                (if (Game.silver) "🥈 银奖杯　" else "银奖杯：三分之二的星（未得）　") +
                (if (Game.gold) "🥇 金奖杯：收工烟花" else "金奖杯：全部三星（未得）"),
            fontSize = 21.sp, color = Mint,
        )
        for ((k, s) in Stages.ALL.withIndex()) {
            if (k == 0 || Stages.ALL[k - 1].act != s.act) Text(Stages.ACTS[s.act], fontSize = 25.sp, color = Sky)
            val open = Game.open(s)
            val st = Game.best[s.id] ?: 0
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg)
                    .clickable(enabled = open) { Game.closeWindow(LEVELS_ID); Game.intro(s) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("${k + 1}. ${s.title}", fontSize = 24.sp, color = if (open) Ink else Dim)
                    Text(s.targets.joinToString("，") { "${it.m.title} ${it.distance.toInt()} 米" }, fontSize = 19.sp, color = Dim)
                }
                Spacer(Modifier.weight(1f))
                Text(if (open) "★".repeat(st) + "☆".repeat(3 - st) else "未开放", fontSize = 23.sp, color = Amber)
            }
        }
    }
}

/** 射表和机器: the table, the machines and how to shoot. */
@Composable
fun GuideWindow() {
    Game.version
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WindowHeader(GUIDE_ID, "射表和机器", 32.sp)
        Text("射表（300 米校零）", fontSize = 25.sp, color = Sky)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(14.dp)) {
            Row { Text("距离", fontSize = 21.sp, color = Dim, modifier = Modifier.weight(1f)); Text("抬枪（刻度）", fontSize = 21.sp, color = Dim, modifier = Modifier.weight(1f)); Text("每 1 米/秒风（刻度）", fontSize = 21.sp, color = Dim, modifier = Modifier.weight(1.3f)); Text("飞行（秒）", fontSize = 21.sp, color = Dim, modifier = Modifier.weight(1f)) }
            for (d in 300..1000 step 100) Row {
                Text("$d 米", fontSize = 21.sp, color = Ink, modifier = Modifier.weight(1f))
                Text("%.1f".format(Ballistics.dropMil(d.toDouble())), fontSize = 21.sp, color = Amber, modifier = Modifier.weight(1f))
                Text("%.2f".format(Ballistics.windMil(d.toDouble(), 1.0)), fontSize = 21.sp, color = Sky, modifier = Modifier.weight(1.3f))
                Text("%.2f".format(Ballistics.flight(d.toDouble())), fontSize = 21.sp, color = Ink, modifier = Modifier.weight(1f))
            }
        }
        Text("机器", fontSize = 25.sp, color = Sky)
        for (m in Machine.entries) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(14.dp)) {
            Text(m.title + (if (m.hp > 1) "　要打 ${m.hp} 枪" else ""), fontSize = 23.sp, color = Amber)
            Text(m.note, fontSize = 20.sp, color = Ink)
        }
        for ((t, d) in GUIDE) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(14.dp)) {
                Text(t, fontSize = 24.sp, color = Amber)
                Text(d, fontSize = 20.sp, color = Ink)
            }
        }
    }
}

private val GUIDE = listOf(
    "开枪" to "用头瞄准，手放哪都行。捏第一下开镜，第二下屏息，第三下开枪。屏息最多 5 秒，憋太久瞄准会晃得更厉害。",
    "核心" to "每台机器身上有一个发光的橙色核心，只有打中核心才算数，打在装甲上没用。所以 700 米以外必须抬枪。",
    "绿圈" to "开镜后十字压住一台机器，绿圈就显示子弹会落在哪里，下坠和风都算好了（提前量要自己算）。把绿圈压到核心上开枪就行。关掉绿圈就是硬核玩法。",
    "下坠" to "枪在 300 米校零。再远，子弹就往下掉。瞄准镜中线下方的数字是刻度：查表 800 米抬 3.0，就把「3」那格压在目标上开枪。",
    "风" to "看远处的橙色风向袋：垂着是没风，平飘大约 6 米/秒。风往右吹，子弹就往右偏，要往左瞄：左右刻度压在目标上。",
    "提前量" to "子弹飞行要时间。800 米飞 0.89 秒，一台 6 米/秒的卡车这时已经走了 5 米多。要瞄它前面。",
    "观察员" to "没打中时，观察员会报告偏了多少。按他的话修正下一枪。",
    "警报" to "第一枪之后警报开始倒数，倒数结束任务失败。打掉雷达站，剩余时间翻倍。",
    "星星" to "打完一星；浪费不超过两发两星；一发不浪费三星。",
    "奖杯" to "十二关全过：铜奖杯。拿到三分之二的星：银奖杯。全部三星：金奖杯，解锁「收工烟花」。",
)
