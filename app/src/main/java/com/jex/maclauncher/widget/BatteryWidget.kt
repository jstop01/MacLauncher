package com.jex.maclauncher.widget

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.R

/** macOS 배터리 위젯: 폰 + 배터리를 알려주는 연결된 블루투스 기기(최대 3) */
class BatteryWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        updateAll(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 탭 새로고침 / 블루투스 기기 연결·해제 (ACL 브로드캐스트는 manifest 수신 허용)
        when (intent.action) {
            ACTION_REFRESH, BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> updateAll(context)
        }
    }

    private data class Battery(val icon: Int, val level: Int, val charging: Boolean = false)

    companion object {
        private const val ACTION_REFRESH = "com.jex.maclauncher.battery.REFRESH"
        private const val GREEN = 0xFF30D158.toInt()
        private const val RED = 0xFFFF453A.toInt()

        private val SLOTS = intArrayOf(R.id.bat_slot0, R.id.bat_slot1, R.id.bat_slot2, R.id.bat_slot3)
        private val RINGS = intArrayOf(R.id.bat_ring0, R.id.bat_ring1, R.id.bat_ring2, R.id.bat_ring3)
        private val ICONS = intArrayOf(R.id.bat_icon0, R.id.bat_icon1, R.id.bat_icon2, R.id.bat_icon3)
        private val LEVELS = intArrayOf(R.id.bat_level0, R.id.bat_level1, R.id.bat_level2, R.id.bat_level3)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryWidget::class.java))
            if (ids.isEmpty()) return
            val batteries = listOf(phone(context)) + bluetooth(context)
            ids.forEach { id -> manager.updateAppWidget(id, views(context, batteries, WidgetSize.of(manager, id))) }
        }

        private fun views(context: Context, batteries: List<Battery>, size: WidgetSize): RemoteViews {
            val small = size.narrowerThan(200)
            val views = RemoteViews(context.packageName, if (small) R.layout.widget_battery_small else R.layout.widget_battery)
            val slots = if (small) 1 else SLOTS.size
            for (i in 0 until slots) {
                val b = batteries.getOrNull(i)
                if (!small) views.setViewVisibility(SLOTS[i], if (b != null) View.VISIBLE else View.GONE)
                if (b == null) continue
                val color = if (b.level <= 20 && !b.charging) RED else GREEN
                views.setImageViewBitmap(RINGS[i], MonitorGraphics.ring(b.level / 100f, color))
                views.setImageViewResource(ICONS[i], b.icon)
                views.setTextViewText(LEVELS[i], (if (b.charging) "⚡" else "") + "${b.level}%")
            }
            views.setOnClickPendingIntent(
                R.id.bat_root,
                PendingIntent.getBroadcast(
                    context, 0, Intent(context, BatteryWidget::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return views
        }

        private fun phone(context: Context): Battery {
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return Battery(
                R.drawable.ic_bat_phone,
                if (level < 0 || scale <= 0) 0 else level * 100 / scale,
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }

        /**
         * 연결된 블루투스 기기 배터리. BluetoothDevice.getBatteryLevel()이 숨은 API라 리플렉션으로 읽고,
         * 기기가 배터리를 안 알려주거나(-1) 막혀 있으면 빠진다.
         */
        @SuppressLint("MissingPermission")
        private fun bluetooth(context: Context): List<Battery> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) return emptyList()
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()
            return runCatching { adapter.bondedDevices }.getOrNull().orEmpty().mapNotNull { device ->
                val level = runCatching { device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int }.getOrDefault(-1)
                if (level < 0) return@mapNotNull null
                val icon = when (device.bluetoothClass?.majorDeviceClass) {
                    BluetoothClass.Device.Major.AUDIO_VIDEO -> R.drawable.ic_bat_headphones
                    BluetoothClass.Device.Major.WEARABLE -> R.drawable.ic_bat_watch
                    else -> R.drawable.ic_bat_device
                }
                Battery(icon, level)
            }.take(SLOTS.size - 1)
        }
    }
}
