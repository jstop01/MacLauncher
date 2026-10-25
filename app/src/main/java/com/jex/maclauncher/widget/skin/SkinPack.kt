package com.jex.maclauncher.widget.skin

import com.jex.maclauncher.widget.WidgetTheme

/**
 * 한 "감성"을 데이터 하나로 표현한다. 새 감성 = 이 객체 하나(+선택적 배경 에셋)이고 코드가 아니다.
 * 위젯 레이아웃은 색이 없는 뼈대 한 벌만 두고, 색·배경·아이콘 스타일을 여기서 런타임에 입힌다.
 *
 * (마이그레이션 중: 지금은 날씨 위젯만 이 구조를 쓰고, 나머지는 [WidgetTheme]를 그대로 쓴다.)
 */
data class SkinPack(
    val id: String,                     // "mac_dark", "mac_light", "y2k" ...
    val name: String,                   // 사용자에게 보이는 이름
    val tier: Tier,                     // 무료 / 프리미엄(IAP)
    val isLight: Boolean,               // 밝은 배경 팩인지 (코드 드로잉 색 선택용)
    val palette: WidgetTheme.Palette,   // 글자·구분선·그래프 색 (기존 Palette 재사용)
    val accent: Int,                    // 강조색 (버튼·하이라이트)
    val panel: PanelStyle,              // 패널 배경을 어떻게 그릴지
    val icon: IconStyle,                // 코드로 그리는 아이콘 스타일
    val cornerRadiusDp: Float = 20f,
)

enum class Tier { FREE, PREMIUM }

/** 패널 배경 비트맵을 그리는 방식. 감성의 정체성 대부분이 여기서 나온다. */
sealed interface PanelStyle {
    /** 단색 + 테두리 (지금 widget_bg와 동일) */
    data class Solid(val color: Int, val stroke: Int) : PanelStyle

    /** 위→아래(또는 angle) 그라데이션 */
    data class Gradient(val from: Int, val to: Int, val angleDeg: Float = 90f, val stroke: Int = 0) : PanelStyle

    /** 반투명 유리 느낌 (단색 틴트 + 알파) */
    data class Glass(val tint: Int, val alpha: Int, val stroke: Int) : PanelStyle
    // 추후: data class Image(val assetPath: String, val scrim: Int) : PanelStyle
}

/** 코드로 그리는 아이콘(날씨·시계바늘·링 등)에 공통 적용할 스타일. 1f/1f/false = 지금과 동일. */
data class IconStyle(
    val strokeScale: Float = 1f,   // 선 두께 배율
    val saturation: Float = 1f,    // 채도 배율
    val shadow: Boolean = false,   // 부드러운 그림자
)
