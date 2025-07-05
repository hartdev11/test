# แก้ไข Smart Cast Error

## ปัญหา
หลังจากเปลี่ยน property ใน data class Post จาก `val` เป็น `var` แล้วเกิด smart cast error เพราะ Kotlin ไม่อนุญาต smart cast กับ mutable property

## การแก้ไข

### 1. CreatePostActivity.kt
```kotlin
// เดิม
if (!it.postImageUrl.isNullOrEmpty()) {
    Glide.with(this).load(it.postImageUrl).into(imageViewPreview)
    imageUri = it.postImageUrl.toUri()
}

// ใหม่
val imageUrl = it.postImageUrl
if (!imageUrl.isNullOrEmpty()) {
    Glide.with(this).load(imageUrl).into(imageViewPreview)
    imageUri = imageUrl.toUri()
}
```

### 2. FeedAdapter.kt
```kotlin
// เดิม
if (!post.postText.isNullOrEmpty()) {
    holder.postContent.text = post.postText
}

// ใหม่
val postText = post.postText
if (!postText.isNullOrEmpty()) {
    holder.postContent.text = postText
}
```

```kotlin
// เดิม
if (!post.postImageUrl.isNullOrEmpty()) {
    loadPostImage(holder.postImage, post.postImageUrl)
}

// ใหม่
val imageUrl = post.postImageUrl
if (!imageUrl.isNullOrEmpty()) {
    loadPostImage(holder.postImage, imageUrl)
}
```

## หลักการแก้ไข
- ใช้ local variable เก็บค่า property ก่อน
- ใช้ local variable แทน smart cast
- ตัวอย่าง: `val imageUrl = post.postImageUrl` แล้วใช้ `imageUrl` แทน `post.postImageUrl`

## ผลลัพธ์
- ไม่มี smart cast error อีกต่อไป
- โค้ดยังคงทำงานเหมือนเดิม
- สามารถแก้ไขค่า property ใน Post ได้ 