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
        rawIcon(context, packageName)?.let { runCatching { draw(it, size) }.getOrNull() }

    /**
     * 앱 APK에 들어 있는 원본 아이콘.
     * 삼성(One UI)의 getApplicationIcon()은 테마 모양으로 이미 잘리고 흰 판까지 깔린 비트맵을 줘서
     * 모양을 바꿀 수 없으므로, 런처 액티비티(없으면 앱)의 아이콘 리소스를 직접 읽는다.
     */
    fun rawIcon(context: Context, packageName: String): Drawable? {
        val pm = context.packageManager
        return runCatching {
            val app = pm.getApplicationInfo(packageName, 0)
            val activityIcon = pm.getLaunchIntentForPackage(packageName)?.component
                ?.let { runCatching { pm.getActivityInfo(it, 0).iconResource }.getOrNull() }
                ?.takeIf { it != 0 }
            val res = activityIcon ?: app.icon
            pm.getResourcesForApplication(app).getDrawableForDensity(res, context.resources.displayMetrics.densityDpi, null)
        }.getOrNull() ?: runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
    }

    fun draw(icon: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val background = (icon as? AdaptiveIconDrawable)?.background
        if (icon is AdaptiveIconDrawable && background != null && !isPlainLight(background)) {
            // 색 배경이 디자인의 일부(카메라, 설정 등): macOS처럼 배경을 스쿼클에 꽉 채움
            c.clipPath(squircle(size.toFloat()))
            // 적응형 아이콘 레이어는 보이는 영역보다 사방 25%씩 크게 그려진다 (108dp 중 72dp가 보임)
            val inset = size / 4
            for (layer in listOfNotNull(background, icon.foreground)) {
                layer.setBounds(-inset, -inset, size + inset, size + inset)
                layer.draw(c)
            }
        } else if (icon is AdaptiveIconDrawable) {
            // 흰 단색 판 위에 로고만 있는 아이콘(Chrome, Gmail 등): 판은 빼고 그림만, 조금 크게
            val inset = (size * 0.3f).toInt()
            icon.foreground?.apply {
                setBounds(-inset, -inset, size + inset, size + inset)
                draw(c)
            }
        } else {
            // 예전 방식 아이콘은 모양이 이미 그려져 있으니 그대로
            icon.setBounds(0, 0, size, size)
            icon.draw(c)
        }
        return bmp
    }

    /**
     * 배경 레이어가 밝은 단색인지 (= 런처가 깔아주는 흰 판 같은 것).
     * 작게 그려 놓고 모든 픽셀이 밝고 거의 같은 색이면 단색 판으로 본다.
     */
    private fun isPlainLight(layer: Drawable): Boolean {
        val probe = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        layer.setBounds(0, 0, 12, 12)
        layer.draw(Canvas(probe))
        val pixels = IntArray(144).also { probe.getPixels(it, 0, 12, 0, 0, 12, 12) }
        probe.recycle()
        val first = pixels[0]
        return pixels.all { p ->
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val light = (r + g + b) / 3 >= 225
            val same = abs(r - ((first shr 16) and 0xFF)) + abs(g - ((first shr 8) and 0xFF)) + abs(b - (first and 0xFF)) < 30
            light && same
        }
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
