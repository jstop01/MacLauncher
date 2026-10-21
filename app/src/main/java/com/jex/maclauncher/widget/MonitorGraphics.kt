package com.jex.maclauncher.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/** 위젯은 직접 그릴 수 없어서 링/그래프를 비트맵으로 만들어 ImageView에 넣는다. */
object MonitorGraphics {

    private const val TRACK = 0xFF3A3A3C.toInt()
    private const val GRID = 0x1FFFFFFF

    /** CPU 링 게이지 (0..1) */
    fun ring(progress: Float, color: Int, size: Int = 180): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = size * 0.1f
        val r = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        p.color = TRACK
        c.drawArc(r, 0f, 360f, false, p)
        p.color = color
        c.drawArc(r, -90f, 360f * progress.coerceIn(0f, 1f), false, p)
        return bmp
    }

    /** 0..1 값을 오른쪽 끝이 최신인 선 그래프로. ImageView가 fitXY로 늘려 쓴다. */
    fun graph(values: List<Float>, maxPoints: Int, color: Int, width: Int = 360, height: Int = 110): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = width.toFloat()
        val h = height.toFloat()
        val inset = 3f   // 선 굵기만큼 위아래 여유

        val grid = Paint().apply { this.color = GRID; strokeWidth = 1.5f }
        for (i in 0..2) {
            val y = inset + (h - 2 * inset) * i / 2f
            c.drawLine(0f, y, w, y, grid)
        }
        if (values.size < 2) return bmp

        val step = w / (maxPoints - 1)
        val startX = w - step * (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = startX + step * i
            val y = h - inset - (h - 2 * inset) * v.coerceIn(0f, 1f)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path(line).apply {
            lineTo(w, h)
            lineTo(startX, h)
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h, (color and 0x00FFFFFF) or 0x55000000, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
        c.drawPath(area, fill)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
        c.drawPath(line, stroke)
        return bmp
    }
}
