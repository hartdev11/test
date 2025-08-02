package com.natthasethstudio.sethpos

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.natthasethstudio.sethpos.R
import com.natthasethstudio.sethpos.util.PremiumChecker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import android.widget.TextView

class SubscriptionActivity : AppCompatActivity() {
    private lateinit var billingClient: BillingClient
    private lateinit var monthlySubscribeButton: Button
    private lateinit var yearlySubscribeButton: Button
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var errorTextView: TextView
    private lateinit var retryButton: Button

    // ใช้ product ID ที่แตกต่างจากฝั่งลูกค้า
    private val monthlyProductId = "storepremiummonthly69"
    private val yearlyProductId = "storepremiummonthly690"
    private lateinit var monthlyProductDetails: ProductDetails
    private lateinit var yearlyProductDetails: ProductDetails

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        // Initialize views
        monthlySubscribeButton = findViewById(R.id.monthlySubscribeButton)
        yearlySubscribeButton = findViewById(R.id.yearlySubscribeButton)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        errorTextView = findViewById(R.id.errorTextView)
        retryButton = findViewById(R.id.retryButton)

        retryButton.setOnClickListener {
            showError("")
            showLoading(true)
            connectToBillingService()
        }

        // Check if store is already premium
        lifecycleScope.launch {
            if (PremiumChecker.isPremiumStore()) {
                showPremiumStatus()
                return@launch
            }

            // Initialize billing client
            billingClient = BillingClient.newBuilder(this@SubscriptionActivity)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build()

            // Connect to billing service
            showLoading(true)
            connectToBillingService()

            // Set up click listeners
            monthlySubscribeButton.setOnClickListener {
                subscribeToPremium(monthlyProductDetails)
            }

            yearlySubscribeButton.setOnClickListener {
                subscribeToPremium(yearlyProductDetails)
            }
        }
    }

    private val purchasesUpdatedListener = object : PurchasesUpdatedListener {
        override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                Toast.makeText(this@SubscriptionActivity, "การสมัครสมาชิกถูกยกเลิก", Toast.LENGTH_SHORT).show()
            } else {
                showError("ไม่สามารถดำเนินการได้ กรุณาลองใหม่อีกครั้ง")
            }
        }
    }

    private fun connectToBillingService() {
        showLoading(true)
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    showLoading(false)
                    queryProducts()
                } else {
                    showError("ไม่สามารถเชื่อมต่อกับระบบชำระเงินได้")
                }
            }

            override fun onBillingServiceDisconnected() {
                showError("การเชื่อมต่อถูกตัด กรุณาลองใหม่อีกครั้ง")
            }
        })
    }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(monthlyProductId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(yearlyProductId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        // Disable buttons while loading
        monthlySubscribeButton.isEnabled = false
        yearlySubscribeButton.isEnabled = false

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !productDetailsList.isNullOrEmpty()) {
                errorTextView.visibility = View.GONE
                retryButton.visibility = View.GONE
                productDetailsList.forEach { productDetails ->
                    when (productDetails.productId) {
                        monthlyProductId -> {
                            monthlyProductDetails = productDetails
                            val price = productDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: ""
                            monthlySubscribeButton.text = "สมัครสมาชิกรายเดือน $price/เดือน"
                            monthlySubscribeButton.isEnabled = true
                        }
                        yearlyProductId -> {
                            yearlyProductDetails = productDetails
                            val price = productDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: ""
                            yearlySubscribeButton.text = "สมัครสมาชิกรายปี $price/ปี"
                            yearlySubscribeButton.isEnabled = true
                        }
                    }
                }
                showLoading(false)
            } else {
                showError("ไม่พบแพ็กเกจที่ต้องการ กรุณาตรวจสอบการเชื่อมต่อหรือรหัสสินค้า แล้วลองใหม่อีกครั้ง")
            }
        }
    }

    private fun subscribeToPremium(productDetails: ProductDetails) {
        showLoading(true)
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken != null) {
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .setOfferToken(offerToken)
                            .build()
                    )
                )
                .build()
            billingClient.launchBillingFlow(this, flowParams)
        } else {
            showError("ไม่สามารถดำเนินการสมัครสมาชิกได้ กรุณาลองใหม่อีกครั้ง")
        }
        showLoading(false)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        updatePremiumStatus(purchase)
                    }
                }
            } else {
                updatePremiumStatus(purchase)
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Toast.makeText(this, "การชำระเงินอยู่ระหว่างดำเนินการ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePremiumStatus(purchase: Purchase) {
        PremiumChecker.setPremiumMerchant(true)
        showPremiumStatus()
        Toast.makeText(this@SubscriptionActivity, "สมัครสมาชิกพรีเมียมสำเร็จ!", Toast.LENGTH_SHORT).show()
    }

    private fun checkSubscriptionStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("premium_stores").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val isPremium = document.getBoolean("isPremium") ?: false
                    val subscriptionId = document.getString("subscriptionId") ?: ""

                    if (isPremium && subscriptionId.isNotEmpty()) {
                        val params = QueryPurchasesParams.newBuilder()
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()

                        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                val isStillValid = purchases.any {
                                    it.products.contains(subscriptionId) &&
                                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                                }

                                if (!isStillValid) {
                                    val premiumData = hashMapOf(
                                        "isPremium" to false,
                                        "subscriptionId" to subscriptionId,
                                        "expirationTime" to System.currentTimeMillis()
                                    )

                                    db.collection("premium_stores").document(userId)
                                        .set(premiumData)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "สมาชิกของคุณหมดอายุแล้ว", Toast.LENGTH_SHORT).show()
                                            finish()
                                        }
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun showPremiumStatus() {
        monthlySubscribeButton.visibility = View.GONE
        yearlySubscribeButton.visibility = View.GONE
        Toast.makeText(this, "คุณเป็นสมาชิกพรีเมียมแล้ว!", Toast.LENGTH_SHORT).show()
    }

    private fun showLoading(isLoading: Boolean) {
        loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        monthlySubscribeButton.isEnabled = !isLoading
        yearlySubscribeButton.isEnabled = !isLoading
        retryButton.visibility = View.GONE
        errorTextView.visibility = View.GONE
    }

    private fun showError(message: String) {
        loadingProgressBar.visibility = View.GONE
        errorTextView.text = message
        errorTextView.visibility = if (message.isNotEmpty()) View.VISIBLE else View.GONE
        retryButton.visibility = if (message.isNotEmpty()) View.VISIBLE else View.GONE
        monthlySubscribeButton.isEnabled = false
        yearlySubscribeButton.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        checkSubscriptionStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
}