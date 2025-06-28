package com.natthasethstudio.model

import java.util.UUID

data class PrinterConnection(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var purpose: String, // e.g., "ใบเสร็จ", "ครัว"
    var connectionType: String, // "Bluetooth", "Network"
    var address: String, // MAC address for Bluetooth, IP address for Network
    var port: Int = 9100,
    var isEnabled: Boolean = true,
    var logoUri: String? = null // URI โลโก้ร้าน (ถ้ามี)
) 