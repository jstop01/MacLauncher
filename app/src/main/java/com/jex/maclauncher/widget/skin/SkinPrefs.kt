package com.jex.maclauncher.widget.skin

import android.content.Context
import com.jex.maclauncher.widget.WidgetRefresher

/**
 * 사용자가 고른 감성 팩을 저장한다. 테마(라이트/다크)와 별개의 선택이다.
 *
 * 기본값은 [AUTO] = 기존 위젯 테마(라이트/다크 토글)를 그대로 따라간다.
 * 그래서 아무것도 안 고른 사용자는 지금과 똑같이 동작하고(하위호환),
 * 팩을 고르면 그 팩으로 덮어쓴다.
 */
object SkinPrefs {
    private const val PREFS = "skin"
    private const val KEY_PACK = "pack_id"

    /** "테마 따름" 센티넬: 저장된 팩 id가 이 값이면 라이트/다크 토글을 따른다. */
    const val AUTO = "auto"

    fun selectedId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PACK, AUTO) ?: AUTO

    /** 저장만 (백업 복원용, 다시 그리기는 호출한 쪽에서) */
    fun selectQuietly(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PACK, id).apply()
    }

    fun select(context: Context, id: String) {
        selectQuietly(context, id)
        WidgetRefresher.all(context)
    }
}
