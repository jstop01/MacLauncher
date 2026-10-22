package com.jex.maclauncher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.R
import java.util.Locale
import kotlin.math.roundToInt

/** macOS 스타일 시스템 모니터 위젯 (CPU 링 + 그래프, 메모리 + 그래프) */
class MonitorWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
        if (!MonitorService.running) MonitorService.start(context)
    }

    // 크기를 바꾸면 작은/큰 레이아웃 다시 고르기
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        updateAll(context)
    }

    override fun onEnabled(context: Context) {
        MonitorService.start(context)
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, MonitorService::class.java))
    }

    companion object {
        private const val BLUE = 0xFF0A84FF.toInt()
        private const val GREEN = 0xFF30D158.toInt()

        fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            ids(context).forEach { id -> manager.updateAppWidget(id, views(context, WidgetSize.of(manager, id))) }
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MonitorWidget::class.java))

        /** 작으면 CPU 링 + 메모리 숫자만, 크면 그래프까지 */
        private fun views(context: Context, size: WidgetSize): RemoteViews {
            val s = MonitorService.state
            val small = size.narrowerThan(200) || size.shorterThan(160)
            val layout = if (small) R.layout.widget_monitor_small else R.layout.widget_monitor
            return RemoteViews(context.packageName, layout).apply {
                setImageViewBitmap(R.id.mon_cpu_ring, MonitorGraphics.ring(s.cpu, BLUE))
                setTextViewText(R.id.mon_cpu_value, "${(s.cpu * 100).roundToInt()}%")
                setTextViewText(R.id.mon_mem_value, formatMemory(s.mem.usedMb))
                if (!small) {
                    setImageViewBitmap(R.id.mon_cpu_graph, MonitorGraphics.graph(s.cpuHistory, MonitorService.HISTORY, BLUE))
                    setImageViewBitmap(R.id.mon_mem_graph, MonitorGraphics.graph(s.memHistory, MonitorService.HISTORY, GREEN))
                }

                // 서비스가 안 돌고 있으면 안내 문구를 보이고, 탭하면 서비스 시작 (사용자 탭은 백그라운드 제한 예외)
                setViewVisibility(R.id.mon_hint, if (MonitorService.running) View.GONE else View.VISIBLE)
                val start = PendingIntent.getForegroundService(
                    context, 0, Intent(context, MonitorService::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                setOnClickPendingIntent(R.id.mon_root, start)
            }
        }

        private fun formatMemory(mb: Long): String =
            if (mb >= 1024) String.format(Locale.US, "%.1fGB", mb / 1024f) else "${mb}MB"
    }
}
