package com.jex.maclauncher.widget

import android.appwidget.AppWidgetManager

/**
 * 홈 화면에 놓인 위젯의 현재 크기(dp). 가로/세로 화면 중 작은 쪽 기준.
 * 런처가 아직 크기를 안 알려줬으면 0이고, 그땐 큰 레이아웃을 쓴다.
 */
data class WidgetSize(val width: Int, val height: Int) {
    fun narrowerThan(dp: Int) = width in 1 until dp
    fun shorterThan(dp: Int) = height in 1 until dp

    companion object {
        fun of(manager: AppWidgetManager, id: Int): WidgetSize {
            val o = manager.getAppWidgetOptions(id)
            return WidgetSize(
                o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
            )
        }
    }
}
