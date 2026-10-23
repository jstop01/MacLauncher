package com.jex.maclauncher.widget.screentime

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import java.util.Calendar

/** 오늘 앱 사용 시간 (사용 정보 접근 권한 필요). 이벤트(앞으로/뒤로 간 시각)로 직접 계산 */
object ScreenTime {

    enum class Category(val label: String, val color: Int) {
        SOCIAL("소셜", 0xFF0A84FF.toInt()),
        ENTERTAINMENT("엔터테인먼트", 0xFFFF9F0A.toInt()),
        OTHER("기타", 0xFF64D2FF.toInt()),
    }

    data class AppUsage(val packageName: String, val label: String, val millis: Long)

    /** total: 오늘 합계, hourly[h][category]: 시간대별 분류별 사용(ms), top: 많이 쓴 앱 순 */
    data class Today(val total: Long, val hourly: Array<LongArray>, val top: List<AppUsage>)

    fun today(context: Context): Today {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val pm = context.packageManager
        val ignored = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet() + context.packageName + "com.android.systemui"

        val perApp = HashMap<String, Long>()
        val hourly = Array(24) { LongArray(Category.entries.size) }
        val categoryOf = HashMap<String, Category>()
        val open = HashMap<String, Long>()   // 앞에 나와 있는 앱 → 나온 시각

        fun add(pkg: String, from: Long, to: Long) {
            if (to <= from || pkg in ignored) return
            perApp[pkg] = (perApp[pkg] ?: 0) + (to - from)
            val cat = categoryOf.getOrPut(pkg) { category(context, pkg) }
            // 시간대별로 나눠 담기
            var t = from
            while (t < to) {
                val hour = ((t - start) / 3_600_000L).toInt().coerceIn(0, 23)
                val hourEnd = start + (hour + 1) * 3_600_000L
                val end = minOf(to, hourEnd)
                hourly[hour][cat.ordinal] += end - t
                t = end
            }
        }

        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(start, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> open[e.packageName] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    open.remove(e.packageName)?.let { add(e.packageName, it, e.timeStamp) }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    open.forEach { (pkg, from) -> add(pkg, from, e.timeStamp) }
                    open.clear()
                }
            }
        }
        open.forEach { (pkg, from) -> add(pkg, from, now) }   // 지금 쓰고 있는 앱

        val top = perApp.entries.sortedByDescending { it.value }.map { (pkg, ms) ->
            AppUsage(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg), ms)
        }
        return Today(perApp.values.sum(), hourly, top)
    }

    private fun category(context: Context, pkg: String): Category {
        val info = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return Category.OTHER
        return when (info.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> Category.SOCIAL
            ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_IMAGE -> Category.ENTERTAINMENT
            else -> Category.OTHER
        }
    }

    /** "3시간 24분" / "45분" / "1분 미만" */
    fun format(ms: Long): String {
        val min = ms / 60_000
        return when {
            min < 1 -> "1분 미만"
            min < 60 -> "${min}분"
            else -> "${min / 60}시간" + if (min % 60 != 0L) " ${min % 60}분" else ""
        }
    }
}
