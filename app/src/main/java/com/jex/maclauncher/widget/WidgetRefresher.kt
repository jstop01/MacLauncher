package com.jex.maclauncher.widget

import android.content.BroadcastReceiver
import android.content.Context
import com.jex.maclauncher.widget.apps.AppsWidget
import com.jex.maclauncher.widget.dock.DockWidget
import com.jex.maclauncher.widget.finder.FinderWidget
import com.jex.maclauncher.widget.music.MusicWidget
import com.jex.maclauncher.widget.screentime.ScreenTimeWidget
import com.jex.maclauncher.widget.shortcuts.ShortcutsWidget
import com.jex.maclauncher.widget.reminders.RemindersWidget
import com.jex.maclauncher.widget.sticky.StickyWidget

/** 놓여 있는 모든 위젯을 다시 그린다 (재부팅/앱 업데이트 후, 테마 변경 시) */
object WidgetRefresher {
    fun all(context: Context, pending: BroadcastReceiver.PendingResult? = null) {
        CalendarWidget.updateAll(context)
        ClockWidget.updateAll(context)
        TimerWidget.updateAll(context)
        ShortcutsWidget.updateAll(context)
        ScreenTimeWidget.refreshAll(context)
        MusicWidget.updateAll(context)
        StickyWidget.updateAll(context)
        DockWidget.updateAll(context)
        AppsWidget.refreshAll(context)
        FinderWidget.refreshAll(context)
        RemindersWidget.updateAll(context)
        BatteryWidget.updateAll(context)
        WeatherWidget.updateAll(context)
        MonitorWidget.updateAll(context)
        PhotoWidget.refreshAll(context, keepPhoto = true)
        StorageWidget.refreshAll(context, pending)
    }
}
