package com.jex.maclauncher.widget.sticky

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.jex.maclauncher.R
import com.jex.maclauncher.backup.WidgetBackup

/** macOS 스티커 색 (메모지 색, 글씨는 항상 검정) */
enum class StickyColor(val label: String, val argb: Long) {
    YELLOW("노랑", 0xFFFEF08A),
    BLUE("파랑", 0xFFBFDBFE),
    GREEN("초록", 0xFFBBF7D0),
    PINK("분홍", 0xFFFBCFE8),
    PURPLE("보라", 0xFFDDD6FE),
    GRAY("회색", 0xFFE5E7EB),
}

/** macOS 스티커 메모 위젯. 위젯마다 내용과 색을 따로 저장, 탭하면 수정 */
class StickyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(KEY_TEXT + it); remove(KEY_COLOR + it) } }.apply()
    }

    companion object {
        private const val KEY_TEXT = "text_"
        private const val KEY_COLOR = "color_"

        private fun prefs(context: Context) = context.getSharedPreferences("sticky_widget", Context.MODE_PRIVATE)

        fun text(context: Context, id: Int): String = prefs(context).getString(KEY_TEXT + id, "").orEmpty()

        fun color(context: Context, id: Int): StickyColor =
            runCatching { StickyColor.valueOf(prefs(context).getString(KEY_COLOR + id, null)!!) }.getOrDefault(StickyColor.YELLOW)

        fun saveQuietly(context: Context, id: Int, text: String, color: StickyColor) {
            prefs(context).edit().putString(KEY_TEXT + id, text).putString(KEY_COLOR + id, color.name).apply()
        }

        fun save(context: Context, id: Int, text: String, color: StickyColor) {
            saveQuietly(context, id, text, color)
            update(context, AppWidgetManager.getInstance(context), id)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, StickyWidget::class.java)).forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            // 백업에서 복원했는데 그때 메모가 모자랐다면, 새로 놓인 메모에 채움
            if (!prefs(context).contains(KEY_TEXT + id)) {
                WidgetBackup.takePendingSticky(context)?.let { (text, color) -> saveQuietly(context, id, text, color) }
            }
            val views = RemoteViews(context.packageName, R.layout.widget_sticky)
            val text = text(context, id)
            views.setTextViewText(R.id.sticky_text, text.ifBlank { "탭해서 메모 쓰기" })
            views.setTextColor(R.id.sticky_text, if (text.isBlank()) 0x66000000 else 0xE6000000.toInt())
            views.setInt(R.id.sticky_paper, "setColorFilter", color(context, id).argb.toInt())
            views.setOnClickPendingIntent(
                R.id.sticky_root,
                PendingIntent.getActivity(
                    context, id, StickyActivity.intent(context, id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            manager.updateAppWidget(id, views)
        }
    }
}
