package com.natthasethstudio.sethpos

data class PrinterSettings(
    val printerType: String,
    val printerPort: String,
    val printerIp: String,
    val printerPortNumber: String,
    val paperWidth: Float = 48f,  // ความกว้างกระดาษเป็นมิลลิเมตร
    val paperDpi: Int = 203,      // ความละเอียดของเครื่องพิมพ์
    val charsPerLine: Int = 32    // จำนวนตัวอักษรต่อบรรทัด
) 