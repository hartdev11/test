package com.natthasethstudio.sethpos

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.natthasethstudio.sethpos.R
import com.natthasethstudio.sethpos.util.PremiumChecker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.*

class SubscriptionActivity : AppCompatActivity() {
    private lateinit var billingClient: BillingClient
    private lateinit var monthlySubscribeButton: Button
    private lateinit var yearlySubscribeButton: Button
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var errorTextView: TextView
    private lateinit var monthlyPlanCard: View
    private lateinit var yearlyPlanCard: View

    // ใช้ product ID ที่แตกต่างจากฝั่งลูกค้า
    private val monthlyProductId = "premium_monthly"
    private val yearlyProductId = "store_yearly"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        // Initialize views
        monthlySubscribeButton = findViewById(R.id.monthlySubscribeButton)
        yearlySubscribeButton = findViewById(R.id.yearlySubscribeButton)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        errorTextView = findViewById(R.id.errorTextView)
        monthlyPlanCard = findViewById(R.id.monthlyPlanCard)
        yearlyPlanCard = findViewById(R.id.yearlyPlanCard)

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
            connectToBillingService()

            // Set up click listeners
            monthlySubscribeButton.setOnClickListener {
                subscribeToPremium(monthlyProductId)
            }

            yearlySubscribeButton.setOnClickListener {
                subscribeToPremium(yearlyProductId)
            }
        }
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "การสมัครสมาชิกถูกยกเลิก", Toast.LENGTH_SHORT).show()
        } else {
            showError("ไม่สามารถดำเนินการได้ กรุณาลองใหม่อีกครั้ง")
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

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (productDetailsList.isNotEmpty()) {
                    updateProductDetails(productDetailsList)
                } else {
                    showError("ไม่พบแพ็คเกจที่ต้องการ")
                }
            } else {
                showError("ไม่สามารถดึงข้อมูลแพ็คเกจได้")
            }
        }
    }

    private fun updateProductDetails(productDetailsList: List<ProductDetails>) {
        for (productDetails in productDetailsList) {
            when (productDetails.productId) {
                monthlyProductId -> {
                    monthlySubscribeButton.text = "สมัครสมาชิก ${productDetails.subscriptionOfferDetails?.get(0)?.pricingPhases?.pricingPhaseList?.get(0)?.formattedPrice}/เดือน"
                }
                yearlyProductId -> {
                    yearlySubscribeButton.text = "สมัครสมาชิก ${productDetails.subscriptionOfferDetails?.get(0)?.pricingPhases?.pricingPhaseList?.get(0)?.formattedPrice}/ปี"
                }
            }
        }
    }

    private fun subscribeToPremium(productId: String) {
        showLoading(true)
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (productDetailsList.isNotEmpty()) {
                    val productDetails = productDetailsList[0]
                    val offerToken = productDetails.subscriptionOfferDetails?.get(0)?.offerToken
                    if (offerToken != null) {
                        val billingFlowParams = BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(
                                listOf(
                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(productDetails)
                                        .setOfferToken(offerToken)
                                        .build()
                                )
                            )
                            .build()

                        billingClient.launchBillingFlow(this, billingFlowParams)
                    } else {
                        showError("ไม่สามารถดำเนินการได้ กรุณาลองใหม่อีกครั้ง")
                    }
                } else {
                    showError("ไม่พบแพ็คเกจที่ต้องการ")
                }
            } else {
                showError("ไม่สามารถดึงข้อมูลแพ็คเกจได้")
            }
            showLoading(false)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == 1) { // PURCHASED
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
        } else if (purchase.purchaseState == 2) { // PENDING
            Toast.makeText(this, "การชำระเงินอยู่ระหว่างดำเนินการ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePremiumStatus(purchase: Purchase) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val premiumData = hashMapOf(
            "isPremium" to true,
            "subscriptionId" to purchase.products[0],
            "purchaseToken" to purchase.purchaseToken,
            "purchaseTime" to purchase.purchaseTime,
            "autoRenewing" to purchase.isAutoRenewing
        )

        db.collection("premium_stores").document(userId)
            .set(premiumData)
            .addOnSuccessListener {
                showPremiumStatus()
                Toast.makeText(this, "สมัครสมาชิกพรีเมียมสำเร็จ!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                showError("ไม่สามารถอัปเดตสถานะสมาชิกได้")
            }
    }

    private fun checkSubscriptionStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("premium_stores").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val isPremium = document.getBoolean("isPremium") ?: false
                    val purchaseTime = document.getLong("purchaseTime") ?: 0
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
        monthlyPlanCard.visibility = View.GONE
        yearlyPlanCard.visibility = View.GONE
        Toast.makeText(this, "คุณเป็นสมาชิกพรีเมียมแล้ว!", Toast.LENGTH_SHORT).show()
    }

    private fun showLoading(show: Boolean) {
        loadingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        monthlySubscribeButton.isEnabled = !show
        yearlySubscribeButton.isEnabled = !show
    }

    private fun showError(message: String) {
        errorTextView.text = message
        errorTextView.visibility = View.VISIBLE
        showLoading(false)
    }

    override fun onResume() {
        super.onResume()
        checkSubscriptionStatus()
    }
}