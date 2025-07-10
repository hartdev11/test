package com.natthasethstudio.sethpos.ui

import android.content.Intent
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
import com.natthasethstudio.sethpos.SubscriptionActivity
import com.natthasethstudio.sethpos.util.PremiumChecker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.launch
import java.util.*

class PremiumSubscriptionActivity : AppCompatActivity() {
    private lateinit var billingClient: BillingClient
    private lateinit var monthlySubscribeButton: Button
    private lateinit var yearlySubscribeButton: Button
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var errorTextView: TextView
    private lateinit var monthlyPlanCard: View
    private lateinit var yearlyPlanCard: View

    private val monthlyProductId = "customer_monthly"
    private val yearlyProductId = "customer_yearly"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium_subscription)

        // ตรวจสอบ role ของผู้ใช้
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener(object : com.google.android.gms.tasks.OnSuccessListener<DocumentSnapshot> {
                    override fun onSuccess(document: DocumentSnapshot) {
                    if (document.exists()) {
                        val role = document.getString("role") ?: "customer"
                        if (role == "merchant") {
                            // ถ้าเป็นร้านค้า ให้ redirect ไปที่หน้าสมัครสมาชิกของร้านค้า
                                val intent = Intent(this@PremiumSubscriptionActivity, SubscriptionActivity::class.java)
                            startActivity(intent)
                            finish()
                            }
                        }
                    }
                })
        }

        // Initialize views
        monthlySubscribeButton = findViewById(R.id.monthlySubscribeButton)
        yearlySubscribeButton = findViewById(R.id.yearlySubscribeButton)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        errorTextView = findViewById(R.id.errorTextView)
        monthlyPlanCard = findViewById(R.id.monthlyPlanCard)
        yearlyPlanCard = findViewById(R.id.yearlyPlanCard)

        // Check if user is already premium
        lifecycleScope.launch {
            if (PremiumChecker.isPremiumUser()) {
                showPremiumStatus()
                return@launch
            }

            // Initialize billing client
            billingClient = BillingClient.newBuilder(this@PremiumSubscriptionActivity)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().build()
                )
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

    private val purchasesUpdatedListener = object : PurchasesUpdatedListener {
        override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                Toast.makeText(this@PremiumSubscriptionActivity, "การสมัครสมาชิกถูกยกเลิก", Toast.LENGTH_SHORT).show()
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
                    // The BillingClient is ready
                    showLoading(false)
                    // Query available products
                    queryProducts()
                } else {
                    showError("ไม่สามารถเชื่อมต่อกับระบบชำระเงินได้")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request
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

        billingClient.queryProductDetailsAsync(params, object : ProductDetailsResponseListener {
            override fun onProductDetailsResponse(billingResult: BillingResult, result: QueryProductDetailsResult) {
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val productDetailsList = result.productDetailsList
                    if (!productDetailsList.isNullOrEmpty()) {
                    updateProductDetails(productDetailsList)
                } else {
                    showError("ไม่พบแพ็คเกจที่ต้องการ")
                }
            } else {
                showError("ไม่สามารถดึงข้อมูลแพ็คเกจได้")
            }
        }
        })
    }

    private fun updateProductDetails(productDetailsList: List<ProductDetails>) {
        // Update UI with product details
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

        billingClient.queryProductDetailsAsync(params, object : ProductDetailsResponseListener {
            override fun onProductDetailsResponse(
                billingResult: BillingResult,
                result: QueryProductDetailsResult
            ) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val productDetailsList = result.productDetailsList
                    if (!productDetailsList.isNullOrEmpty()) {
                        val productDetails = productDetailsList[0]
                        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

                        if (!offerToken.isNullOrEmpty()) {
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

                             billingClient.launchBillingFlow(
                                this@PremiumSubscriptionActivity,
                                billingFlowParams
                            )
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
        })
    }


    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == 1) { // PURCHASED
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, object : AcknowledgePurchaseResponseListener {
                    override fun onAcknowledgePurchaseResponse(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        updatePremiumStatus(purchase)
                    }
                }
                })
            } else {
                updatePremiumStatus(purchase)
            }
        } else if (purchase.purchaseState == 2) { // PENDING
            Toast.makeText(this, "การชำระเงินอยู่ระหว่างดำเนินการ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSubscriptionStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("premium_users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val isPremium = document.getBoolean("isPremium") ?: false
                    val purchaseTime = document.getLong("purchaseTime") ?: 0
                    val subscriptionId = document.getString("subscriptionId") ?: ""

                    // ตรวจสอบการหมดอายุ
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
                                    // อัพเดทสถานะเป็นหมดอายุ
                                    val premiumData = hashMapOf(
                                        "isPremium" to false,
                                        "subscriptionId" to subscriptionId,
                                        "expirationTime" to System.currentTimeMillis()
                                    )

                                    db.collection("premium_users").document(userId)
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

    override fun onResume() {
        super.onResume()
        checkSubscriptionStatus()
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

        db.collection("premium_users").document(userId)
            .set(premiumData)
            .addOnSuccessListener {
                showPremiumStatus()
                Toast.makeText(this, "สมัครสมาชิกพรีเมียมสำเร็จ!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener(object : com.google.android.gms.tasks.OnFailureListener {
                override fun onFailure(e: Exception) {
                showError("ไม่สามารถอัปเดตสถานะสมาชิกได้")
            }
            })
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
}