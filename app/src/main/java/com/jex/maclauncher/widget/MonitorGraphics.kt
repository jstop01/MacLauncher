package com.jex.maclauncher.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** 위젯은 직접 그릴 수 없어서 링/그래프를 비트맵으로 만들어 ImageView에 넣는다. */
object MonitorGraphics {

    /**
     * 링 게이지 (0..1). track = 빈 부분 색 (테마), center = 가운데에 그릴 아이콘 (링 지름의 40%),
     * margin = 비트맵 가장자리 여백 비율 (ImageView가 공간을 채울 때 링이 너무 커지지 않게)
     */
    fun ring(progress: Float, color: Int, track: Int, size: Int = 180, center: Drawable? = null, margin: Float = 0f): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inset = size * margin
        val ring = size - 2 * inset
        val stroke = ring * 0.1f
        val r = RectF(inset + stroke / 2, inset + stroke / 2, size - inset - stroke / 2, size - inset - stroke / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        p.color = track
        c.drawArc(r, 0f, 360f, false, p)
        p.color = color
        c.drawArc(r, -90f, 360f * progress.coerceIn(0f, 1f), false, p)
        center?.let {
            val icon = (ring * 0.4f).toInt()
            val left = (size - icon) / 2
            it.setBounds(left, left, left + icon, left + icon)
            it.draw(c)
        }
        return bmp
    }

    /** 0..1 값을 오른쪽 끝이 최신인 선 그래프로. ImageView가 fitXY로 늘려 쓴다. */
    fun graph(values: List<Float>, maxPoints: Int, color: Int, gridColor: Int, width: Int = 360, height: Int = 110): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = width.toFloat()
        val h = height.toFloat()
        val inset = 3f   // 선 굵기만큼 위아래 여유

        val grid = Paint().apply { this.color = gridColor; strokeWidth = 1.5f }
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
