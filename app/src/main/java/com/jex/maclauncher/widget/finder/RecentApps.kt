package com.jex.maclauncher.widget.finder

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process

/** Finder '최근 사용 앱': 최근 7일 동안 연 앱을 최신순으로 (사용 정보 접근 권한 필요) */
object RecentApps {
    private const val DAYS = 7
    private const val LIMIT = 40

    data class Used(val packageName: String, val lastUsed: Long)

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun list(context: Context): List<Used> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - DAYS * 24 * 60 * 60 * 1000L, now)
        val last = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last[e.packageName] = e.timeStamp
        }
        // 홈 화면(런처)과 이 앱, 실행할 수 없는 시스템 구성요소는 뺀다
        val pm = context.packageManager
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
        return last.entries
            .filter { it.key != context.packageName && it.key !in homes && pm.getLaunchIntentForPackage(it.key) != null }
            .sortedByDescending { it.value }
            .take(LIMIT)
            .map { Used(it.key, it.value) }
    }

    /** "방금 / 5분 전 / 3시간 전 / 2일 전" */
    fun ago(time: Long, now: Long = System.currentTimeMillis()): String {
        val min = (now - time) / 60_000
        return when {
            min < 1 -> "방금"
            min < 60 -> "${min}분 전"
            min < 24 * 60 -> "${min / 60}시간 전"
            else -> "${min / (24 * 60)}일 전"
        }
    }
}
