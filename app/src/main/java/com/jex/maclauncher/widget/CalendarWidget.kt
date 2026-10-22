package com.jex.maclauncher.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** macOS 스타일 월간 달력 위젯. ◀ ▶로 달을 넘기고, 제목을 누르면 이번 달로 돌아온다. */
class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        scheduleMidnight(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREV, ACTION_NEXT, ACTION_TODAY -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val offset = when (intent.action) {
                    ACTION_PREV -> monthOffset(context, id) - 1
                    ACTION_NEXT -> monthOffset(context, id) + 1
                    else -> 0
                }
                prefs(context).edit().putInt(KEY_OFFSET + id, offset).apply()
                update(context, AppWidgetManager.getInstance(context), id)
            }
            ACTION_MIDNIGHT, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                updateAll(context)
                scheduleMidnight(context)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        update(context, manager, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(KEY_OFFSET + it) } }.apply()
    }

    companion object {
        private const val ACTION_PREV = "com.jex.maclauncher.calendar.PREV"
        private const val ACTION_NEXT = "com.jex.maclauncher.calendar.NEXT"
        private const val ACTION_TODAY = "com.jex.maclauncher.calendar.TODAY"
        private const val ACTION_MIDNIGHT = "com.jex.maclauncher.calendar.MIDNIGHT"
        private const val KEY_OFFSET = "offset_"

        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val DIM = 0x59FFFFFF
        private const val RED = 0xFFFF453A.toInt()
        private const val BLUE = 0xFF0A84FF.toInt()

        private val ROWS = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5)
        private val DAYS = intArrayOf(
            R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6,
            R.id.d7, R.id.d8, R.id.d9, R.id.d10, R.id.d11, R.id.d12, R.id.d13,
            R.id.d14, R.id.d15, R.id.d16, R.id.d17, R.id.d18, R.id.d19, R.id.d20,
            R.id.d21, R.id.d22, R.id.d23, R.id.d24, R.id.d25, R.id.d26, R.id.d27,
            R.id.d28, R.id.d29, R.id.d30, R.id.d31, R.id.d32, R.id.d33, R.id.d34,
            R.id.d35, R.id.d36, R.id.d37, R.id.d38, R.id.d39, R.id.d40, R.id.d41,
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java)).forEach { update(context, manager, it) }
        }

        private fun prefs(context: Context) = context.getSharedPreferences("calendar_widget", Context.MODE_PRIVATE)

        private fun monthOffset(context: Context, id: Int) = prefs(context).getInt(KEY_OFFSET + id, 0)

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val size = WidgetSize.of(manager, id)
            if (size.narrowerThan(200) || size.shorterThan(190)) {
                manager.updateAppWidget(id, smallViews(context))
                return
            }
            val today = Calendar.getInstance()
            val month = (today.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, monthOffset(context, id))
            }
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            views.setTextViewText(R.id.cal_title, "${month.get(Calendar.YEAR)}년 ${month.get(Calendar.MONTH) + 1}월")

            // 1일이 있는 주의 일요일부터 42칸. 앞뒤 달 날짜는 흐리게
            val lead = month.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            val rows = (lead + month.getActualMaximum(Calendar.DAY_OF_MONTH) + 6) / 7
            val day = (month.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -lead) }
            DAYS.forEachIndexed { i, cell ->
                val inMonth = day.get(Calendar.MONTH) == month.get(Calendar.MONTH)
                val isToday = day.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                val color = when {
                    isToday -> WHITE
                    !inMonth -> DIM
                    i % 7 == 0 -> RED
                    i % 7 == 6 -> BLUE
                    else -> WHITE
                }
                views.setTextViewText(cell, day.get(Calendar.DAY_OF_MONTH).toString())
                views.setTextColor(cell, color)
                views.setInt(cell, "setBackgroundResource", if (isToday) R.drawable.cal_today else 0)
                day.add(Calendar.DAY_OF_MONTH, 1)
            }
            ROWS.forEachIndexed { r, row -> views.setViewVisibility(row, if (r < rows) View.VISIBLE else View.GONE) }

            views.setOnClickPendingIntent(R.id.cal_prev, action(context, id, ACTION_PREV, 0))
            views.setOnClickPendingIntent(R.id.cal_next, action(context, id, ACTION_NEXT, 1))
            views.setOnClickPendingIntent(R.id.cal_title, action(context, id, ACTION_TODAY, 2))
            views.setOnClickPendingIntent(R.id.cal_grid, openCalendarApp(context))
            manager.updateAppWidget(id, views)
        }

        /** 작은 크기: 오늘 요일 / 날짜 / 월 */
        private fun smallViews(context: Context): RemoteViews {
            val now = Date()
            return RemoteViews(context.packageName, R.layout.widget_calendar_small).apply {
                setTextViewText(R.id.cal_small_weekday, SimpleDateFormat("EEEE", Locale.KOREAN).format(now))
                setTextViewText(R.id.cal_small_day, SimpleDateFormat("d", Locale.KOREAN).format(now))
                setTextViewText(R.id.cal_small_month, SimpleDateFormat("yyyy년 M월", Locale.KOREAN).format(now))
                setOnClickPendingIntent(R.id.cal_small_root, openCalendarApp(context))
            }
        }

        private fun action(context: Context, id: Int, action: String, slot: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, id * 3 + slot,
                Intent(context, CalendarWidget::class.java)
                    .setAction(action)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun openCalendarApp(context: Context): PendingIntent {
            val now = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time").appendPath(System.currentTimeMillis().toString()).build()
            return PendingIntent.getActivity(
                context, 0, Intent(Intent.ACTION_VIEW, now), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        /** 다음 자정에 날짜를 넘긴다 (정확할 필요는 없어서 일반 알람) */
        private fun scheduleMidnight(context: Context) {
            val next = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
            }
            val pi = PendingIntent.getBroadcast(
                context, -1, Intent(context, CalendarWidget::class.java).setAction(ACTION_MIDNIGHT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, next.timeInMillis, pi)
        }
    }
}
