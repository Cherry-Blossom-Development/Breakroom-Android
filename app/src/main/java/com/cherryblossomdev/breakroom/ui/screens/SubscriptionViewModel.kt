package com.cherryblossomdev.breakroom.ui.screens

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.BillingRepository
import com.cherryblossomdev.breakroom.data.SubscriptionApiRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubscriptionViewModel(
    private val billingRepository: BillingRepository,
    private val subscriptionApiRepository: SubscriptionApiRepository
) : ViewModel() {

    var isSubscribed by mutableStateOf(false)
        private set
    var isPurchasing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var purchaseSuccess by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            billingRepository.purchaseResult.collect { result ->
                when (result) {
                    is BillingRepository.PurchaseResult.Success -> handlePurchase(result.purchase)
                    is BillingRepository.PurchaseResult.Cancelled -> isPurchasing = false
                    is BillingRepository.PurchaseResult.Error -> {
                        isPurchasing = false
                        errorMessage = "Purchase failed. Please try again."
                    }
                }
            }
        }
    }

    fun checkSubscription() {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { subscriptionApiRepository.getStatus() }) {
                is BreakroomResult.Success -> isSubscribed = result.data.subscribed
                else -> { /* non-critical */ }
            }
        }
    }

    fun startPurchase(activity: Activity) {
        viewModelScope.launch {
            isPurchasing = true
            errorMessage = null
            val productDetails = withContext(Dispatchers.IO) { billingRepository.queryProductDetails() }
            if (productDetails == null) {
                isPurchasing = false
                errorMessage = "Subscription unavailable. Please try again later."
                return@launch
            }
            billingRepository.launchPurchaseFlow(activity, productDetails)
            // Result arrives via purchaseResult SharedFlow collected in init
        }
    }

    fun clearError() { errorMessage = null }
    fun clearPurchaseSuccess() { purchaseSuccess = false }

    private fun handlePurchase(purchase: com.android.billingclient.api.Purchase) {
        viewModelScope.launch {
            val ackOk = withContext(Dispatchers.IO) {
                billingRepository.acknowledgePurchase(purchase.purchaseToken)
            }
            if (!ackOk) {
                isPurchasing = false
                errorMessage = "Purchase complete but confirmation failed. Please contact support."
                return@launch
            }
            val productId = purchase.products.firstOrNull() ?: BillingRepository.PRODUCT_ID
            when (withContext(Dispatchers.IO) {
                subscriptionApiRepository.verifyGooglePurchase(purchase.purchaseToken, productId)
            }) {
                is BreakroomResult.Success -> {
                    isSubscribed = true
                    purchaseSuccess = true
                }
                else -> errorMessage = "Purchase complete but activation failed. Please contact support."
            }
            isPurchasing = false
        }
    }
}
