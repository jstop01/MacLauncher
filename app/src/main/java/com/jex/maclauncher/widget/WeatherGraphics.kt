package com.jex.maclauncher.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

/**
 * 날씨 상태 아이콘을 코드로 그린다 (해·달·구름·비·눈·안개·뇌우).
 * macOS 날씨처럼 색이 있는 아이콘이라 테마와 무관하게 쓰되, 구름·눈만 라이트에서 살짝 어둡게.
 */
object WeatherGraphics {

    enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, THUNDER }

    private const val SUN = 0xFFFFC107.toInt()
    private const val BOLT = 0xFFFFC928.toInt()
    private const val RAIN = 0xFF4AA3FF.toInt()

    /** WMO 날씨 코드 → 아이콘 종류 */
    fun sky(code: Int): Sky = when (code) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        in 51..57, in 61..67, in 80..82 -> Sky.RAIN
        in 71..77, 85, 86 -> Sky.SNOW
        in 95..99 -> Sky.THUNDER
        else -> Sky.CLOUDY
    }

    /** WMO 날씨 코드 → 한국어 설명 */
    fun describe(code: Int): String = when (code) {
        0 -> "맑음"
        1 -> "대체로 맑음"
        2 -> "부분적 흐림"
        3 -> "흐림"
        45, 48 -> "안개"
        51, 53, 55 -> "이슬비"
        56, 57 -> "어는 이슬비"
        61, 63, 65 -> "비"
        66, 67 -> "어는 비"
        71, 73, 75 -> "눈"
        77 -> "싸락눈"
        80, 81, 82 -> "소나기"
        85, 86 -> "눈 소나기"
        95 -> "뇌우"
        96, 99 -> "우박 뇌우"
        else -> "—"
    }

    private fun cloudColor(light: Boolean) = if (light) 0xFF9AA0AD.toInt() else 0xFFE4E7EE.toInt()
    private fun moonColor(light: Boolean) = if (light) 0xFFB9A94C.toInt() else 0xFFE8DB90.toInt()
    private fun snowColor(light: Boolean) = if (light) 0xFF8E99AC.toInt() else 0xFFEAF2FF.toInt()

    /** 상태 아이콘 한 장. px 정사각형, 가장자리 여백 포함. */
    fun bitmap(sky: Sky, isDay: Boolean, px: Int, light: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = px.toFloat()
        when (sky) {
            Sky.CLEAR -> if (isDay) sun(c, p, s * 0.5f, s * 0.5f, s * 0.22f) else moon(px, c, p, s * 0.52f, s * 0.48f, s * 0.26f, light)
            Sky.PARTLY -> {
                if (isDay) sun(c, p, s * 0.66f, s * 0.34f, s * 0.16f) else moon(px, c, p, s * 0.68f, s * 0.33f, s * 0.17f, light)
                cloud(c, p, cloudColor(light), s * 0.44f, s * 0.60f, s * 0.60f)
            }
            Sky.CLOUDY -> {
                cloud(c, p, cloudColor(light).let { dim(it) }, s * 0.60f, s * 0.42f, s * 0.46f)
                cloud(c, p, cloudColor(light), s * 0.44f, s * 0.58f, s * 0.62f)
            }
            Sky.FOG -> {
                cloud(c, p, cloudColor(light), s * 0.5f, s * 0.42f, s * 0.62f)
                p.color = cloudColor(light)
                p.strokeWidth = s * 0.055f
                p.strokeCap = Paint.Cap.ROUND
                for (i in 0..2) c.drawLine(s * 0.24f, s * 0.70f + i * s * 0.12f, s * 0.76f, s * 0.70f + i * s * 0.12f, p)
            }
            Sky.RAIN -> {
                cloud(c, p, cloudColor(light), s * 0.5f, s * 0.40f, s * 0.62f)
                p.color = RAIN
                p.strokeWidth = s * 0.05f
                p.strokeCap = Paint.Cap.ROUND
                for (i in 0..2) {
                    val x = s * (0.34f + i * 0.16f)
                    c.drawLine(x, s * 0.68f, x - s * 0.04f, s * 0.84f, p)
                }
            }
            Sky.SNOW -> {
                cloud(c, p, cloudColor(light), s * 0.5f, s * 0.40f, s * 0.62f)
                p.color = snowColor(light)
                for (i in 0..2) c.drawCircle(s * (0.34f + i * 0.16f), s * 0.76f, s * 0.035f, p)
            }
            Sky.THUNDER -> {
                cloud(c, p, dim(cloudColor(light)), s * 0.5f, s * 0.40f, s * 0.62f)
                p.color = BOLT
                val bolt = Path().apply {
                    moveTo(s * 0.52f, s * 0.60f)
                    lineTo(s * 0.40f, s * 0.80f)
                    lineTo(s * 0.49f, s * 0.80f)
                    lineTo(s * 0.44f, s * 0.95f)
                    lineTo(s * 0.62f, s * 0.72f)
                    lineTo(s * 0.52f, s * 0.72f)
                    lineTo(s * 0.58f, s * 0.60f)
                    close()
                }
                c.drawPath(bolt, p)
            }
        }
        return bmp
    }

    private fun sun(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        p.color = SUN
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy, r, p)
        p.strokeWidth = r * 0.3f
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val dx = kotlin.math.cos(a).toFloat()
            val dy = kotlin.math.sin(a).toFloat()
            c.drawLine(cx + dx * r * 1.4f, cy + dy * r * 1.4f, cx + dx * r * 1.85f, cy + dy * r * 1.85f, p)
        }
    }

    private fun moon(px: Int, c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, light: Boolean) {
        // 보름 원에서 한쪽을 지워 초승달
        val layer = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        val lp = Paint(Paint.ANTI_ALIAS_FLAG)
        lp.color = moonColor(light)
        lc.drawCircle(cx, cy, r, lp)
        lp.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        lc.drawCircle(cx + r * 0.6f, cy - r * 0.4f, r * 0.95f, lp)
        lp.xfermode = null
        c.drawBitmap(layer, 0f, 0f, null)
    }

    private fun cloud(c: Canvas, p: Paint, color: Int, cx: Float, cy: Float, w: Float) {
        p.color = color
        p.style = Paint.Style.FILL
        val h = w * 0.62f
        c.drawCircle(cx, cy - h * 0.05f, w * 0.28f, p)
        c.drawCircle(cx - w * 0.26f, cy + h * 0.10f, w * 0.20f, p)
        c.drawCircle(cx + w * 0.27f, cy + h * 0.07f, w * 0.22f, p)
        val rect = RectF(cx - w * 0.42f, cy + h * 0.00f, cx + w * 0.44f, cy + h * 0.30f)
        c.drawRoundRect(rect, h * 0.25f, h * 0.25f, p)
    }

    /** 뒤 구름은 살짝 어둡게 */
    private fun dim(color: Int): Int {
        val a = (color ushr 24) and 0xFF
        val r = ((color ushr 16) and 0xFF) * 80 / 100
        val g = ((color ushr 8) and 0xFF) * 80 / 100
        val b = (color and 0xFF) * 80 / 100
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
