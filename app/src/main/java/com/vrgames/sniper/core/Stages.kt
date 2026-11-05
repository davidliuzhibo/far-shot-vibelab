package com.vrgames.sniper.core

object Stages {
    val ACTS = listOf("第一章 · 校枪", "第二章 · 风和提前量", "第三章 · 千米之外")

    /** The player sits this high above the plain, meters: every machine is seen a little downhill. */
    const val PERCH = 25.0

    private fun t(m: Machine, d: Double, bearing: Double, up: Double = 0.0, sweep: Double = 0.0) = TargetDef(m, d, bearing, -PERCH + m.tall / 2 + up, sweep)

    val ALL = listOf(
        Stage("a1", 0, "零位", "枪在 300 米校零：瞄准中心就打中中心。捏一下开镜，捏一下屏息，再捏一下开枪。", listOf(t(Machine.SENTRY, 300.0, 0.0)), 0.0, 0.0, 5, 60.0),
        Stage("a2", 0, "下坠", "500 米，子弹开始往下掉。看左边的射表：500 米要抬 1.2 个刻度。", listOf(t(Machine.SENTRY, 500.0, -20.0), t(Machine.SENTRY, 500.0, 25.0)), 0.0, 0.0, 5, 60.0),
        Stage("a3", 0, "远一点", "600 和 700 米各一台。先用十字压住它，读出距离，再查表抬枪。", listOf(t(Machine.SENTRY, 600.0, -30.0), t(Machine.SENTRY, 700.0, 30.0)), 0.0, 0.0, 5, 60.0),
        Stage("a4", 0, "炮塔", "炮塔装甲厚，要打两枪。第一枪之后它就开始动。", listOf(t(Machine.TURRET, 550.0, 0.0)), 0.0, 0.0, 5, 50.0),
        Stage("b1", 1, "起风了", "风把子弹往一边吹。看风向袋：往右吹就瞄左边一点。", listOf(t(Machine.SENTRY, 500.0, -15.0), t(Machine.SENTRY, 600.0, 25.0)), 2.0, 3.0, 5, 60.0),
        Stage("b2", 1, "逆风", "风反了，而且更大。", listOf(t(Machine.SENTRY, 650.0, 0.0), t(Machine.TURRET, 500.0, 35.0)), -5.0, -3.0, 6, 60.0),
        Stage("b3", 1, "巡逻", "哨兵在走。子弹飞半秒多，要瞄它前面。", listOf(t(Machine.PATROL, 450.0, 0.0, sweep = 20.0)), 0.0, 1.0, 5, 60.0),
        Stage("b4", 1, "无人机", "小目标，还会飘。屏息等它飘回来再开枪。", listOf(t(Machine.DRONE, 350.0, -20.0, up = 8.0, sweep = 25.0), t(Machine.DRONE, 400.0, 30.0, up = 12.0, sweep = 25.0)), 1.0, 2.0, 6, 70.0),
        Stage("c1", 2, "先打雷达", "雷达站毁了，警报时间翻倍。先想好打哪个。", listOf(t(Machine.RADAR, 800.0, 25.0), t(Machine.SENTRY, 600.0, -30.0), t(Machine.SENTRY, 700.0, -5.0)), 1.0, 3.0, 6, 25.0),
        Stage("c2", 2, "公路", "无人卡车跑得快，但路是直的。", listOf(t(Machine.TRUCK, 650.0, 0.0, sweep = 40.0)), -2.0, 0.0, 5, 70.0),
        Stage("c3", 2, "机甲", "四足机甲打两枪才倒。它还在走，风也不小。", listOf(t(Machine.WALKER, 850.0, 0.0, sweep = 15.0)), 3.0, 5.0, 6, 60.0),
        Stage("c4", 2, "千米之外", "1000 米。下坠 4 个多刻度，风还要算进去。把这一片清干净。", listOf(t(Machine.RADAR, 1000.0, -25.0), t(Machine.WALKER, 950.0, 20.0, sweep = 12.0), t(Machine.PATROL, 800.0, 0.0, sweep = 15.0), t(Machine.DRONE, 600.0, -5.0, up = 18.0, sweep = 15.0)), -4.0, -2.0, 8, 40.0),
    )

    fun byId(id: String) = ALL.first { it.id == id }
}
