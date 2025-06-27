package com.natthasethstudio.sethpos

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommentActivity : AppCompatActivity() {

    private lateinit var toolbarComment: Toolbar
    private lateinit var recyclerViewComments: RecyclerView
    private lateinit var editTextComment: EditText
    private lateinit var buttonPostComment: Button
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<Comment>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId = auth.currentUser?.uid
    private var postId: String? = null
    private var emptyView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comment)

        // อ้างอิง View จาก Layout
        toolbarComment = findViewById(R.id.toolbarComment)
        recyclerViewComments = findViewById(R.id.recyclerViewComments)
        editTextComment = findViewById(R.id.editTextComment)
        buttonPostComment = findViewById(R.id.buttonPostComment)

        // ตั้งค่า Toolbar
        setSupportActionBar(toolbarComment)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbarComment.setNavigationOnClickListener {
            finish()
        }

        // ตั้งค่า RecyclerView
        recyclerViewComments.layoutManager = LinearLayoutManager(this)
        commentAdapter = CommentAdapter(commentList)
        recyclerViewComments.adapter = commentAdapter

        // ตั้งค่า Empty View
        setupEmptyView()

        // ตั้งค่า Post ID
        postId = intent.getStringExtra("postId")
        postId?.let { fetchComments(it) }

        // ตั้งค่า Click Listener สำหรับปุ่มโพสต์
        buttonPostComment.setOnClickListener {
            postId?.let { id ->
                val commentText = editTextComment.text.toString()
                postComment(id, commentText)
            }
        }
    }

    private fun setupEmptyView() {
        emptyView = TextView(this).apply {
            text = "ยังไม่มีความคิดเห็น"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            gravity = Gravity.CENTER
        }
    }

    private fun fetchComments(postId: String) {
        firestore.collection("posts")
            .document(postId)
            .collection("comments")
            .orderBy("commentTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("CommentActivity", "Listen failed.", e)
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดความคิดเห็น", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val oldList = ArrayList(commentList)
                    commentList.clear()
                    
                    for (document in snapshot.documents) {
                        val comment = document.toObject(Comment::class.java)
                        comment?.let {
                            commentList.add(it)
                        }
                    }

                    // Update UI efficiently
                    if (commentList.isEmpty()) {
                        showEmptyView()
                    } else {
                        hideEmptyView()
                        updateRecyclerView(oldList, commentList)
                    }
                }
            }
    }

    private fun updateRecyclerView(oldList: List<Comment>, newList: List<Comment>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldComment = oldList[oldItemPosition]
                val newComment = newList[newItemPosition]
                return oldComment.commentTime == newComment.commentTime && 
                       oldComment.userId == newComment.userId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldComment = oldList[oldItemPosition]
                val newComment = newList[newItemPosition]
                return oldComment == newComment
            }
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        diffResult.dispatchUpdatesTo(commentAdapter)
    }

    private fun showEmptyView() {
        recyclerViewComments.visibility = View.GONE
        emptyView?.let { view ->
            if (view.parent == null) {
                (recyclerViewComments.parent as? ViewGroup)?.addView(view)
            }
        }
    }

    private fun hideEmptyView() {
        recyclerViewComments.visibility = View.VISIBLE
        emptyView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    private fun postComment(postId: String, commentText: String) {
        if (commentText.trim().isEmpty()) {
            Toast.makeText(this, "กรุณากรอกความคิดเห็น", Toast.LENGTH_SHORT).show()
            return
        }
        buttonPostComment.isEnabled = false // ปิดปุ่มระหว่างส่ง
        currentUserId?.let { userId ->
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val name = documentSnapshot.getString("name")
                        val nickname = documentSnapshot.getString("nickname")
                        val avatarId = documentSnapshot.getLong("avatarId")?.toInt()
                        val commentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        val comment = Comment(
                            userId = userId,
                            displayName = name ?: "ผู้ใช้",
                            nickname = nickname,
                            avatarId = avatarId,
                            commentText = commentText.trim(),
                            commentTime = commentTime
                        )
                        editTextComment.setText("")
                        firestore.collection("posts")
                            .document(postId)
                            .collection("comments")
                            .add(comment)
                            .addOnSuccessListener {
                                // Update comment count in the main post
                                updatePostCommentCount(postId) {
                                    // หลังอัปเดต ดึงจำนวนคอมเมนต์จริงจาก Firestore
                                    firestore.collection("posts").document(postId).collection("comments").get()
                                        .addOnSuccessListener { snapshot ->
                                            val count = snapshot.size()
                                            firestore.collection("posts").document(postId).update("commentCount", count)
                                        }
                                    buttonPostComment.isEnabled = true // เปิดปุ่มอีกครั้ง
                                }
                                createCommentNotification(postId, userId, nickname ?: name ?: "ผู้ใช้", avatarId ?: 0)
                            }
                            .addOnFailureListener { e ->
                                Log.w("CommentActivity", "Error posting comment: ", e)
                                Toast.makeText(this, "เกิดข้อผิดพลาดในการแสดงความคิดเห็น", Toast.LENGTH_SHORT).show()
                                buttonPostComment.isEnabled = true
                            }
                    } else {
                        Toast.makeText(this, "ไม่พบข้อมูลผู้ใช้", Toast.LENGTH_SHORT).show()
                        buttonPostComment.isEnabled = true
                    }
                }
                .addOnFailureListener { e ->
                    Log.w("CommentActivity", "Error fetching user data for comment: ", e)
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการดึงข้อมูลผู้ใช้", Toast.LENGTH_SHORT).show()
                    buttonPostComment.isEnabled = true
                }
        } ?: run {
            Toast.makeText(this, "กรุณาเข้าสู่ระบบ", Toast.LENGTH_SHORT).show()
            buttonPostComment.isEnabled = true
        }
    }

    private fun updatePostCommentCount(postId: String, onComplete: (() -> Unit)? = null) {
        val postRef = firestore.collection("posts").document(postId)
        postRef.update("commentCount", com.google.firebase.firestore.FieldValue.increment(1))
            .addOnFailureListener { e ->
                Log.w("CommentActivity", "Error updating comment count: ", e)
            }
            .addOnCompleteListener { onComplete?.invoke() }
    }

    private fun createCommentNotification(postId: String, commenterId: String, commenterName: String, commenterAvatarId: Int) {
        // Get post owner ID
        firestore.collection("posts").document(postId).get()
            .addOnSuccessListener { postDoc ->
                val postOwnerId = postDoc.getString("userId")
                if (postOwnerId != null && postOwnerId != commenterId) {
                    val notification = com.natthasethstudio.sethpos.model.Notification(
                        recipientId = postOwnerId,
                        senderId = commenterId,
                        senderName = commenterName,
                        senderAvatarId = commenterAvatarId,
                        type = "comment",
                        message = "$commenterName ได้แสดงความคิดเห็นในโพสต์ของคุณ",
                        postId = postId,
                        timestamp = com.google.firebase.Timestamp.now(),
                        read = false
                    )
                    
                    firestore.collection("notifications")
                        .add(notification)
                        .addOnFailureListener { e ->
                            Log.w("CommentActivity", "Error creating notification: ", e)
                        }
                }
            }
    }
}