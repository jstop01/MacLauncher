package com.jex.maclauncher.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jex.maclauncher.MainActivity
import com.jex.maclauncher.R
import com.jex.maclauncher.system.CpuReader
import com.jex.maclauncher.system.MemInfo
import com.jex.maclauncher.system.MemReader

data class MonitorState(
    val cpu: Float = 0f,                 // 0..1
    val cpuHistory: List<Float> = emptyList(),
    val mem: MemInfo = MemInfo(0, 0),
    val memHistory: List<Float> = emptyList(),
)

/**
 * 시스템 모니터 위젯을 1초마다 갱신하는 포그라운드 서비스.
 * 홈 위젯은 시스템이 최소 30분 간격으로만 갱신하므로 실시간으로 움직이려면 이 서비스가 필요하다.
 * 화면이 꺼져 있는 동안은 멈추고, 모니터 위젯이 하나도 없으면 스스로 종료한다.
 */
class MonitorService : Service() {

    companion object {
        const val HISTORY = 60   // 60초 분량
        private const val CHANNEL = "monitor"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var state = MonitorState()
            private set

        @Volatile
        var running = false
            private set

        /** 백그라운드에서 막히면(Android 12+) false. 위젯 탭이나 앱 화면에서 다시 시작하면 된다. */
        fun start(context: Context): Boolean = runCatching {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }.isSuccess
    }

    private val handler = Handler(Looper.getMainLooper())
    private val cpuReader = CpuReader()

    private val tick = object : Runnable {
        override fun run() {
            if (!MonitorWidget.hasWidgets(this@MonitorService)) {
                stopSelf()
                return
            }
            sample()
            MonitorWidget.updateAll(this@MonitorService)
            handler.postDelayed(this, 1000)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(tick)
            if (intent.action == Intent.ACTION_SCREEN_ON) handler.post(tick)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        running = true
        cpuReader.read() // 첫 샘플은 기준값만 잡음
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        if (getSystemService(PowerManager::class.java).isInteractive) handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterReceiver(screenReceiver)
        running = false
        MonitorWidget.updateAll(this) // "탭하면 실시간 갱신 시작" 표시
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun sample() {
        val cpu = cpuReader.read()
        val mem = MemReader.read(this)
        val s = state
        state = s.copy(
            cpu = cpu,
            cpuHistory = (s.cpuHistory + cpu).takeLast(HISTORY),
            mem = mem,
            memHistory = (s.memHistory + mem.ratio).takeLast(HISTORY),
        )
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        // 최소 중요도: 상태바 아이콘 없이 알림창에만 조용히
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "시스템 모니터 위젯", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle("시스템 모니터")
            .setContentText("위젯을 실시간으로 갱신하는 중")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }
}
