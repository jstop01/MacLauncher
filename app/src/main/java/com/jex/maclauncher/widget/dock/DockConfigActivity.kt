package com.jex.maclauncher.widget.dock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jex.maclauncher.ui.AppPicker
import com.jex.maclauncher.ui.theme.MacLauncherTheme

/** Dock 위젯에 넣을 앱을 고르는 화면. 고른 순서대로 Dock에 놓인다. */
class DockConfigActivity : ComponentActivity() {

    companion object {
        fun intent(context: Context, widgetId: Int): Intent =
            Intent(context, DockConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        // 뒤로 나가면 위젯 추가 취소 (런처 규칙)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        enableEdgeToEdge()
        setContent {
            MacLauncherTheme {
                AppPicker(
                    title = "Dock에 넣을 앱",
                    subtitle = "누른 순서대로 왼쪽부터 놓여요 · 최대 ${DockWidget.MAX_APPS}개",
                    max = DockWidget.MAX_APPS,
                    initial = DockWidget.apps(this, widgetId),
                    onDone = { packages ->
                        DockWidget.save(this, widgetId, packages)
                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    },
                )
            }
        }
    }
}
