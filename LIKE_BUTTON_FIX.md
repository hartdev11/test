# แก้ไขปัญหาปุ่มไลค์เด้งไปด้านบน

## ปัญหา
ปุ่มไลค์เด้งไปด้านบนสุดของโพสต์

## การแก้ไข
1. เปลี่ยน notifyDataSetChanged() เป็น notifyItemChanged()
2. ลบ syncLikeStatusFromFirestore() ใน onResume()
3. เพิ่ม boostCount และ isBoosted ใน DiffCallback
4. ปรับการตั้งค่า RecyclerView 