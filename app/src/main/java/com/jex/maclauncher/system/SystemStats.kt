package com.jex.maclauncher.system

import android.app.ActivityManager
import android.content.Context
import java.io.File

data class MemInfo(val usedMb: Long, val totalMb: Long) {
    val ratio: Float get() = if (totalMb == 0L) 0f else usedMb.toFloat() / totalMb
}

/**
 * CPU 사용률 측정.
 *  1) /proc/stat 읽기 시도 (일부 기기/루팅 환경에서만 허용)
 *  2) 실패 시 각 코어의 scaling_cur_freq / cpuinfo_max_freq 평균으로 추정 (삼성 기기는 보통 읽힘)
 */
class CpuReader {
    private var prevIdle = 0L
    private var prevTotal = 0L
    private var procStatBlocked = false

    private val coreCount: Int by lazy {
        File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }?.size
            ?: Runtime.getRuntime().availableProcessors()
    }

    fun read(): Float {
        if (!procStatBlocked) {
            readProcStat()?.let { return it }
            procStatBlocked = true
        }
        return readByFreq()
    }

    private fun readProcStat(): Float? = runCatching {
        val line = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return null
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 8 || parts[0] != "cpu") return null
        val values = parts.drop(1).map { it.toLong() }
        val idle = values[3] + values[4]
        val total = values.sum()
        val dIdle = idle - prevIdle
        val dTotal = total - prevTotal
        prevIdle = idle
        prevTotal = total
        if (dTotal <= 0) 0f else ((dTotal - dIdle).toFloat() / dTotal).coerceIn(0f, 1f)
    }.getOrNull()

    private fun readByFreq(): Float {
        var sum = 0f
        var n = 0
        for (i in 0 until coreCount) {
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            val cur = readLong(base + "scaling_cur_freq") ?: continue
            val max = readLong(base + "cpuinfo_max_freq") ?: continue
            if (max > 0) { sum += cur.toFloat() / max; n++ }
        }
        return if (n == 0) 0f else (sum / n).coerceIn(0f, 1f)
    }

    private fun readLong(path: String): Long? =
        runCatching { File(path).readText().trim().toLong() }.getOrNull()
}

object MemReader {
    fun read(context: Context): MemInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total = mi.totalMem / (1024 * 1024)
        val avail = mi.availMem / (1024 * 1024)
        return MemInfo(usedMb = total - avail, totalMb = total)
    }
}
