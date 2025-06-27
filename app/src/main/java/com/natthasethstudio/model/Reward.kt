package com.natthasethstudio.sethpos.model

import java.util.Date

data class Reward(
    val id: String,
    val title: String,
    val description: String,
    val requiredStreak: Int,
    val spins: Int,
    var isAvailable: Boolean = false,
    var isClaimed: Boolean = false,
    val claimedAt: Date? = null,
    val expiresAt: Date? = null
) 