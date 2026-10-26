package com.jex.maclauncher.widget.dock

import android.app.PendingIntent
import com.jex.maclauncher.widget.WidgetSize
import com.jex.maclauncher.widget.skin.Skin
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.widget.MacIcon
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.finder.FinderIcon
import com.jex.maclauncher.widget.finder.FinderWidget
import com.jex.maclauncher.backup.WidgetBackup
import com.jex.maclauncher.widget.WidgetTheme

/** macOS Dock 위젯: 고른 앱 아이콘을 한 줄로, 탭하면 실행. 앱 목록은 위젯마다 따로 저장. */
class DockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    // 크기 변경(패널 비트맵 크기), One UI 다크 모드 전환 때도 옵션이 바뀌어 다시 그린다
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        update(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_FINDER) FinderWidget.toggleAll(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(KEY_APPS + it) } }.apply()
    }

    companion object {
        const val MAX_APPS = 10
        private const val ACTION_FINDER = "com.jex.maclauncher.dock.FINDER"
        private const val KEY_APPS = "apps_"
        private const val ICON_PX = 112   // 위젯 갱신 한 번에 넘기는 크기 제한(~1MB) 안에 10개가 들어가도록

        /** 따로 고르지 않았을 때: 설치된 것만, 앞에서부터 */
        private val DEFAULTS = listOf(
            "com.samsung.android.dialer", "com.google.android.dialer",
            "com.samsung.android.messaging", "com.google.android.apps.messaging",
            "com.sec.android.app.sbrowser", "com.android.chrome",
            "com.sec.android.app.camera", "com.android.camera2", "com.google.android.GoogleCamera",
            "com.sec.android.gallery3d", "com.google.android.apps.photos",
            "com.android.settings",
        )

        private fun prefs(context: Context) = context.getSharedPreferences("dock_widget", Context.MODE_PRIVATE)

        fun apps(context: Context, id: Int): List<String> {
            val saved = prefs(context).getString(KEY_APPS + id, null)
            if (saved != null) return saved.split(",").filter { it.isNotBlank() }
            // 백업에서 복원했는데 그때 Dock이 모자랐다면, 새로 놓인 Dock에 채움
            WidgetBackup.takePendingDock(context)?.let { restored ->
                saveQuietly(context, id, restored)
                return restored
            }
            val pm = context.packageManager
            return DEFAULTS.filter { pm.getLaunchIntentForPackage(it) != null }
                .distinctBy { DEFAULT_GROUP[it] ?: it }
                .take(MAX_APPS)
        }

        // 같은 역할 앱(전화/메시지/…)은 기본값에서 하나만
        private val DEFAULT_GROUP = mapOf(
            "com.samsung.android.dialer" to "dialer", "com.google.android.dialer" to "dialer",
            "com.samsung.android.messaging" to "sms", "com.google.android.apps.messaging" to "sms",
            "com.sec.android.app.sbrowser" to "web", "com.android.chrome" to "web",
            "com.sec.android.app.camera" to "camera", "com.android.camera2" to "camera", "com.google.android.GoogleCamera" to "camera",
            "com.sec.android.gallery3d" to "gallery", "com.google.android.apps.photos" to "gallery",
        )

        fun saveQuietly(context: Context, id: Int, packages: List<String>) {
            prefs(context).edit().putString(KEY_APPS + id, packages.joinToString(",")).apply()
        }

        fun save(context: Context, id: Int, packages: List<String>) {
            saveQuietly(context, id, packages)
            update(context, AppWidgetManager.getInstance(context), id)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, DockWidget::class.java)).forEach { update(context, manager, it) }
        }

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val pm = context.packageManager
            val views = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_dock))
            views.removeAllViews(R.id.dock_icons)

            var shown = 0
            // macOS처럼 맨 왼쪽에 Finder: 누르면 홈의 Finder 위젯을 최소화/펼침 (Finder 위젯이 있을 때만)
            if (FinderWidget.hasWidgets(context)) {
                val finder = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_dock_item))
                FinderIcon.apply(context, finder, R.id.dock_icon)
                finder.setContentDescription(R.id.dock_icon, "Finder")
                finder.setViewVisibility(R.id.dock_dot, if (FinderWidget.anyOpen(context)) View.VISIBLE else View.GONE)
                finder.setOnClickPendingIntent(
                    R.id.dock_icon,
                    PendingIntent.getBroadcast(
                        context, 0, Intent(context, DockWidget::class.java).setAction(ACTION_FINDER),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                views.addView(R.id.dock_icons, finder)
            }
            apps(context, id).forEachIndexed { i, pkg ->
                // 지워진 앱은 건너뜀
                val launch = pm.getLaunchIntentForPackage(pkg) ?: return@forEachIndexed
                val icon = MacIcon.of(context, pkg, ICON_PX) ?: return@forEachIndexed
                val item = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_dock_item))
                item.setImageViewBitmap(R.id.dock_icon, icon)
                item.setContentDescription(R.id.dock_icon, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)))
                item.setOnClickPendingIntent(
                    R.id.dock_icon,
                    PendingIntent.getActivity(context, id * 100 + i, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
                views.addView(R.id.dock_icons, item)
                shown++
            }

            views.setViewVisibility(R.id.dock_empty, if (shown == 0) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(
                R.id.dock_empty,
                PendingIntent.getActivity(
                    context, -id - 1, DockConfigActivity.intent(context, id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            manager.updateAppWidget(id, Skin.dress(context, views, WidgetSize.of(manager, id), macDark = R.drawable.dock_bg, macLight = R.drawable.dock_bg_light))
        }
    }
}
