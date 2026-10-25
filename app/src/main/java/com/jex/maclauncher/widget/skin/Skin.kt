package com.jex.maclauncher.widget.skin

import android.content.Context
import android.widget.RemoteViews
import com.jex.maclauncher.widget.WidgetSize
import com.jex.maclauncher.widget.WidgetTheme
import kotlin.math.roundToInt

/**
 * 현재 선택된 감성 팩을 돌려주고, 위젯에 패널 배경을 입힌다.
 *
 * 마이그레이션 중: 지금은 선택 상태를 [WidgetTheme]의 라이트/다크 플래그에서 가져온다
 * (= 기존 테마 토글·백업이 그대로 동작). 팩이 늘어나면 자체 prefs로 옮긴다.
 */
object Skin {

    val MAC_DARK = SkinPack(
        id = "mac_dark",
        name = "맥 다크",
        tier = Tier.FREE,
        isLight = false,
        palette = WidgetTheme.DARK,
        accent = 0xFF0A84FF.toInt(),
        panel = PanelStyle.Solid(color = 0xE61E1E20.toInt(), stroke = 0x33FFFFFF),
        icon = IconStyle(),
        cornerRadiusDp = 20f,
    )

    val MAC_LIGHT = SkinPack(
        id = "mac_light",
        name = "맥 라이트",
        tier = Tier.FREE,
        isLight = true,
        palette = WidgetTheme.LIGHT,
        accent = 0xFF0A84FF.toInt(),
        panel = PanelStyle.Solid(color = 0xF2F6F6F8.toInt(), stroke = 0x1A000000),
        icon = IconStyle(),
        cornerRadiusDp = 20f,
    )

    /** 내장 팩 (무료). 추후 프리미엄 팩은 IAP 해금 후 여기에 합쳐 노출. */
    val BUILTIN = listOf(MAC_DARK, MAC_LIGHT)

    fun current(context: Context): SkinPack = if (WidgetTheme.isLight(context)) MAC_LIGHT else MAC_DARK

    /** 레이아웃 맨 밑 skin_bg(ImageView)에 팩 배경 비트맵을 깐다. */
    fun applyPanel(context: Context, views: RemoteViews, bgId: Int, pack: SkinPack, size: WidgetSize) {
        val d = context.resources.displayMetrics.density
        val wPx = ((if (size.width > 0) size.width else 320) * d).roundToInt()
        val hPx = ((if (size.height > 0) size.height else 200) * d).roundToInt()
        views.setImageViewBitmap(
            bgId,
            SkinGraphics.panel(pack, wPx, hPx, cornerPx = pack.cornerRadiusDp * d, strokePx = 1f * d),
        )
    }
}
