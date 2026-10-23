package com.jex.maclauncher.widget.shortcuts

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.jex.maclauncher.R

/**
 * 단축어 타일 하나. [open]이 있으면 그 화면을 열고, 없으면 [ShortcutsWidget]이 브로드캐스트로 바로 실행(손전등·소리).
 * 색은 macOS 단축어 타일 색.
 */
enum class ShortcutAction(val label: String, val color: Long, val icon: Int, val open: ((Context) -> Intent)?) {
    FLASHLIGHT("손전등", 0xFFFF9F0A, R.drawable.ic_sc_flashlight, null),
    WIFI("와이파이", 0xFF0A84FF, R.drawable.ic_sc_wifi, { Intent(Settings.Panel.ACTION_WIFI) }),
    BLUETOOTH("블루투스", 0xFF5E5CE6, R.drawable.ic_sc_bluetooth, { Intent(Settings.ACTION_BLUETOOTH_SETTINGS) }),
    CAMERA("카메라 · QR", 0xFF30D158, R.drawable.ic_sc_camera, { Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA) }),
    TIMER("타이머", 0xFFFF375F, R.drawable.ic_sc_timer, { Intent(AlarmClock.ACTION_SET_TIMER) }),
    ALARM("알람", 0xFFFF453A, R.drawable.ic_sc_alarm, { Intent(AlarmClock.ACTION_SHOW_ALARMS) }),
    RECORD("녹음", 0xFFBF5AF2, R.drawable.ic_sc_mic, { Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION) }),
    CALCULATOR("계산기", 0xFF8E8E93, R.drawable.ic_sc_calculator, {
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
    }),
    SOUND("소리", 0xFF64D2FF, R.drawable.ic_sc_sound, null),
    INTERNET("인터넷", 0xFF32ADE6, R.drawable.ic_sc_internet, { Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) }),
    SETTINGS("설정", 0xFF636366, R.drawable.ic_sc_settings, { Intent(Settings.ACTION_SETTINGS) }),
    ;

    companion object {
        val DEFAULTS = listOf(FLASHLIGHT, WIFI, BLUETOOTH, CAMERA, TIMER, RECORD, CALCULATOR, SOUND)
        const val MAX = 8
    }
}

/** 브로드캐스트로 바로 실행하는 타일의 동작과 상태 */
object QuickToggles {

    private fun torchCamera(context: Context): String? {
        val cm = context.getSystemService(CameraManager::class.java)
        return cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    fun torchOn(context: Context) =
        context.getSharedPreferences("shortcuts_widget", Context.MODE_PRIVATE).getBoolean("torch", false)

    /** 손전등 켜기/끄기 (권한 필요 없음). 실패하면 false */
    fun toggleTorch(context: Context): Boolean = runCatching {
        val id = torchCamera(context) ?: return false
        val on = !torchOn(context)
        context.getSystemService(CameraManager::class.java).setTorchMode(id, on)
        context.getSharedPreferences("shortcuts_widget", Context.MODE_PRIVATE).edit().putBoolean("torch", on).apply()
    }.isSuccess

    fun vibrateOnly(context: Context) =
        context.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_VIBRATE

    /** 소리 ↔ 진동. 방해 금지 중이라 못 바꾸면 false (호출한 쪽이 소리 설정을 엶) */
    fun toggleRinger(context: Context): Boolean = runCatching {
        val am = context.getSystemService(AudioManager::class.java)
        am.ringerMode = if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
    }.isSuccess
}
