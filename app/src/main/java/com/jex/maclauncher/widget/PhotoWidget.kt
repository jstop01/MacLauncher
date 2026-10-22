package com.jex.maclauncher.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.MainActivity
import com.jex.maclauncher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** macOS 사진 위젯: 갤러리 사진 한 장을 꽉 차게, 1시간마다 다른 사진. 탭하면 갤러리에서 열기 */
class PhotoWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshAll(context, goAsync())

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        refreshAll(context, goAsync(), keepPhoto = true)

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(KEY_PHOTO + it); remove(KEY_CHANGED + it) } }.apply()
    }

    private data class Photo(val uri: Uri, val taken: Long)

    companion object {
        private const val KEY_PHOTO = "photo_"
        private const val KEY_CHANGED = "changed_"
        private const val CHANGE_EVERY = 60 * 60 * 1000L
        private const val CANDIDATES = 400

        private fun prefs(context: Context) = context.getSharedPreferences("photo_widget", Context.MODE_PRIVATE)

        val permission: String
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE

        private fun hasAccess(context: Context) =
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

        /** 사진 읽고 줄이는 데 시간이 걸려서 백그라운드에서. keepPhoto면 같은 사진을 크기만 다시 맞춤 */
        fun refreshAll(context: Context, pending: BroadcastReceiver.PendingResult? = null, keepPhoto: Boolean = false) {
            val app = context.applicationContext
            thread(name = "photo-widget") {
                try {
                    val manager = AppWidgetManager.getInstance(app)
                    manager.getAppWidgetIds(ComponentName(app, PhotoWidget::class.java)).forEach { id ->
                        runCatching { manager.updateAppWidget(id, views(app, manager, id, keepPhoto)) }
                    }
                } finally {
                    pending?.finish()
                }
            }
        }

        private fun views(context: Context, manager: AppWidgetManager, id: Int, keepPhoto: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_photo)
            if (!hasAccess(context)) {
                return hint(context, views, "사진을 보려면 탭해서 권한 허용",
                    PendingIntent.getActivity(context, 4, MainActivity.requestPermissionIntent(context, permission),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            val photo = choose(context, id, keepPhoto) ?: return hint(context, views, "사진이 없어요", null)

            // 위젯 크기에 맞춰 줄인 썸네일 (가로세로 중 긴 쪽 기준, 최대 720px)
            val size = WidgetSize.of(manager, id)
            val density = context.resources.displayMetrics.density
            val px = ((maxOf(size.width, size.height, 160)) * density).roundToInt().coerceAtMost(720)
            val bitmap: Bitmap = runCatching { context.contentResolver.loadThumbnail(photo.uri, Size(px, px), null) }.getOrNull()
                ?: return hint(context, views, "사진을 열 수 없어요", null)

            views.setImageViewBitmap(R.id.photo_image, bitmap)
            views.setTextViewText(R.id.photo_date, SimpleDateFormat("yyyy년 M월 d일", Locale.KOREAN).format(Date(photo.taken)))
            views.setViewVisibility(R.id.photo_hint, View.GONE)
            views.setOnClickPendingIntent(
                R.id.photo_root,
                PendingIntent.getActivity(
                    context, id,
                    Intent(Intent.ACTION_VIEW).setDataAndType(photo.uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return views
        }

        private fun hint(context: Context, views: RemoteViews, text: String, click: PendingIntent?): RemoteViews {
            views.setViewVisibility(R.id.photo_hint, View.VISIBLE)
            views.setTextViewText(R.id.photo_hint, text)
            views.setViewVisibility(R.id.photo_scrim, View.GONE)
            views.setTextViewText(R.id.photo_date, "")
            views.setInt(R.id.photo_root, "setBackgroundResource", WidgetTheme.drawable(context, R.drawable.widget_bg))
            views.setTextColor(R.id.photo_hint, WidgetTheme.palette(context).subText)
            click?.let { views.setOnClickPendingIntent(R.id.photo_root, it) }
            return views
        }

        /** 1시간이 지났으면 새 사진, 아니면 보던 사진 (지워졌으면 새로) */
        private fun choose(context: Context, id: Int, keepPhoto: Boolean): Photo? {
            val p = prefs(context)
            val saved = p.getString(KEY_PHOTO + id, null)?.let { s ->
                val (uri, taken) = s.split("|").let { it[0] to it.getOrNull(1)?.toLongOrNull() }
                Photo(Uri.parse(uri), taken ?: 0)
            }
            val fresh = System.currentTimeMillis() - p.getLong(KEY_CHANGED + id, 0) < CHANGE_EVERY
            if (saved != null && (keepPhoto || fresh) && exists(context, saved.uri)) return saved

            val next = candidates(context).filter { it.uri != saved?.uri }.randomOrNull() ?: saved ?: return null
            p.edit().putString(KEY_PHOTO + id, "${next.uri}|${next.taken}").putLong(KEY_CHANGED + id, System.currentTimeMillis()).apply()
            return next
        }

        private fun exists(context: Context, uri: Uri) =
            runCatching { context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use { it.count > 0 } }
                .getOrNull() == true

        /** 카메라로 찍은 최근 사진 (없으면 아무 사진이나) */
        private fun candidates(context: Context): List<Photo> {
            fun query(selection: String?): List<Photo> {
                val photos = mutableListOf<Photo>()
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED),
                    selection, null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC",
                )?.use { c ->
                    while (c.moveToNext() && photos.size < CANDIDATES) {
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                        val taken = c.getLong(1).takeIf { it > 0 } ?: (c.getLong(2) * 1000)
                        photos += Photo(uri, taken)
                    }
                }
                return photos
            }
            return query("${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = 'Camera'").ifEmpty { query(null) }
        }
    }
}
