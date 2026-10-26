package com.jex.maclauncher.widget.apps

import android.app.Activity
import com.jex.maclauncher.widget.skin.Skin
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
import android.widget.RemoteViewsService
import com.jex.maclauncher.widget.MacIcon
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.WidgetTheme
import com.jex.maclauncher.widget.WidgetSize

/** 앱 전용 Finder: 분류 사이드바 + 앱 격자, 탭하면 실행. 고른 분류는 위젯마다 기억. */
class AppsWidget : AppWidgetProvider() {

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
            ACTION_SECTION -> {
                prefs(context).edit().putString(KEY_SECTION + id, intent.getStringExtra(EXTRA_SECTION)).apply()
                refresh(context, id)
            }
            ACTION_REFRESH -> refresh(context, id)
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(KEY_SECTION + it) } }.apply()
    }

    companion object {
        private const val ACTION_SECTION = "com.jex.maclauncher.apps.SECTION"
        private const val ACTION_REFRESH = "com.jex.maclauncher.apps.REFRESH"
        private const val EXTRA_SECTION = "section"
        private const val KEY_SECTION = "section_"

        private fun prefs(context: Context) = context.getSharedPreferences("apps_widget", Context.MODE_PRIVATE)

        fun section(context: Context, id: Int): AppCatalog.Section =
            prefs(context).getString(KEY_SECTION + id, null)
                ?.let { runCatching { AppCatalog.Section.valueOf(it) }.getOrNull() }
                ?: AppCatalog.Section.ALL

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, AppsWidget::class.java)).forEach { refresh(context, it) }
        }

        fun refresh(context: Context, id: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val section = section(context, id)
            val views = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_apps))

            views.setTextViewText(R.id.apps_title, section.title)
            views.setViewVisibility(R.id.apps_back, View.INVISIBLE) // 폴더 계층이 없어서 뒤로 버튼은 안 씀
            views.setTextViewText(R.id.apps_footer, "${AppCatalog.list(context, section).size}개 앱")
            views.setOnClickPendingIntent(R.id.apps_refresh, broadcast(context, id, ACTION_REFRESH, 0))

            val narrow = WidgetSize.of(manager, id).narrowerThan(280)
            views.setViewVisibility(R.id.apps_sidebar, if (narrow) View.GONE else View.VISIBLE)
            AppCatalog.Section.entries.forEachIndexed { i, s ->
                views.setInt(s.viewId, "setBackgroundResource", if (s == section) WidgetTheme.drawable(context, R.drawable.finder_side_selected) else 0)
                views.setOnClickPendingIntent(s.viewId, broadcast(context, id, ACTION_SECTION, 1 + i) { putExtra(EXTRA_SECTION, s.name) })
            }

            val adapter = Intent(context, AppsItemsService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(Uri.parse("apps://widget/$id"))
            views.setRemoteAdapter(R.id.apps_grid, adapter)
            views.setEmptyView(R.id.apps_grid, R.id.apps_empty)
            views.setPendingIntentTemplate(
                R.id.apps_grid,
                PendingIntent.getActivity(
                    context, id, Intent(context, AppLaunchActivity::class.java),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            manager.updateAppWidget(id, Skin.dress(context, views, WidgetSize.of(manager, id)))
            manager.notifyAppWidgetViewDataChanged(id, R.id.apps_grid)
        }

        private fun broadcast(
            context: Context, id: Int, action: String, slot: Int, extras: Intent.() -> Unit = {},
        ): PendingIntent = PendingIntent.getBroadcast(
            context, id * 16 + slot,
            Intent(context, AppsWidget::class.java)
                .setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** 앱 격자 항목 */
class AppsItemsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))

    private class Factory(private val context: Context, private val widgetId: Int) : RemoteViewsFactory {
        private var apps: List<InstalledApp> = emptyList()

        override fun onCreate() = Unit
        override fun onDataSetChanged() {
            apps = AppCatalog.list(context, AppsWidget.section(context, widgetId))
        }
        override fun onDestroy() = Unit
        override fun getCount() = apps.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_finder_item))
            Skin.tintText(context, views)
            val app = apps.getOrNull(position) ?: return views
            views.setTextViewText(R.id.item_name, app.label)
            MacIcon.of(context, app.packageName, 112)?.let { views.setImageViewBitmap(R.id.item_icon, it) }
            views.setOnClickFillInIntent(R.id.item_root, Intent().putExtra(AppLaunchActivity.EXTRA_PACKAGE, app.packageName))
            return views
        }

        // 기본 "Loading..." 글자 대신 빈 칸
        override fun getLoadingView(): RemoteViews = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_finder_item))
        // 다크·라이트 항목 레이아웃 두 가지. 1이면 밝은↔어두운 팩 전환 뒤 항목이 '불러오는 중'에서 멈춘다
        override fun getViewTypeCount() = 2
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}

/**
 * 격자 탭을 받아 해당 앱을 실행하고 바로 끝나는 중간 액티비티.
 * 목록 항목은 템플릿 PendingIntent 하나만 쓸 수 있어서 패키지명을 받아 여기서 실행한다.
 */
class AppLaunchActivity : Activity() {
    companion object {
        const val EXTRA_PACKAGE = "package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_PACKAGE)
            ?.let(packageManager::getLaunchIntentForPackage)
            ?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        finish()
    }
}
