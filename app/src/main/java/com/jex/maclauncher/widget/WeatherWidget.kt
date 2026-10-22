package com.jex.maclauncher.widget

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.MainActivity
import com.jex.maclauncher.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * macOS 스타일 날씨 위젯. 기기 위치로 Open-Meteo(무료, API 키 없음)에서 현재 날씨와 예보를 가져온다.
 * 30분마다(updatePeriodMillis) 갱신되고, 탭하면 바로 새로고침. 마지막 결과는 캐시해 즉시 보여준다.
 */
class WeatherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        drawAll(context)          // 캐시로 즉시 그리기
        refresh(context, goAsync())  // 백그라운드에서 새로 가져오기
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        drawAll(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context, goAsync())
    }

    private data class Day(val label: String, val code: Int, val hi: Int, val lo: Int)

    private data class Weather(val temp: Int, val code: Int, val isDay: Boolean, val days: List<Day>)

    companion object {
        private const val ACTION_REFRESH = "com.jex.maclauncher.weather.REFRESH"
        private const val PREFS = "weather_widget"
        private const val KEY_BODY = "body"
        private const val KEY_CITY = "city"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"

        private val SLOTS = intArrayOf(R.id.wx_dslot0, R.id.wx_dslot1, R.id.wx_dslot2, R.id.wx_dslot3)
        private val DAYS = intArrayOf(R.id.wx_day0, R.id.wx_day1, R.id.wx_day2, R.id.wx_day3)
        private val DICONS = intArrayOf(R.id.wx_dicon0, R.id.wx_dicon1, R.id.wx_dicon2, R.id.wx_dicon3)
        private val DHIS = intArrayOf(R.id.wx_dhi0, R.id.wx_dhi1, R.id.wx_dhi2, R.id.wx_dhi3)
        private val DLOS = intArrayOf(R.id.wx_dlo0, R.id.wx_dlo1, R.id.wx_dlo2, R.id.wx_dlo3)

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun hasPermission(context: Context) =
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** 캐시된 데이터로 놓인 위젯을 모두 다시 그린다 (테마 변경·옵션 변경 시에도) */
        fun updateAll(context: Context) = drawAll(context)

        private fun drawAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidget::class.java))
            if (ids.isEmpty()) return
            val weather = runCatching { parse(prefs(context).getString(KEY_BODY, null)) }.getOrNull()
            val city = prefs(context).getString(KEY_CITY, null)
            val granted = hasPermission(context)
            ids.forEach { id -> manager.updateAppWidget(id, views(context, weather, city, granted, WidgetSize.of(manager, id))) }
        }

        /** 위치 → Open-Meteo 호출 → 캐시 저장 → 다시 그리기. 네트워크는 백그라운드 스레드. */
        private fun refresh(context: Context, pending: BroadcastReceiver.PendingResult?) {
            if (!hasPermission(context)) {
                drawAll(context)
                pending?.finish()
                return
            }
            Thread {
                try {
                    val loc = lastLocation(context) ?: cachedLocation(context)
                    if (loc != null) {
                        val body = fetch(loc.first, loc.second)
                        if (body != null) {
                            val city = cityName(context, loc.first, loc.second)
                            prefs(context).edit()
                                .putString(KEY_BODY, body)
                                .apply {
                                    if (city != null) putString(KEY_CITY, city)
                                    putFloat(KEY_LAT, loc.first.toFloat())
                                    putFloat(KEY_LON, loc.second.toFloat())
                                }
                                .apply()
                        }
                    }
                } catch (_: Exception) {
                    // 네트워크 실패 시 캐시 유지
                } finally {
                    drawAll(context)
                    pending?.finish()
                }
            }.start()
        }

        private fun views(context: Context, weather: Weather?, city: String?, granted: Boolean, size: WidgetSize): RemoteViews {
            val small = size.narrowerThan(200) || size.shorterThan(170)
            val light = WidgetTheme.isLight(context)
            return if (small) smallViews(context, weather, city, granted, light)
            else largeViews(context, weather, city, granted, light, size)
        }

        private fun largeViews(context: Context, weather: Weather?, city: String?, granted: Boolean, light: Boolean, size: WidgetSize): RemoteViews {
            val v = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_weather))
            v.setOnClickPendingIntent(R.id.wx_root, click(context, granted))
            v.setTextViewText(R.id.wx_city, city ?: "현재 위치")

            if (!granted) {
                v.setViewVisibility(R.id.wx_hint, View.VISIBLE)
                v.setTextViewText(R.id.wx_cond, "날씨를 보려면 위치 권한이 필요해요")
                v.setTextViewText(R.id.wx_temp, "--°")
                v.setTextViewText(R.id.wx_hilo, "")
                v.setImageViewBitmap(R.id.wx_icon, WeatherGraphics.bitmap(WeatherGraphics.Sky.CLOUDY, true, iconPx(context, 64), light))
                SLOTS.forEach { v.setViewVisibility(it, View.GONE) }
                return v
            }
            v.setViewVisibility(R.id.wx_hint, View.GONE)

            if (weather == null) {
                v.setTextViewText(R.id.wx_cond, "불러오는 중…")
                v.setTextViewText(R.id.wx_temp, "--°")
                v.setTextViewText(R.id.wx_hilo, "")
                v.setImageViewBitmap(R.id.wx_icon, WeatherGraphics.bitmap(WeatherGraphics.Sky.CLOUDY, true, iconPx(context, 64), light))
                SLOTS.forEach { v.setViewVisibility(it, View.GONE) }
                return v
            }

            v.setTextViewText(R.id.wx_cond, WeatherGraphics.describe(weather.code))
            v.setTextViewText(R.id.wx_temp, "${weather.temp}°")
            weather.days.firstOrNull()?.let { v.setTextViewText(R.id.wx_hilo, "최고 ${it.hi}°  최저 ${it.lo}°") }
            v.setImageViewBitmap(
                R.id.wx_icon,
                WeatherGraphics.bitmap(WeatherGraphics.sky(weather.code), weather.isDay, iconPx(context, 64), light),
            )

            val shown = weather.days.take(SLOTS.size)
            SLOTS.forEachIndexed { i, slot ->
                val d = shown.getOrNull(i)
                if (d == null) {
                    v.setViewVisibility(slot, View.GONE)
                    return@forEachIndexed
                }
                v.setViewVisibility(slot, View.VISIBLE)
                v.setTextViewText(DAYS[i], d.label)
                v.setTextViewText(DHIS[i], "${d.hi}°")
                v.setTextViewText(DLOS[i], "${d.lo}°")
                v.setImageViewBitmap(DICONS[i], WeatherGraphics.bitmap(WeatherGraphics.sky(d.code), true, iconPx(context, 30), light))
            }
            return v
        }

        private fun smallViews(context: Context, weather: Weather?, city: String?, granted: Boolean, light: Boolean): RemoteViews {
            val v = RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_weather_small))
            v.setOnClickPendingIntent(R.id.wx_small_root, click(context, granted))
            when {
                !granted -> {
                    v.setTextViewText(R.id.wx_small_temp, "--°")
                    v.setTextViewText(R.id.wx_small_city, "탭해서 권한 허용")
                    v.setImageViewBitmap(R.id.wx_small_icon, WeatherGraphics.bitmap(WeatherGraphics.Sky.CLOUDY, true, iconPx(context, 72), light))
                }
                weather == null -> {
                    v.setTextViewText(R.id.wx_small_temp, "--°")
                    v.setTextViewText(R.id.wx_small_city, "불러오는 중…")
                    v.setImageViewBitmap(R.id.wx_small_icon, WeatherGraphics.bitmap(WeatherGraphics.Sky.CLOUDY, true, iconPx(context, 72), light))
                }
                else -> {
                    v.setTextViewText(R.id.wx_small_temp, "${weather.temp}°")
                    v.setTextViewText(R.id.wx_small_city, city ?: WeatherGraphics.describe(weather.code))
                    v.setImageViewBitmap(
                        R.id.wx_small_icon,
                        WeatherGraphics.bitmap(WeatherGraphics.sky(weather.code), weather.isDay, iconPx(context, 72), light),
                    )
                }
            }
            return v
        }

        private fun click(context: Context, granted: Boolean): PendingIntent =
            if (!granted) {
                PendingIntent.getActivity(
                    context, 2, MainActivity.requestPermissionIntent(context, Manifest.permission.ACCESS_COARSE_LOCATION),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                PendingIntent.getBroadcast(
                    context, 0, Intent(context, WeatherWidget::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }

        private fun iconPx(context: Context, dp: Int) = (dp * context.resources.displayMetrics.density).roundToInt()

        // --- 위치 / 네트워크 ---------------------------------------------------

        @SuppressLint("MissingPermission")
        private fun lastLocation(context: Context): Pair<Double, Double>? {
            if (!hasPermission(context)) return null
            val lm = context.getSystemService(LocationManager::class.java) ?: return null
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            val loc: Location? = providers.mapNotNull { p ->
                runCatching { if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null }.getOrNull()
            }.maxByOrNull { it.time }
            return loc?.let { it.latitude to it.longitude }
        }

        private fun cachedLocation(context: Context): Pair<Double, Double>? {
            val p = prefs(context)
            if (!p.contains(KEY_LAT)) return null
            return p.getFloat(KEY_LAT, 0f).toDouble() to p.getFloat(KEY_LON, 0f).toDouble()
        }

        @Suppress("DEPRECATION")
        private fun cityName(context: Context, lat: Double, lon: Double): String? = runCatching {
            val a = Geocoder(context, Locale.KOREAN).getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea
        }.getOrNull()

        private fun fetch(lat: Double, lon: Double): String? {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=4",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }
            return try {
                if (conn.responseCode != 200) null
                else conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

        private fun parse(body: String?): Weather? {
            if (body.isNullOrBlank()) return null
            val obj = JSONObject(body)
            val cur = obj.getJSONObject("current")
            val daily = obj.getJSONObject("daily")
            val times = daily.getJSONArray("time")
            val codes = daily.getJSONArray("weather_code")
            val maxs = daily.getJSONArray("temperature_2m_max")
            val mins = daily.getJSONArray("temperature_2m_min")
            val today = LocalDate.now()
            val days = (0 until times.length()).map { i ->
                val date = LocalDate.parse(times.getString(i))
                val label = if (date == today) "오늘" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
                Day(label, codes.getInt(i), maxs.getDouble(i).roundToInt(), mins.getDouble(i).roundToInt())
            }
            return Weather(
                temp = cur.getDouble("temperature_2m").roundToInt(),
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
                days = days,
            )
        }
    }
}
