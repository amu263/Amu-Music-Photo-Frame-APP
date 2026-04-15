package com.example.musicframe.image

import java.util.Calendar
import kotlin.random.Random

/**
 * 星座运势计算器
 * 根据生日和当前日期生成运势信息
 */
object HoroscopeCalculator {

    // 星座数据：(名称, 起始月, 起始日, 运势关键词, 幸运色HEX)
    private val zodiacData = listOf(
        Zodiac("白羊座", 3, 21, listOf("冲动", "勇气", "领导力"), 0xFFDC143C.toInt()),
        Zodiac("金牛座", 4, 20, listOf("稳定", "固执", "享受"), 0xFF8B4513.toInt()),
        Zodiac("双子座", 5, 21, listOf("好奇", "多变", "沟通"), 0xFF00CED1.toInt()),
        Zodiac("巨蟹座", 6, 21, listOf("敏感", "温情", "居家"), 0xFFC0C0C0.toInt()),
        Zodiac("狮子座", 7, 23, listOf("自信", "骄傲", "表演"), 0xFFFFD700.toInt()),
        Zodiac("处女座", 8, 23, listOf("完美", "分析", "挑剔"), 0xFFDEB887.toInt()),
        Zodiac("天秤座", 9, 23, listOf("和谐", "犹豫", "社交"), 0xFF708090.toInt()),
        Zodiac("天蝎座", 10, 23, listOf("神秘", "执着", "占有"), 0xFF800080.toInt()),
        Zodiac("射手座", 11, 22, listOf("自由", "乐观", "冒险"), 0xFF4169E1.toInt()),
        Zodiac("摩羯座", 12, 22, listOf("务实", "保守", "责任"), 0xFF2F4F4F.toInt()),
        Zodiac("水瓶座", 1, 20, listOf("独特", "创新", "疏离"), 0xFF00BFFF.toInt()),
        Zodiac("双鱼座", 2, 19, listOf("浪漫", "幻想", "敏感"), 0xFF00FA9A.toInt())
    )

    // 运势等级名称（简洁好玩）
    private val fortuneNames = listOf(
        "摸鱼王",      // 1 - 最差
        "倒霉蛋",      // 2
        "普通人",      // 3 - 一般
        "幸运儿",      // 4
        "欧皇本皇"     // 5 - 最好
    )

    // 搞笑行动建议库（按星座性格分类）
    private val actionTips = mapOf(
        "白羊座" to listOf(
            "冲！今天你就是那条最靓的鲑鱼",
            "别想了，干就完了，奥利给！",
            "今天适合跟老板对线，但建议先想好退路",
            "少说话多干饭，这是你今天最重要的任务"
        ),
        "金牛座" to listOf(
            "今天花钱会开心，但要悠着点",
            "理财有风险，不如买点好吃的",
            "躺平是对今天最大的尊重",
            "稳如老牛，今天别做任何冒险决定"
        ),
        "双子座" to listOf(
            "今天适合在网上冲浪八小时",
            "八卦是你的超能力，但别太明显",
            "同时做三件事，三心二意的完美一天",
            "发消息之前记得看看发给谁了"
        ),
        "巨蟹座" to listOf(
            "今天适合回家蹭饭，或者叫外卖",
            "给家里人打个电话，他们想你了",
            "躲在被窝里是最安全的选择",
            "吃一顿好的，什么烦恼都没了"
        ),
        "狮子座" to listOf(
            "你就是主角，今天也要闪闪发光！",
            "万众瞩目？习惯了，小场面",
            "今天适合发朋友圈秀一下生活",
            "自信放光芒，但别闪到别人眼睛"
        ),
        "处女座" to listOf(
            "今天允许自己邋遢一点，真的",
            "完美是美好的形容词，但放过自己吧",
            "检查三遍东西是正常的，不是强迫症",
            "整理房间能让心情变好，动手吧"
        ),
        "天秤座" to listOf(
            "今天选择困难症会发作，随便选一个吧",
            "纠结要不要做的时候，就不要做",
            "社交场合你就是那个人气王",
            "买东西之前先问问钱包的意见"
        ),
        "天蝎座" to listOf(
            "神秘感是你今天的最佳武器",
            "第六感很准，跟着感觉走",
            "适合策划一些大事，或者追剧",
            "今天谁惹你谁倒霉，悠着点"
        ),
        "射手座" to listOf(
            "今天适合出去浪，在家会发霉",
            "说走就走的旅行？就今天吧",
            "好奇心害死猫，但你是射手，没事",
            "学习新技能？不如学怎么摸鱼"
        ),
        "摩羯座" to listOf(
            "工作是你今天的主旋律，卷起来",
            "老板觉得你很忙，这很好",
            "稳扎稳打，今天不适合创新",
            "存钱计划启动，每一分都要省"
        ),
        "水瓶座" to listOf(
            "今天你就是那个与众不同的天才",
            "正常人不理解的，你懂，很好",
            "独处是你今天的充电方式",
            "突然的灵感要及时记下来"
        ),
        "双鱼座" to listOf(
            "今天适合做梦，万一实现了呢",
            "浪漫是刻在你DNA里的东西",
            "情绪波动有点大，看看甜剧治愈一下",
            "艺术创作运爆棚，写写画画吧"
        )
    )

