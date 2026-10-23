package com.jex.maclauncher.widget.finder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.WidgetTheme
import com.jex.maclauncher.widget.WidgetSize
import com.jex.maclauncher.widget.apps.AppCatalog
import com.jex.maclauncher.widget.dock.DockWidget

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
            ACTION_MINIMIZE -> {
                toggle(context, KEY_MINIMIZED + id)
                refresh(context, id)
                DockWidget.updateAll(context)   // Dock의 Finder 점
            }
            ACTION_ZOOM -> toggle(context, KEY_ZOOMED + id).also { refresh(context, id) }
            // 닫기: 즐겨찾는 앱 첫 화면으로, 펼친 상태로
            ACTION_CLOSE -> {
                prefs(context).edit().remove(KEY_MINIMIZED + id).remove(KEY_ZOOMED + id).apply()
                go(context, id, FinderFiles.FAVORITES)
                DockWidget.updateAll(context)
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply {
            ids.forEach { remove(KEY_LOCATION + it); remove(KEY_MINIMIZED + it); remove(KEY_ZOOMED + it) }
        }.apply()
    }

    companion object {
        private const val ACTION_GO = "com.jex.maclauncher.finder.GO"
        private const val ACTION_BACK = "com.jex.maclauncher.finder.BACK"
        private const val ACTION_REFRESH = "com.jex.maclauncher.finder.REFRESH"
        private const val ACTION_CLOSE = "com.jex.maclauncher.finder.CLOSE"
        private const val ACTION_MINIMIZE = "com.jex.maclauncher.finder.MINIMIZE"
        private const val ACTION_ZOOM = "com.jex.maclauncher.finder.ZOOM"
        private const val KEY_MINIMIZED = "minimized_"
        private const val KEY_ZOOMED = "zoomed_"
        private const val KEY_MINI_POSITION = "mini_position"
        private const val KEY_MINI_ICON = "mini_icon"

        /** 🟡 최소화 때 아이콘을 남길지 (끄면 완전히 사라지고 Dock의 Finder나 빈자리 탭으로 펼침) */
        fun miniIconEnabled(context: Context) = prefs(context).getBoolean(KEY_MINI_ICON, true)

        fun setMiniIconEnabled(context: Context, on: Boolean) {
            prefs(context).edit().putBoolean(KEY_MINI_ICON, on).apply()
            refreshAll(context)
        }

        /**
         * 최소화 아이콘 위치: 0~8 (왼쪽 위부터 가로로 3칸씩, 4 = 가운데). 모든 Finder 위젯 공통.
         * 위젯은 화면 어디에 놓였는지 알 수 없어서 사용자가 고른다.
         */
        fun miniPosition(context: Context) = prefs(context).getInt(KEY_MINI_POSITION, 0).coerceIn(0, 8)

        fun setMiniPositionQuietly(context: Context, position: Int) {
            prefs(context).edit().putInt(KEY_MINI_POSITION, position.coerceIn(0, 8)).apply()
        }

        fun setMiniPosition(context: Context, position: Int) {
            prefs(context).edit().putInt(KEY_MINI_POSITION, position).apply()
            refreshAll(context)
        }

        private fun miniGravity(position: Int): Int {
            val h = when (position % 3) { 0 -> Gravity.START; 1 -> Gravity.CENTER_HORIZONTAL; else -> Gravity.END }
            val v = when (position / 3) { 0 -> Gravity.TOP; 1 -> Gravity.CENTER_VERTICAL; else -> Gravity.BOTTOM }
            return h or v
        }
        private const val EXTRA_LOCATION = "location"
        private const val KEY_LOCATION = "location_"

        private fun prefs(context: Context) = context.getSharedPreferences("finder_widget", Context.MODE_PRIVATE)

        /** 펼쳐진 Finder 위젯이 하나라도 있는지 (Dock의 점) */
        fun anyOpen(context: Context): Boolean =
            ids(context).any { !prefs(context).getBoolean(KEY_MINIMIZED + it, false) }

        fun hasWidgets(context: Context) = ids(context).isNotEmpty()

        /** Dock의 Finder 아이콘: 하나라도 펼쳐져 있으면 모두 최소화, 모두 접혀 있으면 모두 펼침 */
        fun toggleAll(context: Context) {
            val minimize = anyOpen(context)
            prefs(context).edit().apply { ids(context).forEach { putBoolean(KEY_MINIMIZED + it, minimize) } }.apply()
            refreshAll(context)
            DockWidget.updateAll(context)
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, FinderWidget::class.java))

        private fun toggle(context: Context, key: String) {
            val p = prefs(context)
            p.edit().putBoolean(key, !p.getBoolean(key, false)).apply()
        }

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
            val views = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_finder))

            views.setTextViewText(R.id.finder_title, FinderFiles.title(context, location))
            views.setViewVisibility(R.id.finder_back, if (FinderFiles.parent(location) != null) View.VISIBLE else View.INVISIBLE)
            views.setOnClickPendingIntent(R.id.finder_back, broadcast(context, id, ACTION_BACK, 0))
            views.setOnClickPendingIntent(R.id.finder_refresh, broadcast(context, id, ACTION_REFRESH, 1))
            views.setOnClickPendingIntent(R.id.finder_close, broadcast(context, id, ACTION_CLOSE, 13))
            views.setOnClickPendingIntent(R.id.finder_minimize, broadcast(context, id, ACTION_MINIMIZE, 14))
            views.setOnClickPendingIntent(R.id.finder_mini, broadcast(context, id, ACTION_MINIMIZE, 14))
            views.setOnClickPendingIntent(R.id.finder_zoom, broadcast(context, id, ACTION_ZOOM, 15))

            // 최소화: 창은 감추고 Finder 아이콘만 (배경도 투명하게)
            val minimized = prefs(context).getBoolean(KEY_MINIMIZED + id, false)
            val windowParts = intArrayOf(R.id.finder_titlebar, R.id.finder_divider, R.id.finder_body, R.id.finder_footer)
            windowParts.forEach { views.setViewVisibility(it, if (minimized) View.GONE else View.VISIBLE) }
            val showIcon = miniIconEnabled(context)
            views.setViewVisibility(R.id.finder_mini, if (minimized && showIcon) View.VISIBLE else View.GONE)
            if (minimized && showIcon) FinderIcon.apply(context, views, R.id.finder_mini)
            // 아이콘 없이 접었으면 빈자리 전체를 탭해서 펼침 (Dock이 없어도 갇히지 않게)
            if (minimized && !showIcon) views.setOnClickPendingIntent(R.id.finder_root, broadcast(context, id, ACTION_MINIMIZE, 14))
            // 펼쳐 있을 땐 내용이 꽉 차서 영향 없고, 최소화일 때 아이콘이 9칸 중 어디에 붙을지만 정함
            views.setInt(R.id.finder_root, "setGravity", miniGravity(miniPosition(context)))
            views.setInt(
                R.id.finder_root, "setBackgroundResource",
                if (minimized) 0 else WidgetTheme.drawable(context, R.drawable.widget_bg),
            )

            // 좁으면 사이드바를 숨기고 파일 격자만
            // 최대화(🟢)도 사이드바를 숨겨 격자를 창 전체로
            val narrow = WidgetSize.of(manager, id).narrowerThan(280) || prefs(context).getBoolean(KEY_ZOOMED + id, false)
            views.setViewVisibility(R.id.finder_sidebar, if (narrow) View.GONE else View.VISIBLE)

            val selected = FinderFiles.sidebarFor(location)
            FinderFiles.sidebar.forEachIndexed { i, (viewId, _, target) ->
                views.setInt(viewId, "setBackgroundResource", if (viewId == selected) WidgetTheme.drawable(context, R.drawable.finder_side_selected) else 0)
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
