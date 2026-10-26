package com.jex.maclauncher.widget.network

import android.app.PendingIntent
import com.jex.maclauncher.widget.skin.Skin
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.MonitorGraphics
import com.jex.maclauncher.widget.MonitorService
import com.jex.maclauncher.widget.WidgetSize
import com.jex.maclauncher.widget.WidgetTheme

/** iStat 스타일 네트워크 위젯: 실시간 ↓/↑ 속도와 60초 그래프, 연결 종류, 오늘 사용량 */
class NetworkWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
        if (!MonitorService.running) MonitorService.start(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        updateAll(context)

    override fun onEnabled(context: Context) {
        MonitorService.start(context)
    }

    companion object {
        /** 그래프 꽉 찬 높이의 최소 기준 (거의 안 쓸 때 잡음이 크게 보이지 않게) */
        private const val MIN_SCALE = 64_000L

        fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, NetworkWidget::class.java))

        fun updateAll(context: Context) {
            val ids = ids(context)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val today = NetworkStats.today(context)
            ids.forEach { id -> WidgetSize.of(manager, id).let { size -> manager.updateAppWidget(id, Skin.dress(context, views(context, size, today), size)) } }
        }

        private fun views(context: Context, size: WidgetSize, today: Pair<Long, Long>?): RemoteViews {
            val small = size.narrowerThan(250)
            val views = RemoteViews(context.packageName, WidgetTheme.layout(context, if (small) R.layout.widget_network_small else R.layout.widget_network))
            val live = NetworkStats.live
            val pack = Skin.current(context)
            val p = pack.palette

            views.setTextViewText(R.id.net_type, NetworkStats.connection(context))
            views.setTextViewText(R.id.net_down, NetworkStats.speed(live.down))
            views.setTextViewText(R.id.net_up, NetworkStats.speed(live.up))

            // 두 그래프를 같은 눈금으로
            val scale = maxOf(MIN_SCALE, live.downHistory.maxOrNull() ?: 0, live.upHistory.maxOrNull() ?: 0).toFloat()
            views.setImageViewBitmap(R.id.net_graph_down, MonitorGraphics.graph(live.downHistory.map { it / scale }, NetworkStats.HISTORY, pack.blue, p.grid))
            views.setImageViewBitmap(R.id.net_graph_up, MonitorGraphics.graph(live.upHistory.map { it / scale }, NetworkStats.HISTORY, pack.red, 0))

            views.setTextViewText(
                R.id.net_today,
                if (today == null) "오늘 사용량은 '사용 정보 접근' 권한 필요"
                else "오늘  Wi-Fi ${NetworkStats.amount(today.first)} · 모바일 ${NetworkStats.amount(today.second)}",
            )

            // 1초 갱신이 멈춰 있으면 안내 + 탭 = 시작, 돌고 있으면 탭 = 데이터 사용량 설정
            views.setViewVisibility(R.id.net_hint, if (MonitorService.running) View.GONE else View.VISIBLE)
            val click = if (MonitorService.running) {
                PendingIntent.getActivity(
                    context, 1,
                    Intent(if (today == null) Settings.ACTION_USAGE_ACCESS_SETTINGS else Settings.ACTION_DATA_USAGE_SETTINGS),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                PendingIntent.getForegroundService(
                    context, 0, Intent(context, MonitorService::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            views.setOnClickPendingIntent(R.id.net_root, click)
            return views
        }
    }
}
