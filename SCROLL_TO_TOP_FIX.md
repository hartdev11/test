# การแก้ไขปัญหาการ Scroll ไปด้านบนเมื่อเปิดหน้าฟีด

## ปัญหา
เมื่อเปิดหน้าฟีด หน้าจอไม่ได้เริ่มต้นที่ด้านบนสุด แต่มาอยู่ที่กลางๆ ของหน้าฟีด ทำให้ต้องเลื่อนขึ้นไปดูโพสต์ข้างบน

## สาเหตุ
1. การเรียก `updatePostsListWithoutScroll` ไม่ได้ scroll ไปด้านบนเมื่อโหลดข้อมูลครั้งแรก
2. การเรียก `refreshFeed()` ใน `onStart()` และ `onResume()` ทำให้มีการ scroll ซ้ำซ้อน
3. การ scroll ใน `refreshFeed()` ทำให้เกิดการ scroll ซ้ำซ้อนกับ `updatePostsListWithoutScroll`

## การแก้ไข

### 1. แก้ไข `updatePostsListWithoutScroll` ใน `CustomerMainActivity.kt`
```kotlin
private fun updatePostsListWithoutScroll(posts: List<Post>, paginate: Boolean) {
    // ... existing code ...
    
    if (hasSignificantChanges) {
        postList.clear()
        postList.addAll(posts)
        feedAdapter.submitList(postList.toList())
        
        // Scroll to top when loading new posts (not pagination)
        if (posts.isNotEmpty()) {
            recyclerViewFeed.post {
                recyclerViewFeed.smoothScrollToPosition(0)
            }
        }
    }
    
    // ... existing code ...
}
```

### 2. ลบการ scroll ใน `onStart()`
```kotlin
override fun onStart() {
    super.onStart()
    auth.addAuthStateListener(authStateListener)
    
    // Refresh feed on start to get latest data
    // แต่ไม่ต้อง refresh ถ้ามีโพสต์อยู่แล้ว
    if (postList.isEmpty()) {
        refreshFeed()
    }
    // ไม่ scroll ไปด้านบนใน onStart เพื่อป้องกันการเด้งไปด้านบน
}
```

### 3. ลบการ scroll ใน `onResume()`
```kotlin
override fun onResume() {
    super.onResume()
    // Reload profile when returning to this activity
    feedAdapter.updateHeaderProfile()
    
    // Sync like status from Firestore to ensure consistency (only if needed)
    // ไม่เรียก syncLikeStatusFromFirestore() ใน onResume เพื่อป้องกันการเด้งไปด้านบน
    
    // ไม่ scroll ไปด้านบนใน onResume เพื่อป้องกันการเด้งไปด้านบนเมื่อกลับมาที่หน้าฟีด
}
```

### 4. ลบการ scroll ใน `refreshFeed()`
```kotlin
private fun refreshFeed() {
    Log.d("CustomerMainActivity", "Refreshing feed...")
    isLastPage = false
    currentPage = 0
    
    // Reset realtime listener
    resetRealtimeListener()
    
    // Fetch posts with current filter - use currentFilterState from FeedAdapter
    val filter = if (feedAdapter.currentFilterState) "all" else "nearMe"
    
    fetchPostsOptimized(filter, paginate = false, provinceFilter = currentProvince)
    
    // ไม่ต้อง scroll ไปด้านบนที่นี่ เพราะ updatePostsListWithoutScroll จะจัดการแล้ว
}
```

## ผลลัพธ์
- ตอนเปิดหน้าฟีดครั้งแรก จะเริ่มต้นที่ด้านบนสุด
- ไม่มีการ scroll ซ้ำซ้อน
- การกดไลค์ไม่ทำให้เด้งไปด้านบน
- การ refresh ฟีดจะ scroll ไปด้านบนอย่างถูกต้อง

## การทดสอบ
1. เปิดแอปและเข้าไปที่หน้าฟีด
2. ตรวจสอบว่าหน้าจอเริ่มต้นที่ด้านบนสุด
3. กดไลค์โพสต์หลายๆ ครั้ง ตรวจสอบว่าไม่เด้งไปด้านบน
4. Pull to refresh ตรวจสอบว่าหน้าจอ scroll ไปด้านบน
5. ออกจากแอปและกลับมา ตรวจสอบว่าหน้าจอยังอยู่ที่ตำแหน่งเดิม 