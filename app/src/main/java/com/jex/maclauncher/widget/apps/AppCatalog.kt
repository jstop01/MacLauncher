package com.jex.maclauncher.widget.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.jex.maclauncher.R

data class InstalledApp(val packageName: String, val label: String, val installedAt: Long, val category: Int, val isGame: Boolean)

/** 앱 Finder의 분류(사이드바)와 앱 목록 */
object AppCatalog {

    enum class Section(val viewId: Int, val title: String) {
        ALL(R.id.apps_side_all, "응용 프로그램"),
        RECENT(R.id.apps_side_recent, "최근 설치"),
        GAMES(R.id.apps_side_games, "게임"),
        SOCIAL(R.id.apps_side_social, "소셜"),
        MEDIA(R.id.apps_side_media, "사진 · 동영상"),
        MUSIC(R.id.apps_side_music, "음악"),
        PRODUCTIVITY(R.id.apps_side_productivity, "생산성"),
    }

    private const val RECENT_LIMIT = 30

    fun list(context: Context, section: Section): List<InstalledApp> {
        val all = installed(context)
        return when (section) {
            Section.ALL -> all.sortedBy { it.label.lowercase() }
            Section.RECENT -> all.sortedByDescending { it.installedAt }.take(RECENT_LIMIT)
            // 앱이 스스로 밝힌 카테고리 기준 (안 밝힌 앱은 '전체'에만)
            Section.GAMES -> all.filter { it.isGame || it.category == ApplicationInfo.CATEGORY_GAME }.sortedBy { it.label.lowercase() }
            Section.SOCIAL -> all.filter { it.category == ApplicationInfo.CATEGORY_SOCIAL }.sortedBy { it.label.lowercase() }
            Section.MEDIA -> all.filter { it.category == ApplicationInfo.CATEGORY_IMAGE || it.category == ApplicationInfo.CATEGORY_VIDEO }
                .sortedBy { it.label.lowercase() }
            Section.MUSIC -> all.filter { it.category == ApplicationInfo.CATEGORY_AUDIO }.sortedBy { it.label.lowercase() }
            Section.PRODUCTIVITY -> all.filter { it.category == ApplicationInfo.CATEGORY_PRODUCTIVITY }.sortedBy { it.label.lowercase() }
        }
    }

    /** 런처에 보이는 앱 (이 앱 제외) */
    @Suppress("DEPRECATION") // FLAG_IS_GAME: 오래된 게임은 category 대신 이 플래그만 있음
    private fun installed(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    installedAt = runCatching { pm.getPackageInfo(info.packageName, 0).firstInstallTime }.getOrDefault(0L),
                    category = info.category,
                    isGame = info.flags and ApplicationInfo.FLAG_IS_GAME != 0,
                )
            }
    }
}
