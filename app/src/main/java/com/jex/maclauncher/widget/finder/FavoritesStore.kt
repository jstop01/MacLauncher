package com.jex.maclauncher.widget.finder

import android.content.Context
import com.jex.maclauncher.R
import org.json.JSONArray
import org.json.JSONObject

/** 즐겨찾기 폴더 색 (macOS 태그 색) */
enum class FolderColor(val label: String, val icon: Int, val argb: Long) {
    BLUE("파랑", R.drawable.ic_folder_blue, 0xFF3D95E6),
    RED("빨강", R.drawable.ic_folder_red, 0xFFD93A30),
    ORANGE("주황", R.drawable.ic_folder_orange, 0xFFE07A1F),
    YELLOW("노랑", R.drawable.ic_folder_yellow, 0xFFD9A800),
    GREEN("초록", R.drawable.ic_folder_green, 0xFF2E9E4A),
    PURPLE("보라", R.drawable.ic_folder_purple, 0xFF8E44C9),
    GRAY("회색", R.drawable.ic_folder_gray, 0xFF7C7C82),
}

data class FavoriteFolder(
    val id: String,
    val name: String,
    val color: FolderColor = FolderColor.BLUE,
    val apps: List<String> = emptyList(),
)

data class Favorites(val apps: List<String> = emptyList(), val folders: List<FavoriteFolder> = emptyList())

/**
 * Finder '즐겨찾는 앱': 맨 위에 둘 앱 + 앱을 나눠 담는 폴더(이름/색).
 * 모든 Finder 위젯이 같은 즐겨찾기를 쓴다.
 */
object FavoritesStore {
    private const val PREFS = "finder_favorites"
    private const val KEY = "favorites"
    const val MAX_APPS = 30

    fun load(context: Context): Favorites {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return Favorites()
        return runCatching {
            val o = JSONObject(raw)
            Favorites(
                apps = o.optJSONArray("apps").strings(),
                folders = o.optJSONArray("folders")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val f = arr.getJSONObject(i)
                        FavoriteFolder(
                            id = f.getString("id"),
                            name = f.getString("name"),
                            color = runCatching { FolderColor.valueOf(f.optString("color")) }.getOrDefault(FolderColor.BLUE),
                            apps = f.optJSONArray("apps").strings(),
                        )
                    }
                }.orEmpty(),
            )
        }.getOrDefault(Favorites())
    }

    fun save(context: Context, favorites: Favorites) {
        val o = JSONObject()
            .put("apps", JSONArray(favorites.apps))
            .put("folders", JSONArray().apply {
                favorites.folders.forEach { f ->
                    put(JSONObject().put("id", f.id).put("name", f.name).put("color", f.color.name).put("apps", JSONArray(f.apps)))
                }
            })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
        FinderWidget.refreshAll(context)
    }

    fun folder(context: Context, id: String) = load(context).folders.firstOrNull { it.id == id }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map(::getString)
}
