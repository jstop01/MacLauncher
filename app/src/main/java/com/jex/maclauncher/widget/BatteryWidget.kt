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
import com.jex.maclauncher.MainActivity
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.WidgetTheme
import org.lsposed.hiddenapibypass.HiddenApiBypass

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
            val views = RemoteViews(context.packageName, if (small) WidgetTheme.layout(context, R.layout.widget_battery_small) else WidgetTheme.layout(context, R.layout.widget_battery))
            val slots = if (small) 1 else SLOTS.size
            for (i in 0 until slots) {
                val b = batteries.getOrNull(i)
                if (!small) views.setViewVisibility(SLOTS[i], if (b != null) View.VISIBLE else View.GONE)
                if (b == null) continue
                val color = if (b.level <= 20 && !b.charging) RED else GREEN
                // 링이 위젯 크기만큼 커져도 선명하도록 큰 비트맵으로
                val icon = context.getDrawable(WidgetTheme.drawable(context, b.icon))
                views.setImageViewBitmap(
                    RINGS[i],
                    MonitorGraphics.ring(b.level / 100f, color, WidgetTheme.palette(context).track, size = 320, center = icon, margin = 0.15f),
                )
                views.setTextViewText(LEVELS[i], (if (b.charging) "⚡" else "") + "${b.level}%")
            }
            // 블루투스 권한이 없으면 안내를 보이고, 탭하면 앱에서 권한 요청 (권한 창은 액티비티에서만 띄울 수 있음)
            val needsPermission = !hasBluetoothPermission(context)
            if (!small) views.setViewVisibility(R.id.bat_hint, if (needsPermission) View.VISIBLE else View.GONE)
            val click = if (needsPermission) {
                PendingIntent.getActivity(
                    context, 1, MainActivity.requestBluetoothIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                PendingIntent.getBroadcast(
                    context, 0, Intent(context, BatteryWidget::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            views.setOnClickPendingIntent(R.id.bat_root, click)
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

        private fun hasBluetoothPermission(context: Context) =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        /**
         * 연결된 블루투스 기기 배터리. BluetoothDevice.getBatteryLevel()은 숨은 API이고
         * targetSdk 31+ 앱에선 리플렉션도 막혀 있어서 HiddenApiBypass로 호출한다.
         * 기기가 배터리를 안 알려주면(-1) 빠진다.
         */
        @SuppressLint("MissingPermission")
        private fun bluetooth(context: Context): List<Battery> {
            if (!hasBluetoothPermission(context)) return emptyList()
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()
            // 버즈 같은 LE 오디오 기기는 왼쪽/오른쪽 등 주소 여러 개로 잡혀서 이름이 같으면 하나로
            return runCatching { adapter.bondedDevices }.getOrNull().orEmpty().mapNotNull { device ->
                val level = runCatching {
                    HiddenApiBypass.invoke(BluetoothDevice::class.java, device, "getBatteryLevel") as Int
                }.getOrDefault(-1)
                if (level < 0) return@mapNotNull null
                val icon = when (device.bluetoothClass?.majorDeviceClass) {
                    BluetoothClass.Device.Major.AUDIO_VIDEO -> R.drawable.ic_bat_headphones
                    BluetoothClass.Device.Major.WEARABLE -> R.drawable.ic_bat_watch
                    else -> R.drawable.ic_bat_device
                }
                Battery(icon, level) to (runCatching { device.name }.getOrNull() ?: device.address)
            }
                .groupBy({ it.second }, { it.first })
                .map { (_, same) -> same.maxBy { it.level } }
                .take(SLOTS.size - 1)
        }
    }
}
