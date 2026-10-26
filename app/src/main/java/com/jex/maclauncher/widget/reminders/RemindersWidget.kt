package com.jex.maclauncher.widget.reminders

import android.app.PendingIntent
import com.jex.maclauncher.widget.WidgetSize
import com.jex.maclauncher.widget.skin.Skin
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Paint
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.WidgetTheme

/** macOS 미리 알림 위젯: 동그라미 탭 = 완료, 글자 탭 = 수정, + = 추가 */
class RemindersWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    // 크기 변경(패널 비트맵 크기), One UI 다크 모드 전환 때도 옵션이 바뀌어 다시 그린다
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RemindersWidget::class.java))
            ids.forEach { id ->
                val views = Skin.dress(context, views(context, id), WidgetSize.of(manager, id))
                // 강조색은 dress(레이아웃 파랑 → 팩 blue) 뒤에 칠해야 남는다
                val accent = Skin.current(context).accent
                views.setTextColor(R.id.rem_count, accent)
                views.setTextColor(R.id.rem_add, accent)
                views.setTextColor(R.id.rem_clear, (accent and 0x00FFFFFF) or (0x99 shl 24))
                manager.updateAppWidget(id, views)
                manager.notifyAppWidgetViewDataChanged(id, R.id.rem_list)
            }
        }

        private fun views(context: Context, id: Int): RemoteViews =
            RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_reminders)).apply {
                setTextViewText(R.id.rem_count, ReminderStore.remaining(context).toString())
                val done = ReminderStore.completed(context)
                setViewVisibility(R.id.rem_clear, if (done > 0) View.VISIBLE else View.GONE)
                setTextViewText(R.id.rem_clear, "완료된 항목 ${done}개 지우기")
                setOnClickPendingIntent(R.id.rem_clear, action(context, ReminderActivity.ACTION_CLEAR, 1))
                setOnClickPendingIntent(R.id.rem_add, action(context, ReminderActivity.ACTION_ADD, 2))

                val adapter = Intent(context, RemindersService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("reminders://widget/$id"))
                setRemoteAdapter(R.id.rem_list, adapter)
                setEmptyView(R.id.rem_list, R.id.rem_empty)
                // 항목마다 fill-in으로 동작/id를 채우므로 MUTABLE
                setPendingIntentTemplate(
                    R.id.rem_list,
                    PendingIntent.getActivity(
                        context, 0, Intent(context, ReminderActivity::class.java),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }

        private fun action(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode,
                Intent(context, ReminderActivity::class.java).putExtra(ReminderActivity.EXTRA_ACTION, action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

/** 미리 알림 목록 항목 */
class RemindersService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var items: List<Reminder> = emptyList()

        override fun onCreate() = Unit
        override fun onDataSetChanged() {
            items = ReminderStore.all(context)
        }
        override fun onDestroy() = Unit
        override fun getCount() = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_reminders_item))
            val item = items.getOrNull(position) ?: return views
            views.setTextViewText(R.id.rem_title, item.title)
            views.setImageViewResource(R.id.rem_check, if (item.done) R.drawable.ic_check_on else R.drawable.ic_check_off)
            val p = WidgetTheme.palette(context)
            views.setTextColor(R.id.rem_title, if (item.done) p.subText else p.text)
            views.setInt(
                R.id.rem_title, "setPaintFlags",
                Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or (if (item.done) Paint.STRIKE_THRU_TEXT_FLAG else 0),
            )
            views.setOnClickFillInIntent(R.id.rem_check, fillIn(ReminderActivity.ACTION_TOGGLE, item.id))
            views.setOnClickFillInIntent(R.id.rem_title, fillIn(ReminderActivity.ACTION_EDIT, item.id))
            return views
        }

        private fun fillIn(action: String, id: Long) =
            Intent().putExtra(ReminderActivity.EXTRA_ACTION, action).putExtra(ReminderActivity.EXTRA_ID, id)

        // 기본 "Loading..." 글자 대신 빈 칸
        override fun getLoadingView(): RemoteViews = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_reminders_item))
        // 다크·라이트 항목 레이아웃 두 가지. 1이면 밝은↔어두운 팩 전환 뒤 항목이 '불러오는 중'에서 멈춘다
        override fun getViewTypeCount() = 2
        override fun getItemId(position: Int) = items.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds() = true
    }
}
