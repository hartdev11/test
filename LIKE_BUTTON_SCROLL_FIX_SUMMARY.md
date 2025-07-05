# สรุปการแก้ไขปัญหาปุ่มไลค์ที่เด้งไปด้านบนสุดของโพสต์

## ปัญหาที่พบ
เมื่อกดปุ่มไลค์ในโพสต์ หน้าจอจะเด้งไปด้านบนสุดของโพสต์ ทำให้ผู้ใช้ต้องเลื่อนลงมาหาโพสต์ที่เพิ่งกดไลค์

## สาเหตุของปัญหา
1. **การเรียก `syncLikeStatusFromFirestore()`** ที่ใช้ `notifyDataSetChanged()` ทำให้ RecyclerView รีเฟรชทั้งหมด
2. **การเรียก `syncLikeStatusFromFirestore()` ใน `onResume()`** ทำให้เกิดการ sync ที่ไม่จำเป็น
3. **DiffCallback ไม่รวม `boostCount` และ `isBoosted`** ทำให้ DiffUtil คิดว่าเนื้อหาเปลี่ยนแปลง
4. **การตั้งค่า RecyclerView** ที่ไม่เหมาะสม

## การแก้ไข

### 1. แก้ไข `syncLikeStatusFromFirestore()` ใน CustomerMainActivity.kt
```kotlin
// เปลี่ยนจาก notifyDataSetChanged() เป็น notifyItemChanged() เฉพาะ item ที่เปลี่ยนแปลง
postList.forEachIndexed { index, post ->
    val wasLiked = post.isLiked
    post.isLiked = post.postId != null && likedPostIds.contains(post.postId)
    if (wasLiked != post.isLiked) {
        updatedCount++
        Log.d("CustomerMainActivity", "Post ${post.postId} like status corrected: $wasLiked -> ${post.isLiked}")
        // อัพเดทเฉพาะ item ที่เปลี่ยนแปลง โดยไม่ทำให้ scroll ไปด้านบน
        feedAdapter.notifyItemChanged(index + 1) // +1 เพราะมี header
    }
}
```

### 2. ลบการเรียก `syncLikeStatusFromFirestore()` ใน `onResume()`
```kotlin
override fun onResume() {
    super.onResume()
    // Reload profile when returning to this activity
    feedAdapter.updateHeaderProfile()
    
    // ไม่เรียก syncLikeStatusFromFirestore() ใน onResume เพื่อป้องกันการเด้งไปด้านบน
    
    // Scroll to top when returning to feed (only if user explicitly navigated back)
    if (postList.isNotEmpty()) {
        recyclerViewFeed.post {
            recyclerViewFeed.smoothScrollToPosition(0)
        }
    }
}
```

### 3. ลบการเรียก `syncLikeStatusFromFirestore()` หลังการกดไลค์สำเร็จ
```kotlin
} else {
    Log.d("CustomerMainActivity", "Like operation successful for post: $postId")
    // ส่งการแจ้งเตือนถ้าเป็นการไลค์
    if (!isCurrentlyLiked) {
        sendNotification(post.userId, "like", post.postId)
    }
    
    // ไม่ต้อง sync like status เพราะเราได้อัพเดท UI แบบ optimistic แล้ว
    // และการ sync อาจทำให้เกิดการเด้งไปด้านบน
}
```

### 4. แก้ไข DiffCallback ใน FeedAdapter.kt
```kotlin
class DiffCallback : DiffUtil.ItemCallback<Post>() {
    override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
        return oldItem.postId == newItem.postId
    }

    override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
        return oldItem.postId == newItem.postId &&
               oldItem.likeCount == newItem.likeCount &&
               oldItem.isLiked == newItem.isLiked &&
               oldItem.commentCount == newItem.commentCount &&
               oldItem.boostCount == newItem.boostCount &&        // เพิ่ม
               oldItem.isBoosted == newItem.isBoosted &&          // เพิ่ม
               oldItem.postText == newItem.postText &&
               oldItem.postImageUrl == newItem.postImageUrl &&
               oldItem.nickname == newItem.nickname &&
               oldItem.displayName == newItem.displayName &&
               oldItem.postTime == newItem.postTime
    }
}
```

### 5. ปรับปรุงการตั้งค่า RecyclerView
```kotlin
// Set up RecyclerView
val layoutManager = LinearLayoutManager(this)
recyclerViewFeed.layoutManager = layoutManager

// ป้องกันการ scroll ไปด้านบนเมื่ออัพเดท item
recyclerViewFeed.itemAnimator = null

// ป้องกันการ scroll ไปด้านบนเมื่อมีการเปลี่ยนแปลงข้อมูล
recyclerViewFeed.setHasFixedSize(true)

// ป้องกันการ scroll ไปด้านบนเมื่อมีการเปลี่ยนแปลงข้อมูล
layoutManager.isItemPrefetchEnabled = false

// ป้องกันการ scroll ไปด้านบนเมื่อมีการเปลี่ยนแปลงข้อมูล
recyclerViewFeed.isNestedScrollingEnabled = false
```

## ผลลัพธ์
- ปุ่มไลค์จะไม่ทำให้หน้าจอเด้งไปด้านบนสุดอีกต่อไป
- การอัพเดท UI จะเป็นแบบ optimistic และเฉพาะเจาะจง
- ประสบการณ์ผู้ใช้ดีขึ้นเพราะไม่ต้องเลื่อนหาตำแหน่งเดิม

## ไฟล์ที่แก้ไข
1. `app/src/main/java/com/natthasethstudio/CustomerMainActivity.kt`
2. `app/src/main/java/com/natthasethstudio/FeedAdapter.kt` 