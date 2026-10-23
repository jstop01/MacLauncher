package com.jex.maclauncher.widget

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.provider.AlarmClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.jex.maclauncher.R

/**
 * macOS 스타일 타이머 (모든 타이머 위젯이 타이머 하나를 같이 씀).
 * 남은 시간 숫자는 Chronometer가 스스로 줄이고, 링은 도는 동안 15초마다 다시 그림.
 * 끝나면 정확한 알람으로 깨어나 알림음을 울린다.
 */
class TimerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_START -> start(context, intent.getIntExtra(EXTRA_MINUTES, 5) * 60_000L)
            ACTION_TOGGLE -> if (state(context) == STATE_RUNNING) pause(context) else resume(context)
            ACTION_CANCEL -> reset(context)
            ACTION_DONE -> finish(context)
            ACTION_TICK -> Unit
            else -> return
        }
        updateAll(context)
        scheduleTick(context)
    }

    companion object {
        private const val ACTION_START = "com.jex.maclauncher.timer.START"
        private const val ACTION_TOGGLE = "com.jex.maclauncher.timer.TOGGLE"
        private const val ACTION_CANCEL = "com.jex.maclauncher.timer.CANCEL"
        private const val ACTION_DONE = "com.jex.maclauncher.timer.DONE"
        private const val ACTION_TICK = "com.jex.maclauncher.timer.TICK"
        private const val EXTRA_MINUTES = "minutes"

        private const val STATE_IDLE = 0
        private const val STATE_RUNNING = 1
        private const val STATE_PAUSED = 2

        private const val ORANGE = 0xFFFF9F0A.toInt()
        private const val CHANNEL = "timer"
        private const val NOTIFICATION_ID = 2
        private val PRESETS = intArrayOf(1, 5, 10, 25)
        private val PRESET_VIEWS = intArrayOf(R.id.tm_preset1, R.id.tm_preset5, R.id.tm_preset10, R.id.tm_preset25)

        private fun prefs(context: Context) = context.getSharedPreferences("timer_widget", Context.MODE_PRIVATE)
        private fun state(context: Context) = prefs(context).getInt("state", STATE_IDLE)
        private fun duration(context: Context) = prefs(context).getLong("duration", 0)
        /** 도는 중: 끝나는 시각(부팅 후 경과 시간 기준) */
        private fun endAt(context: Context) = prefs(context).getLong("end", 0)
        /** 일시정지: 남은 시간 */
        private fun left(context: Context) = prefs(context).getLong("left", 0)

        private fun remaining(context: Context): Long = when (state(context)) {
            STATE_RUNNING -> (endAt(context) - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            STATE_PAUSED -> left(context)
            else -> 0
        }

        private fun start(context: Context, ms: Long) {
            prefs(context).edit().putInt("state", STATE_RUNNING).putLong("duration", ms)
                .putLong("end", SystemClock.elapsedRealtime() + ms).putBoolean("done", false).apply()
            scheduleDone(context, ms)
        }

        private fun pause(context: Context) {
            prefs(context).edit().putInt("state", STATE_PAUSED).putLong("left", remaining(context)).apply()
            cancelAlarm(context, ACTION_DONE)
        }

        private fun resume(context: Context) {
            val left = left(context)
            prefs(context).edit().putInt("state", STATE_RUNNING).putLong("end", SystemClock.elapsedRealtime() + left).apply()
            scheduleDone(context, left)
        }

        private fun reset(context: Context) {
            prefs(context).edit().putInt("state", STATE_IDLE).putBoolean("done", false).apply()
            cancelAlarm(context, ACTION_DONE)
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }

        private fun finish(context: Context) {
            if (state(context) != STATE_RUNNING) return
            prefs(context).edit().putInt("state", STATE_IDLE).putBoolean("done", true).apply()
            notifyDone(context)
        }

        /**
         * 재부팅: 경과 시간이 0부터 다시 시작해 도는 타이머를 이어갈 수 없으니 멈춤.
         * 앱 업데이트: 예약 알람이 지워지니 다시 예약.
         */
        fun restore(context: Context, rebooted: Boolean) {
            if (state(context) != STATE_RUNNING) return
            if (rebooted) reset(context)
            else if (remaining(context) == 0L) finish(context)
            else { scheduleDone(context, remaining(context)); scheduleTick(context) }
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_timer))
            val state = state(context)
            val remaining = remaining(context)
            val total = duration(context).coerceAtLeast(1)
            val p = WidgetTheme.palette(context)

            val progress = if (state == STATE_IDLE) 0f else remaining / total.toFloat()
            views.setImageViewBitmap(R.id.tm_ring, MonitorGraphics.ring(progress, ORANGE, p.track, size = 320, margin = 0.04f))

            // Chronometer: 도는 중이면 스스로 줄어들고, 아니면 멈춘 숫자
            val shown = if (state == STATE_IDLE) duration(context) else remaining
            views.setChronometer(R.id.tm_time, SystemClock.elapsedRealtime() + shown, null, state == STATE_RUNNING)
            views.setChronometerCountDown(R.id.tm_time, true)
            views.setTextViewText(
                R.id.tm_label,
                when {
                    state == STATE_PAUSED -> "일시정지"
                    state == STATE_RUNNING -> "${total / 60_000}분 타이머"
                    prefs(context).getBoolean("done", false) -> "완료"
                    else -> "타이머"
                },
            )

            val idle = state == STATE_IDLE
            views.setViewVisibility(R.id.tm_presets, if (idle) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.tm_controls, if (idle) View.GONE else View.VISIBLE)
            PRESETS.forEachIndexed { i, min ->
                views.setOnClickPendingIntent(PRESET_VIEWS[i], broadcast(context, ACTION_START, i) { putExtra(EXTRA_MINUTES, min) })
            }
            views.setImageViewResource(
                R.id.tm_toggle,
                WidgetTheme.drawable(context, if (state == STATE_RUNNING) R.drawable.ic_media_pause else R.drawable.ic_media_play),
            )
            views.setOnClickPendingIntent(R.id.tm_toggle, broadcast(context, ACTION_TOGGLE, 10))
            views.setOnClickPendingIntent(R.id.tm_cancel, broadcast(context, ACTION_CANCEL, 11))
            views.setOnClickPendingIntent(
                R.id.tm_face,
                PendingIntent.getActivity(
                    context, 12, Intent(AlarmClock.ACTION_SHOW_TIMERS),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return views
        }

        // ── 알람 ──

        private fun alarmIntent(context: Context, action: String) = PendingIntent.getBroadcast(
            context, action.hashCode(), Intent(context, TimerWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun scheduleDone(context: Context, inMs: Long) {
            val am = context.getSystemService(AlarmManager::class.java)
            val at = SystemClock.elapsedRealtime() + inMs
            val pi = alarmIntent(context, ACTION_DONE)
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }

        /** 링을 다시 그리는 가벼운 깨움 (화면 꺼져 있으면 미뤄져도 됨) */
        private fun scheduleTick(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            if (state(context) != STATE_RUNNING) return cancelAlarm(context, ACTION_TICK)
            am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 15_000, alarmIntent(context, ACTION_TICK))
        }

        private fun cancelAlarm(context: Context, action: String) =
            context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, action))

        private fun notifyDone(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "타이머", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                    enableVibration(true)
                },
            )
            val notification: Notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_sc_timer)
                .setContentTitle("타이머 종료")
                .setContentText("${duration(context) / 60_000}분 타이머가 끝났어요")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(NOTIFICATION_ID, notification) }
        }

        private fun broadcast(context: Context, action: String, slot: Int, extras: Intent.() -> Unit = {}): PendingIntent =
            PendingIntent.getBroadcast(
                context, slot, Intent(context, TimerWidget::class.java).setAction(action).apply(extras),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
