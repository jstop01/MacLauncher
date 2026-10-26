package com.jex.maclauncher.widget.skin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 감성 팩의 패널 배경을 비트맵 한 장으로 그린다.
 * 레이아웃 맨 밑 ImageView(skin_bg)에 깔아, 레이아웃을 늘리지 않고도 팩마다 배경을 자유롭게 바꾼다.
 */
object SkinGraphics {

    // 시스템 모니터처럼 1초마다 다시 그리는 위젯이 매번 비트맵을 새로 만들지 않게
    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) = size > 12
    }

    /** 위젯 픽셀 크기에 맞춘 패널 배경. cornerPx·strokePx는 이미 density 반영된 값. */
    fun panel(pack: SkinPack, wPx: Int, hPx: Int, cornerPx: Float, strokePx: Float): Bitmap = synchronized(cache) {
        cache.getOrPut("${pack.id}:$wPx:$hPx:$cornerPx:$strokePx") { draw(pack, wPx, hPx, cornerPx, strokePx) }
    }

    private fun draw(pack: SkinPack, wPx: Int, hPx: Int, cornerPx: Float, strokePx: Float): Bitmap {
        val w = wPx.coerceIn(8, 2000)
        val h = hPx.coerceIn(8, 2000)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val inset = strokePx / 2f
        val rect = RectF(inset, inset, w - inset, h - inset)
        val r = max(0f, cornerPx - inset)

        when (val s = pack.panel) {
            is PanelStyle.Solid -> {
                p.color = s.color
                c.drawRoundRect(rect, r, r, p)
                stroke(c, p, rect, r, strokePx, s.stroke)
            }
            is PanelStyle.Gradient -> {
                val a = Math.toRadians(s.angleDeg.toDouble())
                val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
                p.shader = LinearGradient(
                    w / 2f - dx * w / 2f, h / 2f - dy * h / 2f,
                    w / 2f + dx * w / 2f, h / 2f + dy * h / 2f,
                    s.from, s.to, Shader.TileMode.CLAMP,
                )
                c.drawRoundRect(rect, r, r, p)
                p.shader = null
                stroke(c, p, rect, r, strokePx, s.stroke)
            }
            is PanelStyle.Glass -> {
                p.color = (s.alpha shl 24) or (s.tint and 0x00FFFFFF)
                c.drawRoundRect(rect, r, r, p)
                stroke(c, p, rect, r, strokePx, s.stroke)
            }
        }
        return bmp
    }

    private fun stroke(c: Canvas, p: Paint, rect: RectF, r: Float, strokePx: Float, color: Int) {
        if (strokePx <= 0f || color == 0) return
        p.style = Paint.Style.STROKE
        p.strokeWidth = strokePx
        p.color = color
        c.drawRoundRect(rect, r, r, p)
        p.style = Paint.Style.FILL
    }
}
