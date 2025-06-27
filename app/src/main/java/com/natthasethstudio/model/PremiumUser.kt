package com.natthasethstudio.sethpos.model

import java.util.Date

data class PremiumUser(
    val userId: String = "",
    val isPremium: Boolean = false,
    val subscriptionStartDate: Date? = null,
    val subscriptionEndDate: Date? = null,
    val paymentHistory: List<Payment> = listOf()
) 