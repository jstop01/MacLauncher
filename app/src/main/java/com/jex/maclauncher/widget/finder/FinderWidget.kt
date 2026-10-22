package com.jex.maclauncher.widget.finder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.WidgetSize
import com.jex.maclauncher.widget.apps.AppCatalog

/**
 * macOS Finder 위젯. 사이드바로 위치를 고르고, 격자에서 폴더를 누르면 들어가고 파일을 누르면 연다.
 * 위치는 위젯마다 따로 기억한다.
 */
class FinderWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { refresh(context, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        refresh(context, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        when (intent.action) {
            ACTION_GO -> intent.getStringExtra(EXTRA_LOCATION)?.let { go(context, id, it) }
            ACTION_BACK -> FinderFiles.parent(location(context, id))?.let { go(context, id, it) }
            ACTION_REFRESH -> refresh(context, id)
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(KEY_LOCATION + it) } }.apply()
    }

    companion object {
        private const val ACTION_GO = "com.jex.maclauncher.finder.GO"
        private const val ACTION_BACK = "com.jex.maclauncher.finder.BACK"
        private const val ACTION_REFRESH = "com.jex.maclauncher.finder.REFRESH"
        private const val EXTRA_LOCATION = "location"
        private const val KEY_LOCATION = "location_"

        private fun prefs(context: Context) = context.getSharedPreferences("finder_widget", Context.MODE_PRIVATE)

        fun location(context: Context, id: Int): String =
            prefs(context).getString(KEY_LOCATION + id, null) ?: FinderFiles.RECENT

        fun go(context: Context, id: Int, location: String) {
            prefs(context).edit().putString(KEY_LOCATION + id, location).apply()
            refresh(context, id)
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, FinderWidget::class.java)).forEach { refresh(context, it) }
        }

        /** 제목/사이드바/상태 막대를 다시 그리고 격자 목록도 다시 읽게 한다 */
        fun refresh(context: Context, id: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val location = location(context, id)
            // 앱 목록은 파일 권한이 필요 없고, 최근 사용 앱은 사용 정보 접근 권한이 필요
            val access = FinderFiles.canShow(context, location)
            val views = RemoteViews(context.packageName, R.layout.widget_finder)

            views.setTextViewText(R.id.finder_title, FinderFiles.title(context, location))
            views.setViewVisibility(R.id.finder_back, if (FinderFiles.parent(location) != null) View.VISIBLE else View.INVISIBLE)
            views.setOnClickPendingIntent(R.id.finder_back, broadcast(context, id, ACTION_BACK, 0))
            views.setOnClickPendingIntent(R.id.finder_refresh, broadcast(context, id, ACTION_REFRESH, 1))

            // 좁으면 사이드바를 숨기고 파일 격자만
            val narrow = WidgetSize.of(manager, id).narrowerThan(280)
            views.setViewVisibility(R.id.finder_sidebar, if (narrow) View.GONE else View.VISIBLE)

            val selected = FinderFiles.sidebarFor(location)
            FinderFiles.sidebar.forEachIndexed { i, (viewId, _, target) ->
                views.setInt(viewId, "setBackgroundResource", if (viewId == selected) R.drawable.finder_side_selected else 0)
                views.setOnClickPendingIntent(viewId, broadcast(context, id, ACTION_GO, 2 + i) { putExtra(EXTRA_LOCATION, target) })
            }

            if (access) {
                // 위젯마다 다른 목록이 되도록 data를 위젯 id로 구분
                val adapter = Intent(context, FinderItemsService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("finder://widget/$id"))
                views.setRemoteAdapter(R.id.finder_grid, adapter)
                views.setEmptyView(R.id.finder_grid, R.id.finder_empty)
                views.setPendingIntentTemplate(R.id.finder_grid, FinderOpenActivity.template(context, id))
                views.setViewVisibility(R.id.finder_permission, View.GONE)
                views.setTextViewText(
                    R.id.finder_footer,
                    when {
                        location == FinderFiles.APPS -> "${AppCatalog.list(context, AppCatalog.Section.ALL).size}개 앱"
                        location == FinderFiles.RECENT_APPS -> "최근 7일 · ⟳ 로 새로고침"
                        FinderFiles.isAppLocation(location) -> "즐겨찾기 · ＋ 로 편집"
                        else -> FinderFiles.freeSpace()
                    },
                )
            } else {
                views.setViewVisibility(R.id.finder_grid, View.GONE)
                views.setViewVisibility(R.id.finder_empty, View.GONE)
                views.setViewVisibility(R.id.finder_permission, View.VISIBLE)
                views.setTextViewText(
                    R.id.finder_permission,
                    if (location == FinderFiles.RECENT_APPS) "최근 사용 앱을 보려면 '사용 정보 접근' 권한이 필요해요\n\n탭해서 허용하기"
                    else "파일을 보려면 '모든 파일 접근' 권한이 필요해요\n\n탭해서 허용하기",
                )
                views.setOnClickPendingIntent(R.id.finder_permission, FinderOpenActivity.permission(context, id))
                views.setTextViewText(R.id.finder_footer, "")
            }
            manager.updateAppWidget(id, views)
            if (access) manager.notifyAppWidgetViewDataChanged(id, R.id.finder_grid)
        }

        private fun broadcast(
            context: Context, id: Int, action: String, slot: Int, extras: Intent.() -> Unit = {},
        ): PendingIntent = PendingIntent.getBroadcast(
            context, id * 16 + slot,
            Intent(context, FinderWidget::class.java)
                .setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
