package com.jex.maclauncher.widget.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * 감성 팩을 새로 만들 때 글씨가 안 읽히는 실수를 잡는다.
 * 패널 배경(그라데이션이면 양 끝 색 모두) 위에서 글자색의 WCAG 대비를 잰다.
 */
class SkinContrastTest {

    @Test
    fun bodyTextIsReadableOnEveryPanelColor() = forEachPanel { pack, bg ->
        val ratio = contrast(over(pack.palette.text, bg), bg)
        assertTrue("${pack.id} 본문 글씨 대비 ${"%.2f".format(ratio)} < 4.5", ratio >= 4.5)
    }

    @Test
    fun secondaryTextIsReadableOnEveryPanelColor() = forEachPanel { pack, bg ->
        val ratio = contrast(over(pack.palette.subText, bg), bg)
        assertTrue("${pack.id} 보조 글씨 대비 ${"%.2f".format(ratio)} < 3", ratio >= 3.0)
    }

    @Test
    fun accentStandsOutFromPanel() = forEachPanel { pack, bg ->
        // 링·그래프·버튼 글씨: 큰 그래픽이라 본문보다 낮은 기준
        val ratio = contrast(pack.accent, bg)
        assertTrue("${pack.id} 강조색 대비 ${"%.2f".format(ratio)} < 1.8", ratio >= 1.8)
    }

    @Test
    fun textOnAccentIsReadable() = Skin.BUILTIN.forEach { pack ->
        // 오늘 날짜 숫자는 굵은 글씨라 큰 글씨 기준(3:1). 맥 파랑 위 흰 글씨(3.65)가 기준점
        val ratio = contrast(Skin.onAccent(pack), pack.accent)
        assertTrue("${pack.id} 오늘 표시 숫자 대비 ${"%.2f".format(ratio)} < 3", ratio >= 3.0)
    }

    @Test
    fun packIdsAreUniqueAndPremiumProductIdsAreStable() {
        val ids = Skin.BUILTIN.map { it.id }
        assertEquals("팩 id 중복", ids.size, ids.toSet().size)
        assertTrue(Skin.BUILTIN.all { Skin.byId(it.id) === it })
        // Play 상품 id는 한 번 등록하면 못 바꾼다: skin_<팩 id> 규칙 고정
        assertEquals("skin_ocean_glass", SkinBilling.productId(Skin.OCEAN_GLASS))
    }

    /** 모든 팩·패널 색을 검사하고 실패를 한꺼번에 보여준다 (첫 실패에서 멈추지 않게) */
    private fun forEachPanel(check: (SkinPack, Int) -> Unit) {
        val failures = Skin.BUILTIN.flatMap { pack ->
            val colors = when (val p = pack.panel) {
                is PanelStyle.Solid -> listOf(p.color)
                is PanelStyle.Gradient -> listOf(p.from, p.to)
                is PanelStyle.Glass -> listOf(p.tint)
            }
            colors.mapNotNull { runCatching { check(pack, opaque(it)) }.exceptionOrNull()?.message }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun opaque(c: Int) = c or (0xFF shl 24)

    /** 반투명 글자색을 배경 위에 얹은 실제 색 */
    private fun over(fg: Int, bg: Int): Int {
        val a = ((fg ushr 24) and 0xFF) / 255.0
        fun ch(shift: Int) = (((fg shr shift) and 0xFF) * a + ((bg shr shift) and 0xFF) * (1 - a)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun luminance(c: Int): Double {
        fun lin(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin((c shr 16) and 0xFF) + 0.7152 * lin((c shr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
