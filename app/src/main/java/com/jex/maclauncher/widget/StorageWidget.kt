package com.jex.maclauncher.widget

import android.app.PendingIntent
import android.app.usage.StorageStatsManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.WidgetTheme
import com.jex.maclauncher.widget.finder.FinderFiles
import java.util.Locale
import kotlin.concurrent.thread

/** macOS 스타일 저장 공간 위젯: 전체 사용량 + 항목별 색 막대 + 범례 */
class StorageWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refreshAll(context, goAsync())
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        refreshAll(context, goAsync())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refreshAll(context, goAsync())
    }

    private data class Usage(
        val total: Long,
        val used: Long,
        val photos: Long = 0,
        val videos: Long = 0,
        val music: Long = 0,
        val docs: Long = 0,
        val detailed: Boolean = false,
    ) {
        /** 미디어/파일로 설명 안 되는 나머지 = 앱 · 시스템 */
        val apps: Long get() = (used - photos - videos - music - docs).coerceAtLeast(0)
    }

    companion object {
        private const val ACTION_REFRESH = "com.jex.maclauncher.storage.REFRESH"

        private val COLORS = intArrayOf(
            0xFF8E8E93.toInt(), // 앱 · 시스템
            0xFFFF9F0A.toInt(), // 사진
            0xFFFF453A.toInt(), // 동영상
            0xFFBF5AF2.toInt(), // 음악
            0xFF0A84FF.toInt(), // 문서 · 기타
        )

        /** 미디어 목록을 훑는 데 시간이 걸려서 백그라운드에서 계산 */
        fun refreshAll(context: Context, pending: BroadcastReceiver.PendingResult? = null) {
            val app = context.applicationContext
            thread(name = "storage-widget") {
                try {
                    val manager = AppWidgetManager.getInstance(app)
                    val ids = manager.getAppWidgetIds(ComponentName(app, StorageWidget::class.java))
                    if (ids.isNotEmpty()) {
                        val usage = measure(app)
                        ids.forEach { id -> manager.updateAppWidget(id, views(app, usage, WidgetSize.of(manager, id))) }
                    }
                } finally {
                    pending?.finish()
                }
            }
        }

        private fun measure(context: Context): Usage {
            val stats = context.getSystemService(StorageStatsManager::class.java)
            val total = stats.getTotalBytes(StorageManager.UUID_DEFAULT)
            val used = total - stats.getFreeBytes(StorageManager.UUID_DEFAULT)
            if (!FinderFiles.hasAccess(context)) return Usage(total, used)

            var photos = 0L
            var videos = 0L
            var music = 0L
            var docs = 0L
            context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.SIZE),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val size = c.getLong(1)
                    when (c.getInt(0)) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> photos += size
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> videos += size
                        MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> music += size
                        else -> docs += size
                    }
                }
            }
            return Usage(total, used, photos, videos, music, docs, detailed = true)
        }

        /** 위젯이 낮으면 범례/안내를 숨기고 막대까지만 */
        private fun views(context: Context, u: Usage, size: WidgetSize): RemoteViews =
            RemoteViews(context.packageName, WidgetTheme.layout(context, R.layout.widget_storage)).apply {
                setTextViewText(R.id.st_summary, "${gb(u.total, 0)} 중 ${gb(u.used)} 사용")
                val parts = if (u.detailed) longArrayOf(u.apps, u.photos, u.videos, u.music, u.docs) else longArrayOf(u.used)
                setImageViewBitmap(R.id.st_bar, bar(parts, u.total, WidgetTheme.palette(context)))

                val compact = size.shorterThan(150)
                setViewVisibility(R.id.st_legend, if (u.detailed && !compact) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.st_hint, if (!u.detailed && !compact) View.VISIBLE else View.GONE)
                setTextViewText(R.id.st_apps, gb(u.apps))
                setTextViewText(R.id.st_photos, gb(u.photos))
                setTextViewText(R.id.st_videos, gb(u.videos))
                setTextViewText(R.id.st_music, gb(u.music))
                setTextViewText(R.id.st_docs, gb(u.docs))

                // 권한 있으면 탭 = 새로고침, 없으면 권한 설정 화면
                val click = if (u.detailed || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    PendingIntent.getBroadcast(
                        context, 0, Intent(context, StorageWidget::class.java).setAction(ACTION_REFRESH),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                } else {
                    PendingIntent.getActivity(
                        context, 1,
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                }
                setOnClickPendingIntent(R.id.st_root, click)
            }

        /** 항목별 색 구간 + 남은 공간(어두운 트랙). 양 끝만 둥글게 */
        private fun bar(parts: LongArray, total: Long, theme: WidgetTheme.Palette, width: Int = 600, height: Int = 28): Bitmap {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = height / 2f
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, Path.Direction.CW) }
            c.clipPath(clip)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = theme.track
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            var x = 0f
            parts.forEachIndexed { i, bytes ->
                val w = width * bytes.toFloat() / total.coerceAtLeast(1)
                p.color = if (parts.size == 1) COLORS[0] else COLORS[i]
                c.drawRect(x, 0f, x + w, height.toFloat(), p)
                x += w
                // 구간 사이 얇은 틈
                p.color = theme.panel
                if (w > 0) c.drawRect(x - 1.5f, 0f, x, height.toFloat(), p)
            }
            return bmp
        }

        /** 기기 표기와 같은 10진 단위. 1GB 미만은 MB로 */
        private fun gb(bytes: Long, decimals: Int = 1): String =
            if (bytes < 1_000_000_000) String.format(Locale.US, "%dMB", bytes / 1_000_000)
            else String.format(Locale.US, "%.${decimals}fGB", bytes / 1_000_000_000.0)
    }
}
