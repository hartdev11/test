# แก้ไขปัญหาปุ่มไลค์เด้งไปด้านบน - เวอร์ชัน 2

## ปัญหาที่เหลือ
แม้จะแก้ไขแล้ว แต่ปุ่มไลค์ยังคงเด้งไปด้านบนเมื่อกดซ้ำ

## การแก้ไขเพิ่มเติม

### 1. สร้างฟังก์ชัน updatePostsListWithoutScroll
```kotlin
private fun updatePostsListWithoutScroll(posts: List<Post>, paginate: Boolean) {
    // ซ่อน loading indicator
    feedAdapter.setLoading(false)
    
    if (paginate) {
        postList.addAll(posts)
    } else {
        postList.clear()
        postList.addAll(posts)
    }
    
    feedAdapter.submitList(postList.toList())
    isLoading = false
    swipeRefreshLayout.isRefreshing = false
    showContent()
    
    // ไม่ scroll ไปด้านบน เพื่อป้องกันการเด้งเมื่อมีการเปลี่ยนแปลงข้อมูล
}
```

### 2. แก้ไข realtime listener ให้ใช้ updatePostsListWithoutScroll
```kotlin
// เรียงลำดับและอัพเดท UI โดยไม่ทำให้ scroll ไปด้านบน
updatePostsListWithoutScroll(fetchedPosts, paginate = false)
```

### 3. แก้ไข boost listener ให้ notifyItemChanged ถูกต้อง
```kotlin
// อัพเดท UI โดยไม่ทำให้ scroll ไปด้านบน
val position = postList.indexOf(post)
if (position != -1) {
    feedAdapter.notifyItemChanged(position + 1) // +1 เพราะมี header
}
```

### 4. ปรับ DiffCallback ให้ไม่รวมข้อมูลที่เปลี่ยนแปลงบ่อย
```kotlin
override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
    // เปรียบเทียบเฉพาะข้อมูลที่สำคัญ ไม่รวมข้อมูลที่เปลี่ยนแปลงบ่อย
    return oldItem.postId == newItem.postId &&
           oldItem.postText == newItem.postText &&
           oldItem.postImageUrl == newItem.postImageUrl &&
           oldItem.nickname == newItem.nickname &&
           oldItem.displayName == newItem.displayName &&
           oldItem.postTime == newItem.postTime
           // ไม่รวม likeCount, isLiked, commentCount, boostCount, isBoosted 
           // เพื่อป้องกันการเด้งไปด้านบนเมื่อมีการเปลี่ยนแปลงข้อมูลเหล่านี้
}
```

### 5. เพิ่มการตั้งค่า RecyclerView
```kotlin
// ป้องกันการ scroll ไปด้านบนเมื่อมีการเปลี่ยนแปลงข้อมูล
recyclerViewFeed.overScrollMode = View.OVER_SCROLL_NEVER
```

## ผลลัพธ์ที่คาดหวัง
- ปุ่มไลค์จะไม่เด้งไปด้านบนอีกต่อไป แม้จะกดซ้ำ
- การอัพเดท UI จะเป็นแบบเฉพาะเจาะจงและไม่รบกวนตำแหน่ง scroll
- ประสบการณ์ผู้ใช้ดีขึ้นอย่างมาก 