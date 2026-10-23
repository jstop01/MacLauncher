package com.jex.maclauncher.widget.finder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.RemoteViews
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.dock.DockWidget
import java.io.File

/**
 * 위젯 속 Finder 아이콘(🟡 최소화 아이콘, Dock 맨 왼쪽). 기본은 직접 그린 웃는 얼굴,
 * 사용자가 고른 이미지가 있으면 그걸 씀 (앱 폴더에 256px로 줄여 복사해 둠).
 */
object FinderIcon {
    private const val SIZE = 256

    private fun file(context: Context) = File(context.filesDir, "finder_icon.png")

    fun custom(context: Context): Bitmap? =
        file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** 고른 이미지를 정사각형으로 잘라 저장하고 위젯을 다시 그림. 실패하면 false */
    fun set(context: Context, uri: Uri): Boolean = runCatching {
        val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = SIZE.toFloat() / minOf(info.size.width, info.size.height)
            if (scale < 1f) d.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        file(context).outputStream().use { square.compress(Bitmap.CompressFormat.PNG, 100, it) }
        refresh(context)
    }.isSuccess

    fun clear(context: Context) {
        file(context).delete()
        refresh(context)
    }

    /** RemoteViews의 ImageView에 Finder 아이콘 넣기 */
    fun apply(context: Context, views: RemoteViews, viewId: Int) {
        val bmp = custom(context)
        if (bmp != null) views.setImageViewBitmap(viewId, bmp) else views.setImageViewResource(viewId, R.drawable.ic_finder_face)
    }

    private fun refresh(context: Context) {
        FinderWidget.refreshAll(context)
        DockWidget.updateAll(context)
    }
}
