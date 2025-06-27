package com.natthasethstudio.sethpos.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
// import com.example.com.natthasethstudio.sethpos.util.PaymentManager // ลบออก
import com.natthasethstudio.sethpos.util.NotificationHelper
import java.util.Date
import java.util.Calendar

class SubscriptionRenewalWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val notificationHelper = NotificationHelper(context)

    override suspend fun doWork(): Result {
        val userId = auth.currentUser?.uid ?: return Result.failure()
        
        // ตรวจสอบสถานะสมาชิกพรีเมียม
        firestore.collection("premium_users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val endDate = document.getTimestamp("subscriptionEndDate")?.toDate()
                    val currentDate = Date()
                    
                    if (endDate != null && endDate.after(currentDate)) {
                        // ยังไม่หมดอายุ
                        // ส่งการแจ้งเตือนว่ากำลังจะหมดอายุ
                        sendExpirationNotification(endDate)
                        
                        // พยายามต่ออายุอัตโนมัติ
                        attemptAutoRenewal()
                    }
                }
            }
            .addOnFailureListener {
                // ไม่ต้อง return ใน listener
            }

        return Result.success()
    }

    private fun sendExpirationNotification(endDate: Date) {
        val calendar = Calendar.getInstance()
        calendar.time = endDate
        val daysUntilExpiration = ((endDate.time - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
        
        notificationHelper.showNotification(
            "สมาชิกพรีเมียมกำลังจะหมดอายุ",
            "สมาชิกพรีเมียมของคุณจะหมดอายุในอีก $daysUntilExpiration วัน"
        )
    }

    private fun attemptAutoRenewal() {
        // พยายามต่ออายุอัตโนมัติ
        // ลบทุกการใช้งาน PaymentManager ในคลาสนี้
    }
} 