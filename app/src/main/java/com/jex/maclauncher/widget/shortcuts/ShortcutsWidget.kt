package com.jex.maclauncher.widget.shortcuts

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
import com.jex.maclauncher.widget.WidgetSize
import com.jex.maclauncher.widget.WidgetTheme

/** macOS 단축어 위젯: 고른 동작을 색 타일로, 2열 x (높이에 맞춰) 1~4줄 */
class ShortcutsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        updateAll(context)

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(KEY_ACTIONS + it) } }.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TORCH -> QuickToggles.toggleTorch(context)
            ACTION_RINGER -> if (!QuickToggles.toggleRinger(context)) {
                // 방해 금지 등으로 못 바꾸면 소리 설정을 열어 줌
                runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            else -> return
        }
        updateAll(context)
    }

    companion object {
        private const val ACTION_TORCH = "com.jex.maclauncher.shortcuts.TORCH"
        private const val ACTION_RINGER = "com.jex.maclauncher.shortcuts.RINGER"
        private const val KEY_ACTIONS = "actions_"

        private val TILES = intArrayOf(R.id.sc_tile0, R.id.sc_tile1, R.id.sc_tile2, R.id.sc_tile3, R.id.sc_tile4, R.id.sc_tile5, R.id.sc_tile6, R.id.sc_tile7)
        private val BGS = intArrayOf(R.id.sc_bg0, R.id.sc_bg1, R.id.sc_bg2, R.id.sc_bg3, R.id.sc_bg4, R.id.sc_bg5, R.id.sc_bg6, R.id.sc_bg7)
        private val ICONS = intArrayOf(R.id.sc_icon0, R.id.sc_icon1, R.id.sc_icon2, R.id.sc_icon3, R.id.sc_icon4, R.id.sc_icon5, R.id.sc_icon6, R.id.sc_icon7)
        private val LABELS = intArrayOf(R.id.sc_label0, R.id.sc_label1, R.id.sc_label2, R.id.sc_label3, R.id.sc_label4, R.id.sc_label5, R.id.sc_label6, R.id.sc_label7)
        private val ROWS = intArrayOf(R.id.sc_row0, R.id.sc_row1, R.id.sc_row2, R.id.sc_row3)

        private fun prefs(context: Context) = context.getSharedPreferences("shortcuts_widget", Context.MODE_PRIVATE)

        fun actions(context: Context, id: Int): List<ShortcutAction> =
            prefs(context).getString(KEY_ACTIONS + id, null)
                ?.split(",")?.mapNotNull { runCatching { ShortcutAction.valueOf(it) }.getOrNull() }
                ?: ShortcutAction.DEFAULTS

        fun save(context: Context, id: Int, actions: List<ShortcutAction>) {
            prefs(context).edit().putString(KEY_ACTIONS + id, actions.joinToString(",") { it.name }).apply()
            updateAll(context)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, ShortcutsWidget::class.java)).forEach { id ->
                WidgetSize.of(manager, id).let { size -> manager.updateAppWidget(id, Skin.dress(context, views(context, id, size), size)) }
            }
        }

        private fun views(context: Context, id: Int, size: WidgetSize): RemoteViews {
            val views = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_shortcuts))
            val actions = actions(context, id)
            // 타일 한 줄 약 58dp. 높이를 모르면 2줄
            val fitRows = if (size.height > 0) (size.height / 58).coerceIn(1, 4) else 2
            val rows = minOf(fitRows, (actions.size + 1) / 2).coerceAtLeast(1)
            ROWS.forEachIndexed { r, row -> views.setViewVisibility(row, if (r < rows) View.VISIBLE else View.GONE) }

            for (i in 0 until rows * 2) {
                val action = actions.getOrNull(i)
                views.setViewVisibility(TILES[i], if (action != null) View.VISIBLE else View.INVISIBLE)
                if (action == null) continue
                val on = when (action) {
                    ShortcutAction.FLASHLIGHT -> QuickToggles.torchOn(context)
                    else -> false
                }
                val (label, icon) = when (action) {
                    ShortcutAction.FLASHLIGHT -> (if (on) "손전등 켜짐" else "손전등") to action.icon
                    ShortcutAction.SOUND ->
                        if (QuickToggles.vibrateOnly(context)) "진동" to R.drawable.ic_sc_vibrate else "소리" to action.icon
                    else -> action.label to action.icon
                }
                // 켜진 손전등은 밝게 (macOS 단축어의 '실행 중' 느낌)
                views.setInt(BGS[i], "setColorFilter", if (on) 0xFFFFD60A.toInt() else action.color.toInt())
                views.setImageViewResource(ICONS[i], icon)
                views.setTextViewText(LABELS[i], label)
                views.setOnClickPendingIntent(TILES[i], click(context, action, i))
            }
            return views
        }

        private fun click(context: Context, action: ShortcutAction, slot: Int): PendingIntent {
            action.open?.let { open ->
                return PendingIntent.getActivity(
                    context, 100 + action.ordinal, open(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            val broadcast = if (action == ShortcutAction.FLASHLIGHT) ACTION_TORCH else ACTION_RINGER
            return PendingIntent.getBroadcast(
                context, slot, Intent(context, ShortcutsWidget::class.java).setAction(broadcast),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
