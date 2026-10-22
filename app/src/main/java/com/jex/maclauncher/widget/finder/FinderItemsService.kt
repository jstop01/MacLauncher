package com.jex.maclauncher.widget.finder

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.Size
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.apps.AppCatalog
import com.jex.maclauncher.widget.apps.InstalledApp

/** Finder 위젯 격자의 항목들을 만들어 주는 서비스 */
class FinderItemsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))

    private class Factory(private val context: Context, private val widgetId: Int) : RemoteViewsFactory {
        private var items: List<FinderItem> = emptyList()
        private var apps: List<InstalledApp> = emptyList()   // '응용 프로그램' 위치일 때
        private val thumbs = HashMap<String, Bitmap?>()

        override fun onCreate() = Unit

        // 바인더 스레드에서 불리므로 파일 읽기 OK
        override fun onDataSetChanged() {
            val location = FinderWidget.location(context, widgetId)
            if (location == FinderFiles.APPS) {
                apps = AppCatalog.list(context, AppCatalog.Section.ALL)
                items = emptyList()
            } else {
                apps = emptyList()
                items = FinderFiles.list(context, location)
            }
            thumbs.clear()
        }

        override fun onDestroy() = thumbs.clear()

        override fun getCount() = if (apps.isNotEmpty()) apps.size else items.size

        override fun getViewAt(position: Int): RemoteViews {
            if (apps.isNotEmpty()) return appView(position)
            val item = items.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_finder_item)
            return RemoteViews(context.packageName, R.layout.widget_finder_item).apply {
                setTextViewText(R.id.item_name, item.file.name)
                val thumb = thumbnail(item)
                if (thumb != null) setImageViewBitmap(R.id.item_icon, thumb)
                else setImageViewResource(R.id.item_icon, FinderFiles.iconFor(item))
                setOnClickFillInIntent(
                    R.id.item_root,
                    Intent().putExtra(FinderOpenActivity.EXTRA_PATH, item.file.path)
                        .putExtra(FinderOpenActivity.EXTRA_IS_DIR, item.isDir),
                )
            }
        }

        /** 앱 아이콘 + 이름, 탭하면 실행 */
        private fun appView(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_finder_item)
            val app = apps.getOrNull(position) ?: return views
            views.setTextViewText(R.id.item_name, app.label)
            runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(112, 112) }
                .onSuccess { views.setImageViewBitmap(R.id.item_icon, it) }
            views.setOnClickFillInIntent(R.id.item_root, Intent().putExtra(FinderOpenActivity.EXTRA_PACKAGE, app.packageName))
            return views
        }

        /** 사진/동영상은 썸네일 (실패하면 종류별 아이콘) */
        private fun thumbnail(item: FinderItem): Bitmap? {
            if (item.isDir) return null
            val mime = FinderFiles.mimeType(item.file)
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) return null
            return thumbs.getOrPut(item.file.path) {
                runCatching {
                    if (mime.startsWith("image/")) ThumbnailUtils.createImageThumbnail(item.file, THUMB, null)
                    else ThumbnailUtils.createVideoThumbnail(item.file, THUMB, null)
                }.getOrNull()
            }
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }

    private companion object {
        val THUMB = Size(144, 144)
    }
}
