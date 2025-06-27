# ปกป้องคลาส Firebase, Google Play, Glide ฯลฯ ที่จำเป็นต้องไม่ Obfuscate

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Firebase Messaging
-keep class com.google.firebase.messaging.FirebaseMessagingService { *; }


# Firestore
-keep class com.google.firestore.v1.** { *; }

# Firebase Authentication
-keep class com.google.firebase.auth.** { *; }

# Glide (จำเป็นสำหรับแสดงรูป)
-keep class com.bumptech.glide.** { *; }
-keep interface com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# ZXing (QR code)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# DantSu ESCPOS Thermal Printer
-keep class com.dantsu.escpos.** { *; }
-dontwarn com.dantsu.escpos.**

# Google Play Billing
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Lifecycle / ViewModel
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# Navigation
-keep class androidx.navigation.** { *; }

# สำหรับ Compose (ป้องกันไม่ให้บางฟีเจอร์ UI พัง)
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# AppCompat / Material
-keep class androidx.appcompat.** { *; }
-keep class com.google.android.material.** { *; }

# ป้องกัน ViewBinding
-keep class **.databinding.* { *; }
-keep class **.viewbinding.* { *; }

# ปกป้องคลาสภายในแอปของคุณเองไม่ให้ชื่อถูก obfuscate
-keep class com.natthasethstudio.sethpos.** { *; }
