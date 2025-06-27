package com.natthasethstudio.sethpos

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
@Keep
data class Post(
    @get:JvmName("getUserId")
    val userId: String? = null,
    val displayName: String? = null,
    val profileImageUrl: String? = null,
    val postTime: String? = null,
    val postImageUrl: String? = null,
    val postText: String? = null,
    var storyImageUrl: String? = null, // เพิ่ม Field สำหรับ URL ของ Story Preview
    var hasMoment: Boolean = false, // เพิ่ม Field สำหรับระบุว่าโพสต์นี้มี "Join Moment" หรือไม่
    var joinMomentUserAvatars: List<String> = emptyList(), // เพิ่มรายชื่อ URL รูปโปรไฟล์ของผู้ที่เข้าร่วม Moment
    var likeCount: Int = 0,
    var commentCount: Int = 0,
    var isLiked: Boolean = false,
    var postId: String? = null,
    var avatarId: Int = 0, // เพิ่ม Field สำหรับเก็บ ID ของ avatar ที่เลือก
    var nickname: String? = null, // Change nickname to var
    @get:JvmName("getIsPremiumUser")
    var isPremiumUser: Boolean = false, // เพิ่มฟิลด์สำหรับระบุว่าเป็นสมาชิกพรีเมียมหรือไม่
    var postImages: List<String> = emptyList(), // เพิ่มฟิลด์สำหรับเก็บรายการรูปภาพในโพสต์
    @get:JvmName("getIsBoosted")
    var isBoosted: Boolean = false,  // เพิ่มสถานะการบูสต์
    var boostCount: Int = 0,  // เพิ่มจำนวนการบูสต์
    var province: String? = null,
    var processingLike: Boolean? = null,
    var likes: List<String>? = null
)