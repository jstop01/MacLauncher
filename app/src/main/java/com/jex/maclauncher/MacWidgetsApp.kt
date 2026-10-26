package com.jex.maclauncher

import android.app.Application
import android.content.res.Configuration
import com.jex.maclauncher.widget.WidgetRefresher
import com.jex.maclauncher.widget.skin.Skin
import com.jex.maclauncher.widget.skin.SkinPrefs

/**
 * 감성 '시스템 따라가기'일 때 폰 다크 모드가 바뀌면 위젯을 다시 그린다.
 * 앱 프로세스가 살아 있을 때(시스템 모니터 서비스 등) 바로 반영되고, 아니면 다음 위젯 갱신 때 맞춰진다.
 */
class MacWidgetsApp : Application() {
    private var night = false

    override fun onCreate() {
        super.onCreate()
        night = Skin.systemNight(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val now = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (now == night) return
        night = now
        if (SkinPrefs.selectedId(this) == SkinPrefs.SYSTEM) WidgetRefresher.all(this)
    }
}
