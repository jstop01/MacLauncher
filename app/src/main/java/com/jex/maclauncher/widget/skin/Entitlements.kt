package com.jex.maclauncher.widget.skin

import android.content.Context

/**
 * 어떤 프리미엄 팩을 해금했는지 로컬에 저장한다.
 *
 * 진짜 권한의 출처는 Google Play 계정(구매 내역)이다. 여기 저장소는 그 구매를
 * 확인한 결과를 캐시해 오프라인에서도 바로 쓰게 하는 용도다. 그래서 백업 파일에는
 * 넣지 않는다(기기 간엔 Play 계정으로 복원돼야 한다).
 *
 * 무료(FREE) 팩은 항상 해금 상태로 본다.
 */
object Entitlements {
    private const val PREFS = "skin_entitlements"
    private const val KEY_UNLOCKED = "unlocked"  // 콤마로 이은 팩 id 집합

    fun unlockedIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_UNLOCKED, "")!!
            .split(",").filter { it.isNotBlank() }.toSet()

    fun isUnlocked(context: Context, pack: SkinPack): Boolean =
        pack.tier == Tier.FREE || pack.id in unlockedIds(context)

    /** 구매 확인/복원 결과를 반영 (BillingManager가 호출). */
    fun grant(context: Context, packId: String) {
        val next = unlockedIds(context) + packId
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_UNLOCKED, next.joinToString(",")).apply()
    }

    /** Play에서 환불/취소된 경우 등. */
    fun revoke(context: Context, packId: String) {
        val next = unlockedIds(context) - packId
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_UNLOCKED, next.joinToString(",")).apply()
    }
}
