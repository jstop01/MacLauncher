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

    // ── 여기부터는 "새 감성 = 데이터 하나"의 증명. 레이아웃·코드 추가 없이 아래 객체만으로 다른 감성이 뜬다. ──

    /** 선셋: 주황→마젠타 그라데이션. 채도 높아 흰 글씨가 잘 읽힌다. */
    val SUNSET = SkinPack(
        id = "sunset",
        name = "선셋",
        tier = Tier.FREE,
        isLight = false,
        palette = WidgetTheme.DARK,
        accent = 0xFFFFD166.toInt(),
        panel = PanelStyle.Gradient(from = 0xFFFF512F.toInt(), to = 0xFFDD2476.toInt(), angleDeg = 90f, stroke = 0x33FFFFFF),
        icon = IconStyle(),
        cornerRadiusDp = 24f,
    )

    /** 오션 글래스: 반투명 짙은 청록 유리. (프리미엄) */
    val OCEAN_GLASS = SkinPack(
        id = "ocean_glass",
        name = "오션 글래스",
        tier = Tier.PREMIUM,
        isLight = false,
        palette = WidgetTheme.DARK,
        accent = 0xFF4AD7D1.toInt(),
        panel = PanelStyle.Glass(tint = 0xFF0A4A6B.toInt(), alpha = 0xD9, stroke = 0x40FFFFFF),
        icon = IconStyle(),
        cornerRadiusDp = 22f,
    )

    /** 버블껌: 파스텔 핑크→민트. 밝은 배경이라 어두운 글씨(라이트 팔레트). (프리미엄) */
    val BUBBLEGUM = SkinPack(
        id = "bubblegum",
        name = "버블껌",
        tier = Tier.PREMIUM,
        isLight = true,
        palette = WidgetTheme.LIGHT,
        accent = 0xFFFF4D8D.toInt(),
        panel = PanelStyle.Gradient(from = 0xFFFFDEE9.toInt(), to = 0xFFB5FFFC.toInt(), angleDeg = 90f, stroke = 0x1A000000),
        icon = IconStyle(),
        cornerRadiusDp = 24f,
    )

    /** 내장 팩 (무료). 추후 프리미엄 팩은 IAP 해금 후 여기에 합쳐 노출. */
    val BUILTIN = listOf(MAC_DARK, MAC_LIGHT, SUNSET, OCEAN_GLASS, BUBBLEGUM)

    fun byId(id: String): SkinPack? = BUILTIN.firstOrNull { it.id == id }

    /**
     * 지금 적용할 팩. 선택이 [SkinPrefs.AUTO]면 기존 라이트/다크 토글을 따르고,
     * 특정 팩을 골랐으면 그 팩을 쓴다. 없어진 id이거나 프리미엄인데 해금 안 됐으면
     * (예: 다른 기기에서 백업 복원) 테마 기본으로 폴백한다.
     */
    fun current(context: Context): SkinPack {
        val themeDefault = if (WidgetTheme.isLight(context)) MAC_LIGHT else MAC_DARK
        val id = SkinPrefs.selectedId(context)
        if (id == SkinPrefs.AUTO) return themeDefault
        val pack = byId(id) ?: return themeDefault
        return if (Entitlements.isUnlocked(context, pack)) pack else themeDefault
    }

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
