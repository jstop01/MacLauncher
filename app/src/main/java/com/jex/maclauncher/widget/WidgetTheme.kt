package com.jex.maclauncher.widget

import android.content.Context
import com.jex.maclauncher.R

/**
 * 위젯 라이트/다크 테마. 앱에서 바꾸면 이미 놓인 위젯을 모두 다시 그린다.
 * 라이트 레이아웃/드로어블은 tools/gen_light_widgets.py 가 다크 쪽에서 만든다.
 */
object WidgetTheme {
    private const val PREFS = "widget_theme"
    private const val KEY_LIGHT = "light"

    /** 코드에서 직접 그리는 색 (그래프, 달력 날짜, 미리 알림 글씨 등) */
    data class Palette(
        val text: Int,
        val subText: Int,
        val dim: Int,      // 앞뒤 달 날짜처럼 흐린 글씨
        val track: Int,    // 링/막대의 빈 부분
        val grid: Int,     // 그래프 가이드 선
        val panel: Int,    // 패널 바탕색 (막대 구간 사이 틈)
    )

    // Skin 팩에서도 재사용 (마이그레이션 중 공용 팔레트)
    val DARK = Palette(
        text = 0xFFFFFFFF.toInt(), subText = 0x99FFFFFF.toInt(), dim = 0x59FFFFFF,
        track = 0xFF3A3A3C.toInt(), grid = 0x1FFFFFFF, panel = 0xFF1E1E20.toInt(),
    )
    val LIGHT = Palette(
        text = 0xFF1C1C1E.toInt(), subText = 0xFF8E8E93.toInt(), dim = 0xFFC7C7CC.toInt(),
        track = 0xFFE5E5EA.toInt(), grid = 0x1F000000, panel = 0xFFF6F6F8.toInt(),
    )

    private val LIGHT_LAYOUTS = mapOf(
            R.layout.widget_apps to R.layout.widget_apps_light,
            R.layout.widget_battery to R.layout.widget_battery_light,
            R.layout.widget_battery_small to R.layout.widget_battery_small_light,
            R.layout.widget_calendar to R.layout.widget_calendar_light,
            R.layout.widget_calendar_small to R.layout.widget_calendar_small_light,
            R.layout.widget_dock_item to R.layout.widget_dock_item_light,
            R.layout.widget_dock to R.layout.widget_dock_light,
            R.layout.widget_finder_item to R.layout.widget_finder_item_light,
            R.layout.widget_finder to R.layout.widget_finder_light,
            R.layout.widget_monitor to R.layout.widget_monitor_light,
            R.layout.widget_monitor_small to R.layout.widget_monitor_small_light,
            R.layout.widget_reminders_item to R.layout.widget_reminders_item_light,
            R.layout.widget_reminders to R.layout.widget_reminders_light,
            R.layout.widget_storage to R.layout.widget_storage_light,
            // 날씨 위젯은 Skin 팩(런타임 색/배경)으로 전환되어 _light 레이아웃을 쓰지 않는다.
    )

    private val LIGHT_DRAWABLES = mapOf(
            R.drawable.widget_bg to R.drawable.widget_bg_light,
            R.drawable.finder_side_selected to R.drawable.finder_side_selected_light,
            R.drawable.ic_add_tile to R.drawable.ic_add_tile_light,
            R.drawable.ic_media_play to R.drawable.ic_media_play_light,
            R.drawable.ic_media_pause to R.drawable.ic_media_pause_light,
            R.drawable.ic_bat_phone to R.drawable.ic_bat_phone_light,
            R.drawable.ic_bat_headphones to R.drawable.ic_bat_headphones_light,
            R.drawable.ic_bat_watch to R.drawable.ic_bat_watch_light,
            R.drawable.ic_bat_device to R.drawable.ic_bat_device_light,
    )

    fun isLight(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LIGHT, false)

    /** 저장만 (다시 그리기는 호출한 쪽에서, 백업 복원용) */
    fun setLightQuietly(context: Context, light: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LIGHT, light).apply()
    }

    fun setLight(context: Context, light: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LIGHT, light).apply()
        WidgetRefresher.all(context)
    }

    /** 코드로 그리는 색. 감성 팩마다 다름 (맥 다크/라이트면 DARK/LIGHT 그대로) */
    fun palette(context: Context) = com.jex.maclauncher.widget.skin.Skin.current(context).palette

    /** 다크 레이아웃 id → 현재 테마의 레이아웃 id */
    fun layout(context: Context, dark: Int): Int = if (isLight(context)) LIGHT_LAYOUTS[dark] ?: dark else dark

    /** 다크 드로어블 id → 현재 테마의 드로어블 id */
    fun drawable(context: Context, dark: Int): Int = if (isLight(context)) LIGHT_DRAWABLES[dark] ?: dark else dark
}
