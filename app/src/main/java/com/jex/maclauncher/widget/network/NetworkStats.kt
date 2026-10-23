package com.jex.maclauncher.widget.network

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.SystemClock
import java.util.Calendar
import java.util.Locale

/** 실시간 속도(1초마다 [sample]), 연결 종류, 오늘 데이터 사용량 */
object NetworkStats {

    const val HISTORY = 60

    data class Live(
        val down: Long = 0,                     // 초당 바이트
        val up: Long = 0,
        val downHistory: List<Long> = emptyList(),
        val upHistory: List<Long> = emptyList(),
    )

    @Volatile
    var live = Live()
        private set

    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L

    /** 시스템 모니터 서비스가 1초마다 부름 */
    fun sample() {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()
        if (lastRx >= 0 && now > lastAt) {
            val sec = (now - lastAt) / 1000f
            val down = ((rx - lastRx).coerceAtLeast(0) / sec).toLong()
            val up = ((tx - lastTx).coerceAtLeast(0) / sec).toLong()
            val l = live
            live = Live(down, up, (l.downHistory + down).takeLast(HISTORY), (l.upHistory + up).takeLast(HISTORY))
        }
        lastRx = rx; lastTx = tx; lastAt = now
    }

    /** "Wi-Fi" / "모바일 데이터" / "이더넷" / "연결 안 됨" */
    fun connection(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "연결 안 됨"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "모바일 데이터"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "이더넷"
            else -> "연결됨"
        }
    }

    private var todayCache: Pair<Long, Pair<Long, Long>>? = null

    /** 오늘 (Wi-Fi, 모바일) 사용 바이트. 사용 정보 접근 권한 필요, 1분 동안 캐시 */
    fun today(context: Context): Pair<Long, Long>? {
        todayCache?.let { (at, value) -> if (System.currentTimeMillis() - at < 60_000) return value }
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val nsm = context.getSystemService(NetworkStatsManager::class.java)
        fun total(type: Int): Long = runCatching {
            nsm.querySummaryForDevice(type, null, start, now).let { it.rxBytes + it.txBytes }
        }.getOrDefault(-1)
        val wifi = total(ConnectivityManager.TYPE_WIFI)
        val mobile = total(ConnectivityManager.TYPE_MOBILE)
        if (wifi < 0 && mobile < 0) return null
        return (wifi.coerceAtLeast(0) to mobile.coerceAtLeast(0)).also { todayCache = now to it }
    }

    /** 초당 속도: "820 B/s", "12.3 KB/s", "4.5 MB/s" */
    fun speed(bytesPerSec: Long): String = when {
        bytesPerSec < 1_000 -> "$bytesPerSec B/s"
        bytesPerSec < 1_000_000 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1_000.0)
        else -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_000_000.0)
    }

    /** 데이터 양: "340MB", "1.2GB" */
    fun amount(bytes: Long): String = when {
        bytes < 1_000_000 -> "${bytes / 1_000}KB"
        bytes < 1_000_000_000 -> "${bytes / 1_000_000}MB"
        else -> String.format(Locale.US, "%.1fGB", bytes / 1_000_000_000.0)
    }
}
