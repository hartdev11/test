package com.natthasethstudio.sethpos.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import com.natthasethstudio.sethpos.R

class ProfileBackgroundManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "profile_background_prefs"
        private const val KEY_BACKGROUND_ID = "background_id"
        private const val KEY_USER_ID = "user_id"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // รายการพื้นหลังทั้งหมดที่มี animation
    private val backgroundDrawables = listOf(
        R.drawable.bg_profile_mountain_animated, // ภูเขา
        R.drawable.bg_profile_city_animated,     // เมือง
        R.drawable.bg_profile_sea_animated       // ทะเล
    )
    
    /**
     * รับพื้นหลังสำหรับผู้ใช้ (สุ่มหรือใช้ที่มีอยู่แล้ว)
     */
    fun getBackgroundForUser(userId: String): Drawable? {
        try {
            val savedUserId = prefs.getString(KEY_USER_ID, null)
            val savedBackgroundId = prefs.getInt(KEY_BACKGROUND_ID, -1)
            
            // ถ้าเป็นผู้ใช้ใหม่หรือเปลี่ยนผู้ใช้ ให้สุ่มพื้นหลังใหม่
            if (savedUserId != userId || savedBackgroundId == -1) {
                val randomBackgroundId = backgroundDrawables.random()
                saveBackgroundForUser(userId, randomBackgroundId)
                Log.d("ProfileBackgroundManager", "สุ่มพื้นหลังใหม่: $randomBackgroundId")
                return try {
                    ContextCompat.getDrawable(context, randomBackgroundId)
                } catch (e: Exception) {
                    Log.e("ProfileBackgroundManager", "Error loading random background: "+e.message)
                    // Fallback to mountain background
                    ContextCompat.getDrawable(context, R.drawable.bg_profile_mountain_animated)
                }
            }
            
            // ใช้พื้นหลังที่มีอยู่แล้ว
            Log.d("ProfileBackgroundManager", "ใช้พื้นหลังเดิม: $savedBackgroundId")
            return try {
                ContextCompat.getDrawable(context, savedBackgroundId)
            } catch (e: Exception) {
                Log.e("ProfileBackgroundManager", "Error loading saved background: ${e.message}")
                // Fallback to mountain background
                ContextCompat.getDrawable(context, R.drawable.bg_profile_mountain_animated)
            }
        } catch (e: Exception) {
            Log.e("ProfileBackgroundManager", "Error in getBackgroundForUser: ${e.message}")
            // Ultimate fallback
            return try {
                ContextCompat.getDrawable(context, R.drawable.bg_profile_mountain_animated)
            } catch (e2: Exception) {
                Log.e("ProfileBackgroundManager", "Error loading fallback background: ${e2.message}")
                null
            }
        }
    }
    
    /**
     * บันทึกพื้นหลังสำหรับผู้ใช้
     */
    private fun saveBackgroundForUser(userId: String, backgroundId: Int) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putInt(KEY_BACKGROUND_ID, backgroundId)
            .apply()
        Log.d("ProfileBackgroundManager", "บันทึกพื้นหลัง: $backgroundId สำหรับ user: $userId")
    }
    
    /**
     * เปลี่ยนพื้นหลังใหม่ (สุ่มใหม่)
     */
    fun changeBackground(userId: String): Drawable? {
        try {
            Log.d("ProfileBackgroundManager", "เปลี่ยนพื้นหลังสำหรับ User: $userId")
            val randomBackgroundId = backgroundDrawables.random()
            Log.d("ProfileBackgroundManager", "สุ่มได้ Background ID: $randomBackgroundId")
            saveBackgroundForUser(userId, randomBackgroundId)
            val drawable = try {
                ContextCompat.getDrawable(context, randomBackgroundId)
            } catch (e: Exception) {
                Log.e("ProfileBackgroundManager", "Error loading new background: ${e.message}")
                // Fallback to mountain background
                ContextCompat.getDrawable(context, R.drawable.bg_profile_mountain_animated)
            }
            Log.d("ProfileBackgroundManager", "ได้ Drawable: ${drawable != null}")
            return drawable
        } catch (e: Exception) {
            Log.e("ProfileBackgroundManager", "Error in changeBackground: ${e.message}")
            // Ultimate fallback
            return try {
                ContextCompat.getDrawable(context, R.drawable.bg_profile_mountain_animated)
            } catch (e2: Exception) {
                Log.e("ProfileBackgroundManager", "Error loading fallback background: ${e2.message}")
                null
            }
        }
    }
    
    /**
     * รับชื่อสถานที่จาก ID
     */
    fun getLocationName(backgroundId: Int): String {
        return when (backgroundId) {
            R.drawable.bg_profile_simple_animated -> "🎬 พื้นหลังทดสอบ (มี Animation)"
            R.drawable.bg_profile_simple -> "🎨 พื้นหลังทดสอบ (ไม่มี Animation)"
            R.drawable.bg_profile_paris -> "🗼 ปารีส, ฝรั่งเศส"
            R.drawable.bg_profile_tokyo -> "🗾 โตเกียว, ญี่ปุ่น"
            R.drawable.bg_profile_santorini -> "🏛️ ซานโตรินี, กรีซ"
            R.drawable.bg_profile_swiss -> "🏔️ สวิตเซอร์แลนด์"
            R.drawable.bg_profile_venice -> "🛶 เวนิส, อิตาลี"
            else -> "🌍 สถานที่ลึกลับ"
        }
    }
    
    /**
     * รับชื่อสถานที่จาก Drawable
     */
    fun getLocationNameFromDrawable(drawable: Drawable?): String {
        val savedBackgroundId = prefs.getInt(KEY_BACKGROUND_ID, -1)
        return getLocationName(savedBackgroundId)
    }
    
    /**
     * เริ่ม animation ของพื้นหลัง
     */
    fun startBackgroundAnimation(drawable: Drawable?) {
        Log.d("ProfileBackgroundManager", "พยายามเริ่ม animation: ${drawable != null}")
        if (drawable is AnimatedVectorDrawable) {
            try {
                Log.d("ProfileBackgroundManager", "เริ่ม AnimatedVectorDrawable")
                drawable.start()
                Log.d("ProfileBackgroundManager", "เริ่ม animation สำเร็จ")
            } catch (e: Exception) {
                Log.e("ProfileBackgroundManager", "เกิดข้อผิดพลาดในการเริ่ม animation: ${e.message}")
            }
        } else {
            Log.d("ProfileBackgroundManager", "ไม่ใช่ AnimatedVectorDrawable: ${drawable?.javaClass?.simpleName}")
        }
    }
    
    /**
     * หยุด animation ของพื้นหลัง
     */
    fun stopBackgroundAnimation(drawable: Drawable?) {
        if (drawable is AnimatedVectorDrawable) {
            try {
                drawable.stop()
            } catch (e: Exception) {
                // ถ้าเกิดข้อผิดพลาดในการหยุด animation ให้ข้ามไป
            }
        }
    }
} 