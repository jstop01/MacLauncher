package com.jex.maclauncher.widget

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar

/** 기기 캘린더 일정 읽기 (캘린더 읽기 권한 필요, 없으면 빈 결과) */
object CalendarEvents {

    data class Event(val title: String, val begin: Long, val allDay: Boolean, val color: Int)

    fun hasAccess(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** [from, to) 사이 일정 (시작 시각 순) */
    fun between(context: Context, from: Long, to: Long): List<Event> {
        if (!hasAccess(context)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val events = mutableListOf<Event>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.DISPLAY_COLOR,
                ),
                "${CalendarContract.Instances.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    events += Event(
                        title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(제목 없음)",
                        begin = c.getLong(1),
                        allDay = c.getInt(2) == 1,
                        color = c.getInt(3),
                    )
                }
            }
        }
        return events
    }

    /** 이 달(1일~말일)에서 일정이 있는 날짜(1~31) */
    fun daysWithEvents(context: Context, month: Calendar): Set<Int> {
        val start = (month.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val day = Calendar.getInstance()
        return between(context, start.timeInMillis, end.timeInMillis).mapNotNull { e ->
            day.timeInMillis = e.begin
            // 종일 일정의 BEGIN은 UTC 자정이라 시간대에 따라 하루 밀릴 수 있음 → UTC 기준 날짜로
            if (e.allDay) {
                val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = e.begin }
                utc.get(Calendar.DAY_OF_MONTH).takeIf { utc.get(Calendar.MONTH) == start.get(Calendar.MONTH) }
            } else {
                day.get(Calendar.DAY_OF_MONTH).takeIf { day.get(Calendar.MONTH) == start.get(Calendar.MONTH) }
            }
        }.toSet()
    }

    /** 지금부터 7일 안의 다음 일정 (지난 종일 일정 제외) */
    fun upcoming(context: Context, limit: Int): List<Event> {
        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return between(context, todayStart, now + 7L * 24 * 60 * 60 * 1000)
            .filter { it.allDay || it.begin >= now - 30 * 60 * 1000 }   // 시작한 지 30분 넘은 건 뺌
            .take(limit)
    }
}
