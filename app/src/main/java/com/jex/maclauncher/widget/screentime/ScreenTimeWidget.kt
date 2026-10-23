package com.jex.maclauncher.widget.screentime

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.MacIcon
import com.jex.maclauncher.widget.WidgetSize
import com.jex.maclauncher.widget.WidgetTheme
import com.jex.maclauncher.widget.finder.RecentApps
import kotlin.concurrent.thread

/** macOS 스크린 타임 위젯: 오늘 합계, 시간대별 막대(분류별 색), 많이 쓴 앱 3개. 탭하면 새로고침 */
class ScreenTimeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshAll(context, goAsync())

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        refreshAll(context, goAsync())

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refreshAll(context, goAsync())
    }

    companion object {
        private const val ACTION_REFRESH = "com.jex.maclauncher.screentime.REFRESH"
        private val APPS = intArrayOf(R.id.st_app0, R.id.st_app1, R.id.st_app2)
        private val ICONS = intArrayOf(R.id.st_app_icon0, R.id.st_app_icon1, R.id.st_app_icon2)
        private val NAMES = intArrayOf(R.id.st_app_name0, R.id.st_app_name1, R.id.st_app_name2)
        private val TIMES = intArrayOf(R.id.st_app_time0, R.id.st_app_time1, R.id.st_app_time2)

        /** 하루치 이벤트를 훑어서 백그라운드에서 */
        fun refreshAll(context: Context, pending: BroadcastReceiver.PendingResult? = null) {
            val app = context.applicationContext
            thread(name = "screentime-widget") {
                try {
                    val manager = AppWidgetManager.getInstance(app)
                    val ids = manager.getAppWidgetIds(ComponentName(app, ScreenTimeWidget::class.java))
                    if (ids.isEmpty()) return@thread
                    val today = if (RecentApps.hasAccess(app)) runCatching { ScreenTime.today(app) }.getOrNull() else null
                    ids.forEach { id -> manager.updateAppWidget(id, views(app, WidgetSize.of(manager, id), today)) }
                } finally {
                    pending?.finish()
                }
            }
        }

        private fun views(context: Context, size: WidgetSize, today: ScreenTime.Today?): RemoteViews {
            val small = size.narrowerThan(250)
            val views = RemoteViews(
                context.packageName,
                WidgetTheme.layout(context, if (small) R.layout.widget_screentime_small else R.layout.widget_screentime),
            )
            if (today == null) {
                views.setTextViewText(R.id.st_total, "—")
                views.setViewVisibility(R.id.st_hint, View.VISIBLE)
                views.setViewVisibility(R.id.st_chart, View.GONE)
                if (!small) views.setViewVisibility(R.id.st_legend, View.GONE)
                APPS.forEach { views.setViewVisibility(it, View.GONE) }
                views.setOnClickPendingIntent(
                    R.id.st_root,
                    PendingIntent.getActivity(
                        context, 1, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                return views
            }

            views.setTextViewText(R.id.st_total, ScreenTime.format(today.total))
            views.setImageViewBitmap(R.id.st_chart, chart(context, today.hourly, small))
            if (!small) views.setTextViewText(R.id.st_legend, legend())
            APPS.forEachIndexed { i, row ->
                val app = today.top.getOrNull(i)
                views.setViewVisibility(row, if (app != null) View.VISIBLE else View.INVISIBLE)
                if (app == null) return@forEachIndexed
                MacIcon.of(context, app.packageName, 84)?.let { views.setImageViewBitmap(ICONS[i], it) }
                views.setTextViewText(NAMES[i], app.label)
                views.setTextViewText(TIMES[i], ScreenTime.format(app.millis))
            }
            views.setOnClickPendingIntent(
                R.id.st_root,
                PendingIntent.getBroadcast(
                    context, 0, Intent(context, ScreenTimeWidget::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return views
        }

        /** "● 소셜  ● 엔터테인먼트  ● 기타" */
        private fun legend(): CharSequence = SpannableStringBuilder().apply {
            ScreenTime.Category.entries.forEach { c ->
                val start = length
                append("●")
                setSpan(ForegroundColorSpan(c.color), start, length, 0)
                append(" ${c.label}   ")
            }
        }

        /** 0~23시 막대, 분류별로 쌓음. 가장 많이 쓴 시간이 꽉 찬 높이(최소 30분 기준) */
        private fun chart(context: Context, hourly: Array<LongArray>, small: Boolean, width: Int = 720, height: Int = if (small) 120 else 240): Bitmap {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = WidgetTheme.palette(context)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val max = maxOf(hourly.maxOf { it.sum() }, 30 * 60_000L).toFloat()
            val slot = width / 24f
            val barW = slot * 0.62f
            val radius = barW / 2.5f

            // 빈 막대(트랙) + 6시간마다 옅은 기준선
            paint.color = p.grid
            for (h in 0 until 24) {
                val x = h * slot + (slot - barW) / 2
                c.drawRoundRect(RectF(x, 0f, x + barW, height.toFloat()), radius, radius, paint)
            }
            for (h in 0 until 24) {
                val x = h * slot + (slot - barW) / 2
                var bottom = height.toFloat()
                ScreenTime.Category.entries.forEach { cat ->
                    val ms = hourly[h][cat.ordinal]
                    if (ms <= 0) return@forEach
                    val top = bottom - height * (ms / max)
                    paint.color = cat.color
                    c.drawRoundRect(RectF(x, top, x + barW, bottom), radius, radius, paint)
                    bottom = top
                }
            }
            return bmp
        }
    }
}
