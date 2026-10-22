package com.jex.maclauncher.widget.reminders

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Reminder(val id: Long, val title: String, val done: Boolean = false)

/** 미리 알림 목록 (폰에만 저장, 모든 미리 알림 위젯이 같은 목록을 씀) */
object ReminderStore {
    private const val PREFS = "reminders"
    private const val KEY = "items"

    /** 안 한 것 먼저(추가한 순서), 완료한 것은 아래로 */
    fun all(context: Context): List<Reminder> = load(context).sortedBy { it.done }

    fun remaining(context: Context) = load(context).count { !it.done }

    fun completed(context: Context) = load(context).count { it.done }

    fun get(context: Context, id: Long) = load(context).firstOrNull { it.id == id }

    fun add(context: Context, title: String) =
        save(context, load(context) + Reminder(System.currentTimeMillis(), title))

    fun rename(context: Context, id: Long, title: String) =
        save(context, load(context).map { if (it.id == id) it.copy(title = title) else it })

    fun toggle(context: Context, id: Long) =
        save(context, load(context).map { if (it.id == id) it.copy(done = !it.done) else it })

    fun delete(context: Context, id: Long) = save(context, load(context).filterNot { it.id == id })

    fun clearCompleted(context: Context) = save(context, load(context).filterNot { it.done })

    private fun load(context: Context): List<Reminder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Reminder(o.getLong("id"), o.getString("title"), o.optBoolean("done"))
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, items: List<Reminder>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("done", it.done)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
