package com.example.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ProPlanOption(
    val productId: String,
    val productType: String,
    val title: String,
    val subtitle: String,
    val fallbackPrice: String,
    val badge: String?
) {
    MONTHLY(
        productId = "sukoono_pro_monthly",
        productType = BillingClient.ProductType.SUBS,
        title = "Monthly Pro",
        subtitle = "Cancel anytime • Full Pro access",
        fallbackPrice = "$3.99 / mo",
        badge = null
    ),
    YEARLY(
        productId = "sukoono_pro_yearly",
        productType = BillingClient.ProductType.SUBS,
        title = "Yearly Pro",
        subtitle = "Most popular • Billed annually",
        fallbackPrice = "$24.99 / yr",
        badge = "SAVE 48%"
    ),
    LIFETIME(
        productId = "sukoono_pro_lifetime",
        productType = BillingClient.ProductType.INAPP,
        title = "Lifetime Unlock",
        subtitle = "One-time purchase • Yours forever",
        fallbackPrice = "$49.99 once",
        badge = "ONE-TIME"
    )
}

data class BillingUiState(
    val isConnected: Boolean = false,
    val isPurchasing: Boolean = false,
    val productPrices: Map<ProPlanOption, String> = emptyMap(),
    val statusMessage: String? = null
)

/**
 * Google Play Billing v8 integration supporting Monthly/Yearly subscriptions
 * and a One-Time Lifetime Unlock, automatically acknowledging and restoring purchases.
 */
