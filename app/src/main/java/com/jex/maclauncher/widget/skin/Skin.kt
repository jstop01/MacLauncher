package com.jex.maclauncher.widget.skin

import android.content.Context
import android.content.res.XmlResourceParser
import android.view.View
import android.widget.RemoteViews
import com.jex.maclauncher.R
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

    // 색 있는 배경 위에서는 회색 트랙·패널색이 탁해 보여서, 반투명 흰색/검정으로 배경색이 비치게 한다
    private fun onColor(panel: Int) = WidgetTheme.Palette(
        text = 0xFFFFFFFF.toInt(), subText = 0xD9FFFFFF.toInt(), dim = 0x80FFFFFF.toInt(),
        track = 0x40FFFFFF, grid = 0x33FFFFFF, panel = panel,
    )

    private fun onPastel(panel: Int) = WidgetTheme.Palette(
        text = 0xFF1C1C1E.toInt(), subText = 0xB31C1C1E.toInt(), dim = 0x661C1C1E,
        track = 0x26000000, grid = 0x1F000000, panel = panel,
    )

    /** 선셋: 주황→마젠타 그라데이션. 채도 높아 흰 글씨가 잘 읽힌다. */
    val SUNSET = SkinPack(
        id = "sunset",
        name = "선셋",
        tier = Tier.FREE,
        isLight = false,
        palette = onColor(0xFFEE3A52.toInt()),
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
        palette = onColor(0xFF0A4A6B.toInt()),
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
        palette = onPastel(0xFFDAEFF2.toInt()),
        accent = 0xFFFF4D8D.toInt(),
        panel = PanelStyle.Gradient(from = 0xFFFFDEE9.toInt(), to = 0xFFB5FFFC.toInt(), angleDeg = 90f, stroke = 0x1A000000),
        icon = IconStyle(),
        cornerRadiusDp = 24f,
    )

    /** 미드나잇: 짙은 남색 단색 + 푸른 테두리. 맥 다크보다 차분한 밤 느낌. */
    val MIDNIGHT = SkinPack(
        id = "midnight",
        name = "미드나잇",
        tier = Tier.FREE,
        isLight = false,
        palette = WidgetTheme.DARK.copy(subText = 0xA6C8D2FF.toInt(), track = 0xFF232A4D.toInt(), panel = 0xFF0B1026.toInt()),
        accent = 0xFF5E8BFF.toInt(),
        panel = PanelStyle.Solid(color = 0xEB0B1026.toInt(), stroke = 0x404F6BFF),
        icon = IconStyle(),
        cornerRadiusDp = 20f,
    )

    /** 포레스트: 깊은 청록→연두 그라데이션. (프리미엄) */
    val FOREST = SkinPack(
        id = "forest",
        name = "포레스트",
        tier = Tier.PREMIUM,
        isLight = false,
        palette = onColor(0xFF3F8070.toInt()),
        accent = 0xFFFFE08A.toInt(),
        panel = PanelStyle.Gradient(from = 0xFF134E5E.toInt(), to = 0xFF71B280.toInt(), angleDeg = 90f, stroke = 0x33FFFFFF),
        icon = IconStyle(),
        cornerRadiusDp = 24f,
    )

    /** 라벤더: 연보라→하늘 파스텔. 밝은 배경이라 어두운 글씨. (프리미엄) */
    val LAVENDER = SkinPack(
        id = "lavender",
        name = "라벤더",
        tier = Tier.PREMIUM,
        isLight = true,
        palette = onPastel(0xFFC9C6FB.toInt()),
        accent = 0xFF6A3DF0.toInt(),
        panel = PanelStyle.Gradient(from = 0xFFE0C3FC.toInt(), to = 0xFF8EC5FC.toInt(), angleDeg = 90f, stroke = 0x1A000000),
        icon = IconStyle(),
        cornerRadiusDp = 24f,
    )

    /** 피커에 보이는 순서: 무료 먼저, 프리미엄은 해금 전엔 자물쇠 */
    val BUILTIN = listOf(MAC_DARK, MAC_LIGHT, SUNSET, MIDNIGHT, OCEAN_GLASS, BUBBLEGUM, FOREST, LAVENDER)

    fun byId(id: String): SkinPack? = BUILTIN.firstOrNull { it.id == id }

    /** 강조색 위에 올릴 글자색: 밝은 강조색(선셋 노랑 등)이면 검정, 아니면 흰색 */
    fun onAccent(pack: SkinPack): Int {
        val c = pack.accent
        val lum = 0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
        return if (lum > 170) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
    }

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

    /**
     * 레이아웃의 skin_bg에 지금 팩의 패널을 깔고 그 views를 그대로 돌려준다 (날씨·시계 외 위젯 공용).
     * 맥 다크/라이트는 비트맵 없이 기존 드로어블. panel=false면 배경 없이(Finder 최소화).
     * 레이아웃에 박힌 글자색도 [tintText]로 팩 팔레트로 바꿔 끼운다.
     */
    fun dress(
        context: Context, views: RemoteViews, size: WidgetSize, panel: Boolean = true,
        macDark: Int = R.drawable.widget_bg, macLight: Int = R.drawable.widget_bg_light,
    ): RemoteViews {
        val pack = current(context)
        views.setViewVisibility(R.id.skin_bg, if (panel) View.VISIBLE else View.GONE)
        when {
            !panel -> Unit
            pack.id == MAC_DARK.id -> views.setImageViewResource(R.id.skin_bg, macDark)
            pack.id == MAC_LIGHT.id -> views.setImageViewResource(R.id.skin_bg, macLight)
            else -> applyPanel(context, views, R.id.skin_bg, pack, size)
        }
        tintText(context, views, pack)
        return views
    }

    private enum class Role { TEXT, SUB, DIM }

    // 레이아웃(다크/라이트)에 적힌 색 → 역할. 여기 없는 색(빨강 일요일, 파랑 다운로드 등)은 의미가 있어서 그대로 둔다
    private val ROLE_OF = mapOf(
        0xFFFFFFFF.toInt() to Role.TEXT, 0xFF1C1C1E.toInt() to Role.TEXT,
        0x99FFFFFF.toInt() to Role.SUB, 0xFF8E8E93.toInt() to Role.SUB,
        0xCCFFFFFF.toInt() to Role.SUB, 0xCC1C1C1E.toInt() to Role.SUB,
        0x66FFFFFF to Role.DIM, 0xFFAEAEB2.toInt() to Role.DIM,
    )

    private val roleCache = HashMap<Int, List<Pair<Int, Role>>>()

    /**
     * views의 레이아웃 XML을 읽어 역할 색을 쓰는 TextView(id 있는 것)에 팩 글자색을 입힌다.
     * 맥 다크/라이트는 레이아웃 색이 곧 팩 색이라 건너뛴다. 코드에서 따로 setTextColor 한 뷰는 강조색 등 역할 밖 색이라 겹치지 않는다.
     */
    fun tintText(context: Context, views: RemoteViews, pack: SkinPack = current(context)) {
        if (pack.id == MAC_DARK.id || pack.id == MAC_LIGHT.id) return
        val p = pack.palette
        for ((id, role) in roles(context, views.layoutId)) {
            views.setTextColor(id, when (role) { Role.TEXT -> p.text; Role.SUB -> p.subText; Role.DIM -> p.dim })
        }
    }

    private fun roles(context: Context, layout: Int): List<Pair<Int, Role>> = synchronized(roleCache) {
        roleCache.getOrPut(layout) {
            val out = ArrayList<Pair<Int, Role>>()
            runCatching {
                context.resources.getLayout(layout).use { x ->
                    while (x.next() != XmlResourceParser.END_DOCUMENT) {
                        if (x.eventType != XmlResourceParser.START_TAG || x.getAttributeValue(ANDROID, "textColor") == null) continue
                        val id = x.getAttributeResourceValue(ANDROID, "id", 0)
                        val role = ROLE_OF[x.getAttributeIntValue(ANDROID, "textColor", 0)]
                        if (id != 0 && role != null) out += id to role
                    }
                }
            }
            out
        }
    }

    private const val ANDROID = "http://schemas.android.com/apk/res/android"

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
