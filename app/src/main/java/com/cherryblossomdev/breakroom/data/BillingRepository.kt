package com.cherryblossomdev.breakroom.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BillingRepository(context: Context) {

    companion object {
        // Update this after creating the subscription product in Google Play Console
        const val PRODUCT_ID = "breakroom_premium_monthly"
    }

    sealed class PurchaseResult {
        data class Success(val purchase: Purchase) : PurchaseResult()
        data object Cancelled : PurchaseResult()
        data class Error(val message: String) : PurchaseResult()
    }

    private val _purchaseResult = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 1)
    val purchaseResult: SharedFlow<PurchaseResult> = _purchaseResult.asSharedFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            when {
                result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty() -> {
                    purchases
                        .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        .forEach { _purchaseResult.tryEmit(PurchaseResult.Success(it)) }
                }
                result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                    _purchaseResult.tryEmit(PurchaseResult.Cancelled)
                else ->
                    _purchaseResult.tryEmit(PurchaseResult.Error(result.debugMessage))
            }
        }
        .enablePendingPurchases()
        .build()

    suspend fun ensureConnected(): Boolean {
        if (billingClient.isReady) return true
        return suspendCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    // Will retry on next ensureConnected() call
                }
            })
        }
    }

    suspend fun queryProductDetails(): ProductDetails? {
        if (!ensureConnected()) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        val result = billingClient.queryProductDetails(params)
        return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK)
            result.productDetailsList?.firstOrNull()
        else null
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
            _purchaseResult.tryEmit(PurchaseResult.Error("No subscription offer available"))
            return
        }
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    suspend fun acknowledgePurchase(purchaseToken: String): Boolean {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        val result = billingClient.acknowledgePurchase(params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }
}