class BillingManager(
    private val appContext: Context,
    private val entitlementRepository: EntitlementRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private val _billingState = MutableStateFlow(BillingUiState())
    val billingState: StateFlow<BillingUiState> = _billingState.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                _billingState.value = _billingState.value.copy(isPurchasing = false)
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingState.value = _billingState.value.copy(
                    isPurchasing = false,
                    statusMessage = "Purchase cancelled."
                )
            }
            else -> {
                _billingState.value = _billingState.value.copy(
                    isPurchasing = false,
                    statusMessage = billingResult.debugMessage.ifBlank {
                        "Purchase could not be completed (code ${billingResult.responseCode})."
                    }
                )
            }
        }
    }

    private var billingClient: BillingClient? = null

    fun startConnection() {
        if (billingClient != null) return
        try {
            val pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()

            val client = BillingClient.newBuilder(appContext)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(pendingParams)
                .enableAutoServiceReconnection()
                .build()

            billingClient = client
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _billingState.value = _billingState.value.copy(
                            isConnected = true,
                            statusMessage = null
                        )
                        queryAllProductDetails()
                        restorePurchases()
                    } else {
                        _billingState.value = _billingState.value.copy(
                            isConnected = false
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _billingState.value = _billingState.value.copy(isConnected = false)
                }
            })
        } catch (_: Throwable) {
            _billingState.value = _billingState.value.copy(isConnected = false)
        }
    }

    private fun queryAllProductDetails() {
        val client = billingClient ?: return

        val subProducts = ProPlanOption.entries
            .filter { it.productType == BillingClient.ProductType.SUBS }
            .map { plan ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(plan.productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }

        val inAppProducts = ProPlanOption.entries
            .filter { it.productType == BillingClient.ProductType.INAPP }
            .map { plan ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(plan.productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }

        if (subProducts.isNotEmpty()) {
            val subParams = QueryProductDetailsParams.newBuilder().setProductList(subProducts).build()
            client.queryProductDetailsAsync(subParams) { result, detailsResult ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val updatedPrices = _billingState.value.productPrices.toMutableMap()
                    detailsResult.productDetailsList.forEach { details ->
                        productDetailsMap[details.productId] = details
                        val matchingPlan = ProPlanOption.entries.firstOrNull { it.productId == details.productId }
                        val formatted = details.subscriptionOfferDetails
                            ?.firstOrNull()
                            ?.pricingPhases
                            ?.pricingPhaseList
                            ?.firstOrNull()
                            ?.formattedPrice
                        if (matchingPlan != null && formatted != null) {
                            updatedPrices[matchingPlan] = formatted
                        }
                    }
                    _billingState.value = _billingState.value.copy(productPrices = updatedPrices)
                }
            }
        }

        if (inAppProducts.isNotEmpty()) {
            val inAppParams = QueryProductDetailsParams.newBuilder().setProductList(inAppProducts).build()
            client.queryProductDetailsAsync(inAppParams) { result, detailsResult ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val updatedPrices = _billingState.value.productPrices.toMutableMap()
                    detailsResult.productDetailsList.forEach { details ->
                        productDetailsMap[details.productId] = details
                        val matchingPlan = ProPlanOption.entries.firstOrNull { it.productId == details.productId }
                        val formatted = details.oneTimePurchaseOfferDetails?.formattedPrice
                        if (matchingPlan != null && formatted != null) {
                            updatedPrices[matchingPlan] = formatted
                        }
                    }
                    _billingState.value = _billingState.value.copy(productPrices = updatedPrices)
                }
            }
        }
    }

    fun restorePurchases() {
        val client = billingClient
        if (client == null || !client.isReady) {
            _billingState.value = _billingState.value.copy(
                statusMessage = if (AdMobManager.isNetworkConnected(appContext)) {
                    "Google Play Billing is unavailable on this device."
                } else {
                    "Connect to the internet to restore Google Play purchases."
                }
            )
            return
        }

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasActiveSub = purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        it.products.any { id ->
                            id == ProPlanOption.MONTHLY.productId || id == ProPlanOption.YEARLY.productId
                        }
                }
                scope.launch {
                    entitlementRepository.setProSubscription(hasActiveSub)
                }
                purchases.forEach { acknowledgeIfNeeded(it) }
            }
        }

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasLifetime = purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        it.products.contains(ProPlanOption.LIFETIME.productId)
                }
                scope.launch {
                    entitlementRepository.setLifetimeUnlocked(hasLifetime)
                }
                purchases.forEach { acknowledgeIfNeeded(it) }
            }
        }
    }

    fun launchPurchase(activity: Activity?, plan: ProPlanOption) {
        if (!AdMobManager.isNetworkConnected(appContext)) {
            _billingState.value = _billingState.value.copy(
                statusMessage = "You're offline. Connect to the internet to complete a Google Play purchase."
            )
            return
        }

        val client = billingClient
        if (client == null || !client.isReady || activity == null) {
            _billingState.value = _billingState.value.copy(
                statusMessage = "Google Play Store billing service is not ready on this device."
            )
            return
        }

        val details = productDetailsMap[plan.productId]
        if (details == null) {
            queryAllProductDetails()
            _billingState.value = _billingState.value.copy(
                statusMessage = "Loading store offer for ${plan.title}. Configure '${plan.productId}' in Google Play Console to complete live purchases."
            )
            return
        }

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        if (plan.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                _billingState.value = _billingState.value.copy(
                    statusMessage = "No active subscription offer found for ${plan.title}."
                )
                return
            }
            productParamsBuilder.setOfferToken(offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()

        _billingState.value = _billingState.value.copy(isPurchasing = true, statusMessage = null)
        val launchResult = client.launchBillingFlow(activity, flowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _billingState.value = _billingState.value.copy(
                isPurchasing = false,
                statusMessage = launchResult.debugMessage.ifBlank { "Unable to open Google Play checkout." }
            )
        }
    }

    fun clearStatusMessage() {
        _billingState.value = _billingState.value.copy(statusMessage = null)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            acknowledgeIfNeeded(purchase)
            scope.launch {
                if (purchase.products.contains(ProPlanOption.LIFETIME.productId)) {
                    entitlementRepository.setLifetimeUnlocked(true)
                }
                if (purchase.products.any {
                        it == ProPlanOption.MONTHLY.productId || it == ProPlanOption.YEARLY.productId
                    }
                ) {
                    entitlementRepository.setProSubscription(true)
                }
            }
            _billingState.value = _billingState.value.copy(
                statusMessage = "Welcome to Sukoono Pro! All sounds, mixer, and ad-free mode are now active."
            )
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            _billingState.value = _billingState.value.copy(
                statusMessage = "Purchase pending confirmation. Pro access will unlock automatically once payment settles."
            )
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        val client = billingClient ?: return
        if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { }
        }
    }

    fun endConnection() {
        try {
            billingClient?.endConnection()
        } catch (_: Throwable) {}
        billingClient = null
    }
}
