package com.jex.maclauncher.widget.skin

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * 프리미엄 팩 구매의 연결 지점(seam).
 *
 * 실제 결제(Google Play Billing)는 회사 Mac + Play 콘솔(상품 등록·릴리스 서명)에서 붙인다.
 * 여기서는 그때 끼워 넣을 자리만 비워두고, 집(디버그 빌드)에서는 결제 없이 바로 해금해
 * UI/흐름을 테스트할 수 있게 한다.
 *
 * 붙일 때 할 일(회사 Mac):
 *   1) build.gradle 에 com.android.billingclient:billing-ktx 추가
 *   2) 각 프리미엄 팩 id로 Play 콘솔에 인앱 상품 등록
 *   3) [purchase] 안에서 BillingClient.launchBillingFlow → 구매 확인(acknowledge)
 *      → [Entitlements.grant] → onResult(Purchased)
 *   4) 앱 시작 시 queryPurchases 로 복원해 [Entitlements] 동기화(환불 시 revoke)
 */
object SkinBilling {

    sealed interface Result {
        object Purchased : Result
        object Canceled : Result
        data class Unavailable(val reason: String) : Result
    }

    /** 실제 결제 플로우. 아직 미연결이라 Unavailable. (회사 Mac에서 구현) */
    fun purchase(context: Context, pack: SkinPack, onResult: (Result) -> Unit) {
        onResult(Result.Unavailable("결제가 아직 연결되지 않았어요"))
    }

    /** 디버그 빌드에서만: 결제 없이 바로 해금(테스트용). 릴리스에선 아무것도 안 함. */
    fun debugUnlock(context: Context, pack: SkinPack): Boolean {
        if (!isDebug(context)) return false
        Entitlements.grant(context, pack.id)
        return true
    }

    fun isDebug(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
