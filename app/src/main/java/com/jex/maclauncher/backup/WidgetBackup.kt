package com.jex.maclauncher.backup

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import com.jex.maclauncher.widget.WidgetRefresher
import com.jex.maclauncher.widget.WidgetTheme
import com.jex.maclauncher.widget.dock.DockWidget
import com.jex.maclauncher.widget.finder.FinderWidget
import com.jex.maclauncher.widget.sticky.StickyColor
import com.jex.maclauncher.widget.sticky.StickyWidget
import org.json.JSONArray
import org.json.JSONObject

/**
 * 위젯 설정 백업/복원 (JSON 파일 하나).
 *
 * Dock·메모는 위젯 id별로 저장되는데, 앱을 다시 깔면 id가 바뀐다.
 * 그래서 id 대신 '지금 놓인 위젯 순서'로 내보내고, 복원할 때 지금 놓인 위젯에 순서대로 채운다.
 * 위젯이 모자라면 남은 건 대기열에 두고, 새로 Dock/메모를 놓을 때 꺼내 쓴다 ([takePendingDock], [takePendingSticky]).
 */
object WidgetBackup {

    private const val VERSION = 1
    private const val PREFS = "widget_backup"
    private const val KEY_PENDING_DOCK = "pending_dock"
    private const val KEY_PENDING_STICKY = "pending_sticky"

    data class Result(val dock: Int, val sticky: Int, val pendingDock: Int, val pendingSticky: Int)

    fun export(context: Context, uri: Uri) {
        val json = JSONObject()
            .put("version", VERSION)
            .put("app", "mac-widgets")
            .put("theme_light", WidgetTheme.isLight(context))
            .put("finder_favorites", raw(context, "finder_favorites", "favorites"))
            .put("finder_mini_position", FinderWidget.miniPosition(context))
            .put("reminders", raw(context, "reminders", "items"))
            .put("dock", JSONArray().apply {
                ids(context, DockWidget::class.java).forEach { put(DockWidget.apps(context, it).joinToString(",")) }
            })
            .put("sticky", JSONArray().apply {
                ids(context, StickyWidget::class.java).forEach {
                    put(JSONObject().put("text", StickyWidget.text(context, it)).put("color", StickyWidget.color(context, it).name))
                }
            })
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toString(2).toByteArray()) }
    }

    fun import(context: Context, uri: Uri): Result {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val json = JSONObject(text)
        require(json.optString("app") == "mac-widgets") { "Mac 위젯 백업 파일이 아니에요" }

        // 위젯 id와 상관없는 것들은 그대로
        WidgetTheme.setLightQuietly(context, json.optBoolean("theme_light"))
        json.optString("finder_favorites").takeIf { it.isNotEmpty() }?.let { setRaw(context, "finder_favorites", "favorites", it) }
        json.optString("reminders").takeIf { it.isNotEmpty() }?.let { setRaw(context, "reminders", "items", it) }
        if (json.has("finder_mini_position")) FinderWidget.setMiniPositionQuietly(context, json.getInt("finder_mini_position"))

        // Dock·메모: 지금 놓인 위젯에 순서대로, 남으면 대기열
        val docks = json.optJSONArray("dock").strings()
        val dockIds = ids(context, DockWidget::class.java)
        docks.zip(dockIds.toList()).forEach { (apps, id) -> DockWidget.saveQuietly(context, id, apps.split(",").filter { it.isNotBlank() }) }
        val restDock = docks.drop(dockIds.size)

        val stickies = json.optJSONArray("sticky")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }.orEmpty()
        val stickyIds = ids(context, StickyWidget::class.java)
        stickies.zip(stickyIds.toList()).forEach { (o, id) -> StickyWidget.saveQuietly(context, id, o.optString("text"), color(o)) }
        val restSticky = stickies.drop(stickyIds.size)

        prefs(context).edit()
            .putString(KEY_PENDING_DOCK, JSONArray(restDock).toString())
            .putString(KEY_PENDING_STICKY, JSONArray(restSticky).toString())
            .apply()

        WidgetRefresher.all(context)
        return Result(minOf(docks.size, dockIds.size), minOf(stickies.size, stickyIds.size), restDock.size, restSticky.size)
    }

    /** 새로 놓인 Dock에 쓸 복원 대기 앱 목록 (없으면 null) */
    fun takePendingDock(context: Context): List<String>? {
        val queue = prefs(context).getString(KEY_PENDING_DOCK, null)?.let { JSONArray(it).strings() }.orEmpty()
        if (queue.isEmpty()) return null
        prefs(context).edit().putString(KEY_PENDING_DOCK, JSONArray(queue.drop(1)).toString()).apply()
        return queue.first().split(",").filter { it.isNotBlank() }
    }

    /** 새로 놓인 메모에 쓸 복원 대기 내용 (없으면 null) */
    fun takePendingSticky(context: Context): Pair<String, StickyColor>? {
        val raw = prefs(context).getString(KEY_PENDING_STICKY, null) ?: return null
        val queue = JSONArray(raw)
        if (queue.length() == 0) return null
        val first = queue.getJSONObject(0)
        queue.remove(0)
        prefs(context).edit().putString(KEY_PENDING_STICKY, queue.toString()).apply()
        return first.optString("text") to color(first)
    }

    private fun color(o: JSONObject) =
        runCatching { StickyColor.valueOf(o.optString("color")) }.getOrDefault(StickyColor.YELLOW)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun raw(context: Context, file: String, key: String): String =
        context.getSharedPreferences(file, Context.MODE_PRIVATE).getString(key, "").orEmpty()

    private fun setRaw(context: Context, file: String, key: String, value: String) =
        context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().putString(key, value).apply()

    /** 놓인 순서(id 오름차순) */
    private fun ids(context: Context, provider: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider)).sortedArray()

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map(::getString)
}
