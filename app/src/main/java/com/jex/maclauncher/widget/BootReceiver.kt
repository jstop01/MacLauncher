package com.jex.maclauncher.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * 재부팅/앱 업데이트 후 모든 위젯을 다시 그린다.
 * 업데이트하면 런처가 위젯을 처음 레이아웃(빈 값)으로 되돌리기 때문에 빠짐없이 채워야 한다.
 * 그 되돌림이 우리 갱신보다 늦게 도착하면 빈 Dock이 남아서, 몇 초 뒤 한 번 더 그린다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerWidget.restore(context, rebooted = intent.action == Intent.ACTION_BOOT_COMPLETED)
        CalendarWidget.resetMonth(context)
        WidgetRefresher.all(context, goAsync())
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({ WidgetRefresher.all(app) }, 4000)
        if (MonitorService.needed(context)) MonitorService.start(context)
    }
}
