package com.jex.maclauncher.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * 앱 아이콘을 macOS처럼 스쿼클(둥근 사각형)로 잘라 그린다.
 * 그냥 getApplicationIcon().toBitmap()을 쓰면 런처/기기 기본 모양(픽셀은 원)으로 잘려 나오므로,
 * 적응형 아이콘의 배경·전경 두 겹을 직접 그리고 모양은 우리가 정한다.
 */
object MacIcon {

    fun of(context: Context, packageName: String, size: Int): Bitmap? =
        runCatching { draw(context.packageManager.getApplicationIcon(packageName), size) }.getOrNull()

    fun draw(icon: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (icon is AdaptiveIconDrawable) {
            c.clipPath(squircle(size.toFloat()))
            // 적응형 아이콘 레이어는 보이는 영역보다 사방 25%씩 크게 그려진다 (108dp 중 72dp가 보임)
            val inset = size / 4
            for (layer in listOfNotNull(icon.background, icon.foreground)) {
                layer.setBounds(-inset, -inset, size + inset, size + inset)
                layer.draw(c)
            }
        } else {
            // 예전 방식 아이콘은 모양이 이미 그려져 있으니 그대로
            icon.setBounds(0, 0, size, size)
            icon.draw(c)
        }
        return bmp
    }

    /** macOS Big Sur 아이콘 모양에 가까운 초타원(n=5) */
    private fun squircle(size: Float, n: Double = 5.0): Path {
        val r = size / 2
        val path = Path()
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val ct = cos(t)
            val st = sin(t)
            val x = r + r * (sign(ct) * abs(ct).pow(2 / n)).toFloat()
            val y = r + r * (sign(st) * abs(st).pow(2 / n)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}
