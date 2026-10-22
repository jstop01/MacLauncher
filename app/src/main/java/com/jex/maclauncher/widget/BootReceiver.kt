package com.jex.maclauncher.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jex.maclauncher.widget.apps.AppsWidget
import com.jex.maclauncher.widget.dock.DockWidget

/** 재부팅/앱 업데이트 후 위젯 갱신을 다시 시작 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CalendarWidget.updateAll(context)
        DockWidget.updateAll(context)
        AppsWidget.refreshAll(context)
        BatteryWidget.updateAll(context)
        if (MonitorWidget.hasWidgets(context)) MonitorService.start(context)
    }
}
