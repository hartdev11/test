package com.natthasethstudio.sethpos

data class Comment(
    val userId: String? = null,
    val displayName: String? = null,
    val profileImageUrl: String? = null,
    val commentText: String? = null,
    val commentTime: String? = null,
    val nickname: String? = null,
    val avatarId: Int? = null
)