package com.natthasethstudio.sethpos.model

data class ReceiptData(
    val shopName: String,
    val shopAddress: String,
    val shopPhone: String,
    val shopTaxId: String?, // Optional
    val receiptNumber: String,
    val transactionDateTime: String,
    val customerName: String?, // Optional
    val items: List<ReceiptItem>,
    val subTotal: Double,
    val discount: Double,
    val taxAmount: Double,
    val totalAmount: Double,
    val paidAmount: Double,
    val changeAmount: Double,
    val footerMessage: String
)

data class ReceiptItem(
    val itemName: String,
    val quantity: Int,
    val pricePerUnit: Double,
    val totalPrice: Double
)