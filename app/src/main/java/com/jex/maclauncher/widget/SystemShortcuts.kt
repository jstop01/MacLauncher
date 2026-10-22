package com.jex.maclauncher.widget

import android.content.Context
import android.content.Intent
import android.os.storage.StorageManager
import android.provider.Settings

/**
 * 위젯에서 기기 설정 화면으로 가는 바로가기.
 * 삼성 디바이스 케어는 기기/버전마다 화면 주소가 달라서 후보를 앞에서부터 시도하고,
 * 없으면 안드로이드 기본 설정 화면으로 간다.
 */
object SystemShortcuts {

    private const val DEVICE_CARE = "com.samsung.android.lool"

    /** 시스템 모니터 → 삼성 디바이스 케어(최적화) */
    fun deviceCare(context: Context): Intent = first(
        context,
        Intent("com.samsung.android.sm.ACTION_DASHBOARD").setPackage(DEVICE_CARE),
        context.packageManager.getLaunchIntentForPackage(DEVICE_CARE),
        Intent(Settings.ACTION_SETTINGS),
    )

    /** 저장 공간 → 삼성 저장공간 화면 (없으면 안드로이드 저장소 설정) */
    fun storage(context: Context): Intent = first(
        context,
        Intent("com.samsung.android.sm.ACTION_STORAGE").setPackage(DEVICE_CARE),
        Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        Intent(StorageManager.ACTION_MANAGE_STORAGE),
    )

    private fun first(context: Context, vararg candidates: Intent?): Intent {
        val pm = context.packageManager
        val found = candidates.filterNotNull().firstOrNull { it.resolveActivity(pm) != null }
            ?: Intent(Settings.ACTION_SETTINGS)
        return found.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
