package com.jex.maclauncher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.skin.Skin
import com.jex.maclauncher.widget.skin.SkinPack
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * macOS 스타일 시계. 작게 놓으면 아날로그 시계 하나, 넓게 놓으면 세계 시계(최대 4개 도시).
 * 바늘은 AnalogClock이 알아서 돌리므로 앱이 따로 갱신하지 않는다.
 */
class ClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        updateAll(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) updateAll(context)
    }

    private data class City(val name: String, val zone: String)

    companion object {
        /** 세계 시계 도시. 첫 칸은 기기 시간대(이름은 아래 표에서 찾고 없으면 '현지') */
        private val WORLD = listOf(
            City("뉴욕", "America/New_York"),
            City("런던", "Europe/London"),
            City("도쿄", "Asia/Tokyo"),
            City("파리", "Europe/Paris"),
        )
        private val NAMES = mapOf(
            "Asia/Seoul" to "서울", "Asia/Tokyo" to "도쿄", "America/New_York" to "뉴욕",
            "Europe/London" to "런던", "Europe/Paris" to "파리",
        )

        private val SLOTS = intArrayOf(R.id.clk_slot0, R.id.clk_slot1, R.id.clk_slot2, R.id.clk_slot3)
        private val FACES = intArrayOf(R.id.clk_face0, R.id.clk_face1, R.id.clk_face2, R.id.clk_face3)
        private val CITIES = intArrayOf(R.id.clk_city0, R.id.clk_city1, R.id.clk_city2, R.id.clk_city3)
        private val OFFSETS = intArrayOf(R.id.clk_offset0, R.id.clk_offset1, R.id.clk_offset2, R.id.clk_offset3)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id -> manager.updateAppWidget(id, views(context, WidgetSize.of(manager, id))) }
        }

        private fun views(context: Context, size: WidgetSize): RemoteViews {
            val world = !size.narrowerThan(250)
            val pack = Skin.current(context)
            val views = RemoteViews(
                context.packageName,
                if (world) R.layout.widget_clock else R.layout.widget_clock_small,
            )
            Skin.applyPanel(context, views, R.id.skin_bg, pack, size)
            val local = TimeZone.getDefault()
            // 지금 시차가 0인 도시(서울 기준 도쿄 등)는 겹쳐 보여서 뺌
            val now = System.currentTimeMillis()
            val others = WORLD.filter { TimeZone.getTimeZone(it.zone).getOffset(now) != local.getOffset(now) }
            val cities = (listOf(City(NAMES[local.id] ?: "현지", local.id)) + others)
                .take(if (world) SLOTS.size else 1)
            // 칸이 좁으면 도시 수를 줄임 (한 칸 최소 약 80dp)
            val shown = if (world) cities.take((size.width / 80).coerceIn(2, SLOTS.size)) else cities
            val face = faceIcons(context, size, if (world) shown.size else 1, world, pack)

            shown.forEachIndexed { i, city ->
                face?.let { f -> f.forEach { (method, icon) -> views.setIcon(FACES[i], method, icon) } }
                // 다른 시간대 바늘은 안드로이드 12+ 에서만 (그 아래는 기기 시간)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) views.setString(FACES[i], "setTimeZone", city.zone)
                if (world) {
                    views.setViewVisibility(SLOTS[i], View.VISIBLE)
                    views.setTextViewText(CITIES[i], city.name)
                    views.setTextColor(CITIES[i], pack.palette.text)
                    views.setTextViewText(OFFSETS[i], offset(local, TimeZone.getTimeZone(city.zone)))
                    views.setTextColor(OFFSETS[i], pack.palette.subText)
                }
            }
            if (world) for (i in shown.size until SLOTS.size) views.setViewVisibility(SLOTS[i], View.GONE)

            views.setOnClickPendingIntent(
                R.id.clk_root,
                PendingIntent.getActivity(
                    context, 0, Intent(AlarmClock.ACTION_SHOW_ALARMS),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return views
        }

        /**
         * 다이얼과 바늘을 칸 크기에 맞춘 같은 크기 비트맵으로 (안드로이드 12+).
         * AnalogClock은 그림을 줄이기만 하고 키우지는 않아서, 위젯이 커도 시계가 작게 나오지 않도록.
         */
        private fun faceIcons(context: Context, size: WidgetSize, count: Int, world: Boolean, pack: SkinPack): List<Pair<String, Icon>>? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            val density = context.resources.displayMetrics.density
            val w = if (size.width > 0) size.width else 150
            val h = if (size.height > 0) size.height else 150
            // 세계 시계는 아래 도시·시차 글씨 자리(약 40dp)를 뺌
            val dp = if (world) minOf(w / count - 12, h - 24 - 40) else minOf(w, h) - 16
            // 런처가 알려주는 크기가 실제보다 작은 경우가 많아 넉넉히 키움 (AnalogClock이 칸에 맞게 줄여줌)
            val px = (dp * density * 1.4f).roundToInt().coerceIn(160, 560)
            val p = pack.palette
            return listOf(
                "setDial" to Icon.createWithBitmap(dial(px, pack)),
                "setHourHand" to Icon.createWithBitmap(hand(px, 0.50f, 0.055f, p.text)),
                "setMinuteHand" to Icon.createWithBitmap(hand(px, 0.78f, 0.035f, p.text)),
                // 초침은 macOS 상징색(주황) 고정 — 팩이 바뀌어도 시계의 정체성 유지
                "setSecondHand" to Icon.createWithBitmap(hand(px, 0.84f, 0.014f, 0xFFFF9F0A.toInt(), tail = 0.18f, dot = 0.035f)),
            )
        }

        /** 가운데에서 위로 뻗은 바늘 (길이·굵기는 반지름 비율) */
        private fun hand(size: Int, length: Float, width: Float, color: Int, tail: Float = 0f, dot: Float = 0f): Bitmap {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = size / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = r * width * 2
                strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(r, r + r * tail, r, r - r * length, paint)
            if (dot > 0) c.drawCircle(r, r, r * dot * 2, paint)
            return bmp
        }

        /** "오늘 · -13시간" 같은 시차 (같은 시간대면 "현재 위치") */
        private fun offset(local: TimeZone, other: TimeZone): String {
            val now = System.currentTimeMillis()
            val diffMin = (other.getOffset(now) - local.getOffset(now)) / 60_000
            if (diffMin == 0) return "현재 위치"
            val h = diffMin / 60
            val m = kotlin.math.abs(diffMin % 60)
            return (if (diffMin > 0) "+" else "") + "${h}시간" + (if (m != 0) " ${m}분" else "")
        }

        /** 숫자 1~12와 분 눈금이 있는 macOS 시계 판. 색은 감성 팩을 따른다. */
        private fun dial(size: Int, pack: SkinPack): Bitmap {
            val p = pack.palette
            val light = pack.isLight
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = size / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = if (light) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            c.drawCircle(r, r, r, paint)

            paint.strokeWidth = size * 0.008f
            paint.color = p.dim
            for (i in 0 until 60) {
                if (i % 5 == 0) continue
                val a = Math.toRadians(i * 6.0)
                val (dx, dy) = sin(a).toFloat() to -cos(a).toFloat()
                c.drawLine(r + dx * r * 0.90f, r + dy * r * 0.90f, r + dx * r * 0.95f, r + dy * r * 0.95f, paint)
            }

            paint.color = p.text
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = size * 0.15f
            paint.typeface = Typeface.create(Typeface.DEFAULT, 600, false)
            val baseline = (paint.descent() + paint.ascent()) / 2
            for (n in 1..12) {
                val a = Math.toRadians(n * 30.0)
                val tr = r * 0.72f
                c.drawText("$n", r + tr * sin(a).toFloat(), r - tr * cos(a).toFloat() - baseline, paint)
            }
            return bmp
        }
    }
}
