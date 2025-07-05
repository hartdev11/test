# การแก้ไขปัญหาการแสดงผลสัตว์ที่สลับกัน

## ปัญหา
- กดไลค์ → ไม่มีหัวใจขึ้น (ควรมีหัวใจ)
- กดยกเลิกไลค์ → มีหัวใจขึ้น (ควรเป็นอิโมจิเศร้า)

## สาเหตุ
ปัญหาคือ `post.isLiked` ยังไม่ได้ถูกอัพเดทในตอนที่เรียก `onLikeClickListener.invoke(post, position)` เพราะการอัพเดท UI แบบ optimistic เกิดขึ้นใน `CustomerMainActivity` หลังจากที่ `onLikeClickListener` ถูกเรียกแล้ว

## การแก้ไข

### 1. เก็บสถานะปัจจุบันก่อนเรียก onLikeClickListener
```kotlin
holder.btnLike.setOnClickListener {
    if (!isAnimating) {
        // ... animation code ...
        
        // เก็บสถานะปัจจุบันก่อนที่จะส่งไปยัง onLikeClickListener
        val currentLikeState = post.isLiked
        val willBeLiked = !currentLikeState
        
        Log.d("FeedAdapter", "Like button clicked - current state: $currentLikeState, will be: $willBeLiked")
        
        onLikeClickListener.invoke(post, position)
        
        // ส่ง interaction type ที่ถูกต้อง
        val interactionType = if (willBeLiked) "like" else "unlike"
        Log.d("FeedAdapter", "Sending animal interaction: $interactionType")
        onAnimalInteraction?.invoke(interactionType)
    }
}
```

### 2. เพิ่ม debug log ใน CustomerMainActivity
```kotlin
private fun handleAnimalInteraction(interactionType: String) {
    val animatedAnimalsView = binding.animatedAnimalsView
    Log.d("CustomerMainActivity", "Received animal interaction: $interactionType")
    when (interactionType) {
        "like" -> {
            Log.d("CustomerMainActivity", "Calling onLikePressed")
            animatedAnimalsView?.onLikePressed()
        }
        "unlike" -> {
            Log.d("CustomerMainActivity", "Calling onLikeUnpressed")
            animatedAnimalsView?.onLikeUnpressed()
        }
        // ... other cases ...
    }
}
```

## ผลลัพธ์
- กดไลค์ → แสดงหัวใจ (onLikePressed → showHeartAnimation)
- กดยกเลิกไลค์ → แสดงอิโมจิเศร้า (onLikeUnpressed → showSadFaceAnimation)

## การทดสอบ
1. กดไลค์โพสต์ → ตรวจสอบว่าสัตว์แสดงหัวใจ
2. กดยกเลิกไลค์ → ตรวจสอบว่าสัตว์แสดงอิโมจิเศร้า
3. ตรวจสอบ debug log เพื่อยืนยันว่าส่ง interaction type ที่ถูกต้อง

## Debug Log ที่คาดหวัง
```
FeedAdapter: Like button clicked - current state: false, will be: true
FeedAdapter: Sending animal interaction: like
CustomerMainActivity: Received animal interaction: like
CustomerMainActivity: Calling onLikePressed
```

```
FeedAdapter: Like button clicked - current state: true, will be: false
FeedAdapter: Sending animal interaction: unlike
CustomerMainActivity: Received animal interaction: unlike
CustomerMainActivity: Calling onLikeUnpressed
``` 