package com.jex.maclauncher.widget.finder

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.Size
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.jex.maclauncher.widget.MacIcon
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.apps.AppCatalog

/** Finder 격자 한 칸 */
private sealed interface Entry {
    data class FileEntry(val item: FinderItem) : Entry
    data class AppEntry(val packageName: String, val label: String) : Entry
    data class FolderEntry(val folder: FavoriteFolder) : Entry
    /** 즐겨찾기 편집 (folderId가 있으면 그 폴더의 앱 고르기) */
    data class AddEntry(val folderId: String?) : Entry
}

/** Finder 위젯 격자의 항목들을 만들어 주는 서비스 */
class FinderItemsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))

    private class Factory(private val context: Context, private val widgetId: Int) : RemoteViewsFactory {
        private var entries: List<Entry> = emptyList()
        private val thumbs = HashMap<String, Bitmap?>()

        override fun onCreate() = Unit

        // 바인더 스레드에서 불리므로 파일 읽기 OK
        override fun onDataSetChanged() {
            val location = FinderWidget.location(context, widgetId)
            val folderId = FinderFiles.favoriteFolderId(location)
            entries = when {
                location == FinderFiles.APPS ->
                    AppCatalog.list(context, AppCatalog.Section.ALL).map { Entry.AppEntry(it.packageName, it.label) }
                // 이름 아래에 마지막으로 쓴 시각
                location == FinderFiles.RECENT_APPS -> RecentApps.list(context).mapNotNull { used ->
                    (apps(listOf(used.packageName)).firstOrNull() as? Entry.AppEntry)
                        ?.let { it.copy(label = "${it.label}\n${RecentApps.ago(used.lastUsed)}") }
                }
                // 즐겨찾기 맨 위: 폴더 → 앱 → ＋
                location == FinderFiles.FAVORITES -> {
                    val fav = FavoritesStore.load(context)
                    fav.folders.map { Entry.FolderEntry(it) } + apps(fav.apps) + Entry.AddEntry(null)
                }
                folderId != null ->
                    apps(FavoritesStore.folder(context, folderId)?.apps.orEmpty()) + Entry.AddEntry(folderId)
                else -> FinderFiles.list(context, location).map { Entry.FileEntry(it) }
            }
            thumbs.clear()
        }

        /** 지워진 앱은 빼고 라벨 붙이기 */
        private fun apps(packages: List<String>): List<Entry> {
            val pm = context.packageManager
            return packages.mapNotNull { pkg ->
                runCatching { Entry.AppEntry(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull()
            }
        }

        override fun onDestroy() = thumbs.clear()

        override fun getCount() = entries.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_finder_item)
            when (val e = entries.getOrNull(position)) {
                is Entry.FileEntry -> {
                    views.setTextViewText(R.id.item_name, e.item.file.name)
                    val thumb = thumbnail(e.item)
                    if (thumb != null) views.setImageViewBitmap(R.id.item_icon, thumb)
                    else views.setImageViewResource(R.id.item_icon, FinderFiles.iconFor(e.item))
                    views.setOnClickFillInIntent(
                        R.id.item_root,
                        Intent().putExtra(FinderOpenActivity.EXTRA_PATH, e.item.file.path)
                            .putExtra(FinderOpenActivity.EXTRA_IS_DIR, e.item.isDir),
                    )
                }
                is Entry.AppEntry -> {
                    views.setTextViewText(R.id.item_name, e.label)
                    MacIcon.of(context, e.packageName, 112)?.let { views.setImageViewBitmap(R.id.item_icon, it) }
                    views.setOnClickFillInIntent(R.id.item_root, Intent().putExtra(FinderOpenActivity.EXTRA_PACKAGE, e.packageName))
                }
                is Entry.FolderEntry -> {
                    views.setTextViewText(R.id.item_name, e.folder.name)
                    views.setImageViewResource(R.id.item_icon, e.folder.color.icon)
                    // 폴더 위치로 이동 (파일 폴더와 같은 경로)
                    views.setOnClickFillInIntent(
                        R.id.item_root,
                        Intent().putExtra(FinderOpenActivity.EXTRA_PATH, FinderFiles.favoriteFolder(e.folder.id))
                            .putExtra(FinderOpenActivity.EXTRA_IS_DIR, true),
                    )
                }
                is Entry.AddEntry -> {
                    views.setTextViewText(R.id.item_name, if (e.folderId == null) "편집" else "앱 넣기")
                    views.setImageViewResource(R.id.item_icon, R.drawable.ic_add_tile)
                    views.setOnClickFillInIntent(
                        R.id.item_root,
                        Intent().putExtra(FinderOpenActivity.EXTRA_EDIT_FAVORITES, e.folderId.orEmpty()),
                    )
                }
                null -> Unit
            }
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

        // 기본 "Loading..." 글자 대신 빈 칸
        override fun getLoadingView(): RemoteViews = RemoteViews(context.packageName, R.layout.widget_finder_item)
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }

    private companion object {
        val THUMB = Size(144, 144)
    }
}
