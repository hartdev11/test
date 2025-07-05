# การแก้ไขปัญหาการกดไลค์ซ้ำไม่ได้

## ปัญหา
หลังจากกดไลค์แล้ว ถ้าจะกดยกเลิกไลค์ทันทีจะกดไม่ได้ ต้องไปกดปุ่มอื่นในโพสต์เดียวกันก่อนถึงจะกดยกเลิกได้

## สาเหตุ
1. `processingLikePostIds` ใน `FeedAdapter` เป็น `Set<String>` ที่ถูกส่งมาจาก `CustomerMainActivity`
2. เมื่อ `CustomerMainActivity` อัพเดท `processingLikePostIds` แล้ว `FeedAdapter` ไม่ได้รับข้อมูลที่อัพเดท
3. การ disable/enable ปุ่มไลค์ใน `FeedAdapter` ขึ้นอยู่กับ `processingLikePostIds` แต่ข้อมูลไม่ sync

## การแก้ไข

### 1. เปลี่ยน `processingLikePostIds` ใน `FeedAdapter` เป็น `MutableSet<String>`
```kotlin
class FeedAdapter(
    private var currentUserId: String?,
    private val processingLikePostIds: MutableSet<String>, // เปลี่ยนจาก Set<String> เป็น MutableSet<String>
    // ... other parameters
)
```

### 2. เพิ่ม method สำหรับอัพเดท like button state ใน `FeedAdapter`
```kotlin
// Function to update like button state for a specific post
fun updateLikeButtonStateForPost(postId: String) {
    val position = currentList.indexOfFirst { it.postId == postId }
    if (position != -1) {
        notifyItemChanged(position + 1) // +1 because of header
    }
}
```

### 3. เรียกใช้ method นี้ใน `CustomerMainActivity` หลังจากอัพเดท `processingLikePostIds`
```kotlin
batch.commit().addOnCompleteListener {
    processingLikePostIds.remove(postId)
    
    // อัพเดท like button state ใน FeedAdapter
    feedAdapter.updateLikeButtonStateForPost(postId)
    
    // ... rest of the code
}
```

### 4. อัพเดท like button state ใน error handling ด้วย
```kotlin
if (!it.isSuccessful) {
    // ... error handling code ...
    feedAdapter.notifyItemChanged(postIndex + 1)
    // อัพเดท like button state ใน FeedAdapter
    feedAdapter.updateLikeButtonStateForPost(postId)
    Toast.makeText(this, "เกิดข้อผิดพลาดในการกดไลค์", Toast.LENGTH_SHORT).show()
}
```

## ผลลัพธ์
- สามารถกดไลค์และยกเลิกไลค์ได้ทันทีโดยไม่ต้องกดปุ่มอื่นก่อน
- ปุ่มไลค์จะถูก enable/disable อย่างถูกต้องตามสถานะการประมวลผล
- ไม่มีการ delay หรือการรอให้กดปุ่มอื่นก่อน

## การทดสอบ
1. กดไลค์โพสต์
2. กดยกเลิกไลค์ทันที (ควรทำงานได้)
3. กดไลค์ซ้ำหลายๆ ครั้งติดต่อกัน (ควรทำงานได้ทุกครั้ง)
4. ตรวจสอบว่าปุ่มไลค์ถูก disable ชั่วคราวระหว่างการประมวลผล
5. ตรวจสอบว่าปุ่มไลค์ถูก enable หลังจากประมวลผลเสร็จ 