    /**
     * 获取星座信息
     */
    fun getZodiac(month: Int, day: Int): Zodiac {
        // Day-of-year calculation
        val daysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val monthStarts = IntArray(13)
        for (i in 2..12) {
            monthStarts[i] = monthStarts[i - 1] + daysInMonth[i - 1]
        }
        val doy = monthStarts[month] + day

        // Capricorn wraps year boundary: Dec 22+ (doy 356+) OR Jan 1-19 (doy 1-19)
        if (doy >= 356 || doy <= 19) return zodiacData.find { it.name == "摩羯座" }!!
        if (doy in 20..49) return zodiacData.find { it.name == "水瓶座" }!!
        if (doy in 50..79) return zodiacData.find { it.name == "双鱼座" }!!

        // Standard zodiac boundaries (first day): Aries Mar 21, Taurus Apr 20, etc.
        return zodiacData.find {
            when (it.name) {
                "白羊座" -> doy in 80..109
                "金牛座" -> doy in 110..139
                "双子座" -> doy in 140..170
                "巨蟹座" -> doy in 171..202
                "狮子座" -> doy in 203..233
                "处女座" -> doy in 234..264
                "天秤座" -> doy in 265..294
                "天蝎座" -> doy in 295..325
                "射手座" -> doy in 326..355
                else -> false
            }
        } ?: zodiacData.first()
    }

    /**
     * 获取星座运势
     * @param birthdayMonth 出生月
     * @param birthdayDay 出生日
     * @param year 当前年份
     * @param month 当前月份
     * @param day 当前日期
     */
    fun getHoroscope(
        birthdayMonth: Int,
        birthdayDay: Int,
        year: Int,
        month: Int,
        day: Int
    ): HoroscopeResult {
        val zodiac = getZodiac(birthdayMonth, birthdayDay)
        
        // 用日期作为种子生成伪随机运势
        val seed = year * 1000 + month * 100 + day + zodiac.startMonth * 31 + zodiac.startDay
        val random = Random(seed)
        
        // 运势等级 1-5
        val fortuneLevel = random.nextInt(5) + 1
        
        // 运势名称
        val fortuneName = fortuneNames[fortuneLevel - 1]
        
        // 从星座对应建议库取一条
        val tips = actionTips[zodiac.name] ?: actionTips["白羊座"]!!
        val actionTip = tips[random.nextInt(tips.size)]
        
        // 运势颜色（根据等级：红→橙→黄→绿→紫）
        val fortuneColor = when (fortuneLevel) {
            1 -> 0xFF8B0000.toInt()  // 深红 - 倒霉
            2 -> 0xFFFF6347.toInt()  // 番茄红 - 欠佳
            3 -> 0xFFFFD700.toInt()  // 金黄 - 普通
            4 -> 0xFF32CD32.toInt()  // 绿色 - 顺利
            5 -> 0xFF9932CC.toInt()  // 紫色 - 超级旺
            else -> 0xFFFFD700.toInt()
        }
        
        return HoroscopeResult(
            zodiacName = zodiac.name,
            fortuneLevel = fortuneLevel,
            fortuneName = fortuneName,
            actionTip = actionTip,
            fortuneColor = fortuneColor,
            luckyColor = zodiac.luckyColor
        )
    }

    data class Zodiac(
        val name: String,
        val startMonth: Int,
        val startDay: Int,
        val keywords: List<String>,
        val luckyColor: Int
    )

    data class HoroscopeResult(
        val zodiacName: String,      // 星座名
        val fortuneLevel: Int,        // 运势等级 1-5
        val fortuneName: String,      // 运势简称
        val actionTip: String,        // 行动建议
        val fortuneColor: Int,        // 运势颜色
        val luckyColor: Int           // 幸运色
    )
}
