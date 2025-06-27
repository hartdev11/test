package com.natthasethstudio.sethpos.model

import java.util.Date

data class Payment(
    val paymentId: String,
    val amount: Double,
    val paymentDate: Date,
    val paymentMethod: String,
    val status: String
) 