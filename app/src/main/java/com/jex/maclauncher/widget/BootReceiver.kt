package com.jex.maclauncher.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 재부팅/앱 업데이트 후 모든 위젯을 다시 그린다.
 * 업데이트하면 런처가 위젯을 처음 레이아웃(빈 값)으로 되돌리기 때문에 빠짐없이 채워야 한다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerWidget.restore(context, rebooted = intent.action == Intent.ACTION_BOOT_COMPLETED)
        CalendarWidget.resetMonth(context)
        WidgetRefresher.all(context, goAsync())
        if (MonitorService.needed(context)) MonitorService.start(context)
    }
}
