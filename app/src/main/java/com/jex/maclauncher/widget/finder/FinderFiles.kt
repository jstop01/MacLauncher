package com.jex.maclauncher.widget.finder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.jex.maclauncher.R
import java.io.File
import java.util.Locale

data class FinderItem(val file: File, val isDir: Boolean)

/**
 * Finder 위젯이 보여주는 위치와 파일 목록.
 * 위치는 절대 경로, 또는 특별 위치 RECENT(최근 항목) / APPS(응용 프로그램) / FAVORITES(즐겨찾는 앱, 폴더는 "favorites/<id>").
 */
object FinderFiles {

    const val RECENT = "recent"
    /** 응용 프로그램: 파일 대신 설치된 앱을 보여주는 위치 */
    const val APPS = "apps"
    const val FAVORITES = "favorites"

    fun favoriteFolder(id: String) = "$FAVORITES/$id"
    fun favoriteFolderId(location: String): String? = location.removePrefix("$FAVORITES/").takeIf { it != location }
    private const val RECENT_LIMIT = 60
    private const val MAX_ITEMS = 300

    val root: File get() = Environment.getExternalStorageDirectory()

    /** 사이드바 항목: id → (이름, 위치) */
    val sidebar: List<Triple<Int, String, String>>
        get() = listOf(
            Triple(R.id.side_favorites, "즐겨찾는 앱", FAVORITES),
            Triple(R.id.side_recent, "최근 항목", RECENT),
            Triple(R.id.side_apps, "응용 프로그램", APPS),
            Triple(R.id.side_download, "다운로드", dir(Environment.DIRECTORY_DOWNLOADS)),
            Triple(R.id.side_documents, "문서", dir(Environment.DIRECTORY_DOCUMENTS)),
            Triple(R.id.side_photos, "사진", dir(Environment.DIRECTORY_DCIM)),
            Triple(R.id.side_music, "음악", dir(Environment.DIRECTORY_MUSIC)),
            Triple(R.id.side_movies, "동영상", dir(Environment.DIRECTORY_MOVIES)),
            Triple(R.id.side_phone, "내 폰", root.path),
        )

    private fun dir(name: String) = File(root, name).path

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun title(context: Context, location: String): String {
        favoriteFolderId(location)?.let { id -> return FavoritesStore.folder(context, id)?.name ?: "즐겨찾는 앱" }
        return sidebar.firstOrNull { it.third == location }?.second ?: File(location).name
    }

    /** 파일이 아니라 앱/즐겨찾기를 보여주는 위치 (파일 권한 불필요) */
    fun isAppLocation(location: String) = location == APPS || location == FAVORITES || favoriteFolderId(location) != null

    /** 지금 위치가 속한 사이드바 항목 (가장 깊게 일치하는 것) */
    fun sidebarFor(location: String): Int? =
        sidebar.filter { location == it.third || location.startsWith(it.third + "/") }
            .maxByOrNull { it.third.length }?.first

    /** 뒤로: 상위 폴더. 최근 항목이나 저장소 맨 위에선 null */
    fun parent(location: String): String? {
        if (favoriteFolderId(location) != null) return FAVORITES
        if (location == RECENT || location == APPS || location == FAVORITES || location == root.path) return null
        return File(location).parentFile?.path?.takeIf { it.startsWith(root.path) }
    }

    fun list(context: Context, location: String): List<FinderItem> =
        if (location == RECENT) recent(context) else folder(File(location))

    /** 폴더 먼저, 이름순. 숨김 파일 제외 */
    private fun folder(dir: File): List<FinderItem> =
        (dir.listFiles() ?: emptyArray())
            .filterNot { it.name.startsWith(".") }
            .map { FinderItem(it, it.isDirectory) }
            .sortedWith(compareBy<FinderItem> { !it.isDir }.thenBy { it.file.name.lowercase(Locale.ROOT) })
            .take(MAX_ITEMS)

    /** 최근 수정된 파일 (MediaStore 기준) */
    @Suppress("DEPRECATION") // DATA 컬럼: 모든 파일 접근 권한이 있으면 실제 경로를 준다
    private fun recent(context: Context): List<FinderItem> {
        val items = mutableListOf<FinderItem>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns.DATA),
            "${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL",
            null,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
        )?.use { c ->
            while (c.moveToNext() && items.size < RECENT_LIMIT) {
                val file = File(c.getString(0) ?: continue)
                if (file.path.contains("/.") || !file.isFile) continue
                items += FinderItem(file, false)
            }
        }
        return items
    }

    fun freeSpace(): String {
        val gb = StatFs(root.path).availableBytes / 1_000_000_000.0
        return String.format(Locale.US, "%.1fGB 사용 가능", gb)
    }

    fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "*/*"

    /** 썸네일이 없는 파일의 종류별 아이콘 */
    fun iconFor(item: FinderItem): Int {
        if (item.isDir) return R.drawable.ic_file_folder
        val ext = item.file.extension.lowercase(Locale.ROOT)
        val mime = mimeType(item.file)
        return when {
            ext == "pdf" -> R.drawable.ic_file_pdf
            ext == "apk" -> R.drawable.ic_file_apk
            ext in setOf("zip", "rar", "7z", "tar", "gz") -> R.drawable.ic_file_archive
            mime.startsWith("image/") -> R.drawable.ic_file_image
            mime.startsWith("video/") -> R.drawable.ic_file_video
            mime.startsWith("audio/") -> R.drawable.ic_file_audio
            else -> R.drawable.ic_file_doc
        }
    }
}
