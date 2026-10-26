package com.jex.maclauncher.widget.skin

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.jex.maclauncher.widget.WidgetRefresher

/**
 * 프리미엄 감성 팩 인앱 결제 (Google Play Billing).
 *
 * 상품 id는 "skin_<팩 id>" (예: skin_ocean_glass). Play 콘솔에 같은 id의 일회성 인앱 상품을 등록하면 판매된다.
 * Play에서 받지 않은 빌드(GitHub APK)나 상품 미등록 상태에선 상품 조회가 비어 [Result.Unavailable].
 *
 * 구매 내역이 진짜 권한이고, [Entitlements]는 그 캐시다: 앱을 열 때 [restore]로 다시 맞춘다(환불되면 회수).
 */
object SkinBilling {

    sealed interface Result {
        object Purchased : Result
        object Canceled : Result
        data class Unavailable(val reason: String) : Result
    }

    fun productId(pack: SkinPack) = "skin_${pack.id}"

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var client: BillingClient? = null
    private val details = HashMap<String, ProductDetails>()
    private var pending: Pair<String, (Result) -> Unit>? = null  // 진행 중인 구매 (상품 id, 콜백)

    private fun client(context: Context): BillingClient = client ?: BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onPurchasesUpdated(context.applicationContext, result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build().also { client = it }

    /** 연결돼 있으면 바로, 아니면 연결 후 실행. 실패하면 onFail (메인 스레드) */
    private fun withClient(context: Context, onFail: (String) -> Unit, block: (BillingClient) -> Unit) {
        val c = client(context)
        if (c.isReady) return block(c)
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                main.post {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) block(c)
                    else onFail(message(result))
                }
            }

            override fun onBillingServiceDisconnected() {
                client = null  // 다음 호출 때 새로 연결
            }
        })
    }

    /** 표시용 가격 (예: "₩1,200"). 아직 모르면 null이고, 알아내면 onPrice로 알려준다. */
    fun price(context: Context, pack: SkinPack, onPrice: (String) -> Unit) {
        details[productId(pack)]?.let { d -> d.oneTimePurchaseOfferDetails?.formattedPrice?.let(onPrice); return }
        loadDetails(context, listOf(productId(pack)), onFail = {}) {
            details[productId(pack)]?.oneTimePurchaseOfferDetails?.formattedPrice?.let(onPrice)
        }
    }

    private fun loadDetails(context: Context, ids: List<String>, onFail: (String) -> Unit, done: () -> Unit) {
        withClient(context, onFail) { c ->
            val params = QueryProductDetailsParams.newBuilder().setProductList(ids.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
            }).build()
            c.queryProductDetailsAsync(params) { result, list ->
                main.post {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) return@post onFail(message(result))
                    list.forEach { details[it.productId] = it }
                    done()
                }
            }
        }
    }

    /** 결제 창을 띄운다. 결과는 메인 스레드에서 onResult로. */
    fun purchase(activity: Activity, pack: SkinPack, onResult: (Result) -> Unit) {
        val id = productId(pack)
        loadDetails(activity, listOf(id), onFail = { onResult(Result.Unavailable(it)) }) {
            val d = details[id] ?: return@loadDetails onResult(Result.Unavailable("아직 판매 준비 중이에요 (Play 스토어 버전에서 구매할 수 있어요)"))
            val c = client ?: return@loadDetails onResult(Result.Unavailable("결제 연결이 끊겼어요. 다시 시도해 주세요"))
            pending = id to onResult
            val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()),
            ).build()
            val r = c.launchBillingFlow(activity, flow)
            if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                pending = null
                onResult(if (r.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                    restore(activity); Result.Purchased
                } else Result.Unavailable(message(r)))
            }
        }
    }

    private fun onPurchasesUpdated(context: Context, result: BillingResult, purchases: List<Purchase>?) {
        main.post {
            val (id, callback) = pending ?: (null to null)
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchases.orEmpty().forEach { handle(context, it) }
                    val bought = purchases.orEmpty().any { p ->
                        p.purchaseState == Purchase.PurchaseState.PURCHASED && id in p.products
                    }
                    val waiting = purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING }
                    callback?.invoke(when {
                        bought -> Result.Purchased
                        waiting -> Result.Unavailable("결제 승인을 기다리고 있어요. 완료되면 자동으로 해금돼요")
                        else -> Result.Canceled
                    })
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> callback?.invoke(Result.Canceled)
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { restore(context); callback?.invoke(Result.Purchased) }
                else -> callback?.invoke(Result.Unavailable(message(result)))
            }
            pending = null
        }
    }

    /** 결제 완료된 구매를 해금하고, 아직 확인(acknowledge) 안 했으면 확인한다 (3일 안에 안 하면 자동 환불). */
    private fun handle(context: Context, p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        packIds(p).forEach { Entitlements.grant(context, it) }
        if (!p.isAcknowledged) {
            client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {}
        }
    }

    private fun packIds(p: Purchase) = p.products.filter { it.startsWith("skin_") }.map { it.removePrefix("skin_") }

    /**
     * Play 구매 내역으로 [Entitlements]를 다시 맞춘다 (앱 시작 시). 다른 기기에서 산 팩이 생기고, 환불된 팩은 잠긴다.
     * 바뀐 게 있으면 위젯을 다시 그리고 onChanged를 부른다. 연결 실패(오프라인 등) 땐 캐시를 그대로 둔다.
     */
    fun restore(context: Context, onChanged: () -> Unit = {}) {
        val app = context.applicationContext
        withClient(app, onFail = {}) { c ->
            c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
                main.post {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) return@post
                    val before = Entitlements.unlockedIds(app)
                    val owned = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    owned.forEach { handle(app, it) }
                    // 디버그 빌드는 '바로 해금'으로 연 팩이 있으니 회수하지 않는다
                    if (!isDebug(app)) {
                        val ownedIds = owned.flatMap { packIds(it) }.toSet()
                        (before - ownedIds).forEach { Entitlements.revoke(app, it) }
                    }
                    if (Entitlements.unlockedIds(app) != before) {
                        WidgetRefresher.all(app)  // 회수됐으면 위젯이 기본 팩으로
                        onChanged()
                    }
                }
            }
        }
    }

    private fun message(r: BillingResult): String = when (r.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "이 기기에서는 Play 결제를 쓸 수 없어요"
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.NETWORK_ERROR -> "Play 스토어에 연결하지 못했어요. 인터넷을 확인해 주세요"
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "아직 판매 준비 중이에요"
        else -> "결제를 진행하지 못했어요 (${r.responseCode})"
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
