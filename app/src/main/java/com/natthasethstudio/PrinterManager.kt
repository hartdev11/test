package com.natthasethstudio.sethpos

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.tcp.TcpConnection
import com.natthasethstudio.PrinterRepository
import com.natthasethstudio.sethpos.CartItem
import com.natthasethstudio.model.PrinterConnection
import java.text.SimpleDateFormat
import java.util.*
import java.nio.charset.Charset
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream
import android.util.Base64
import java.io.ByteArrayOutputStream

class PrinterManager(private val context: Context) {
    private val repository = PrinterRepository(context)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    // --- ReceiptLine & Align ---
    private data class ReceiptLine(val text: String, val align: Align = Align.LEFT, val size: Float = 26f, val bold: Boolean = false)
    private enum class Align { LEFT, CENTER, RIGHT }

    // --- Receipt Layout Builder ---
    private fun buildStandardRestaurantReceiptLines(
        storeName: String,
        storeAddress: String,
        storePhone: String,
        items: List<CartItem>,
        totalAmount: Double,
        cashReceived: Double,
        change: Double,
        tableOrNote: String?,
        dateTime: String
    ): List<ReceiptLine> {
        val lines = mutableListOf<ReceiptLine>()
        
        // หัวใบเสร็จ - Luxury Restaurant Style
        lines.add(ReceiptLine("=".repeat(32), Align.CENTER, 28f, true)) // เส้นแบ่งยาวเต็ม
        if (storeName.isNotBlank()) lines.add(ReceiptLine(storeName, Align.CENTER, 28f, true))
        if (storeAddress.isNotBlank() && storeAddress != "-") lines.add(ReceiptLine(storeAddress, Align.CENTER, 22f, false))
        if (storePhone.isNotBlank() && storePhone != "-") lines.add(ReceiptLine(storePhone, Align.CENTER, 22f, false))
        lines.add(ReceiptLine("=".repeat(32), Align.CENTER, 28f, true)) // เส้นแบ่งยาวเต็ม
        
        // ข้อมูลการสั่งซื้อ
        if (!tableOrNote.isNullOrEmpty()) {
            lines.add(ReceiptLine("โต๊ะ: $tableOrNote", Align.CENTER, 24f, true))
        }
        lines.add(ReceiptLine("วันที่: $dateTime", Align.CENTER, 22f, false))
        lines.add(ReceiptLine("", Align.CENTER, 22f, false)) // บรรทัดว่าง
        
        // หัวข้อรายการอาหาร
        lines.add(ReceiptLine("รายการอาหาร", Align.CENTER, 28f, true))
        lines.add(ReceiptLine("-".repeat(32), Align.CENTER, 22f, true)) // เส้นแบ่งสั้น
        
        // หัวตาราง
        val headerFormat = "%-20s %4s %8s %8s"
        lines.add(ReceiptLine(String.format(headerFormat, "รายการ", "จำนวน", "ราคา", "รวม"), Align.LEFT, 22f, true))
        lines.add(ReceiptLine("-".repeat(32), Align.CENTER, 22f, true)) // เส้นแบ่งสั้น
        
        // รายการสินค้า
        for (item in items) {
            val itemName = if (item.name.length > 18) item.name.substring(0, 15) + "..." else item.name
            val itemLine = String.format("%-20s %4d %8.0f %8.0f", itemName, item.quantity, item.price, item.price * item.quantity)
            lines.add(ReceiptLine(itemLine, Align.LEFT, 22f, false))
        }
        
        lines.add(ReceiptLine("-".repeat(32), Align.CENTER, 22f, true)) // เส้นแบ่งสั้น
        
        // สรุปยอด
        val totalFormat = "%-32s %8.0f"
        lines.add(ReceiptLine(String.format(totalFormat, "รวมเงิน", totalAmount), Align.RIGHT, 24f, true))
        lines.add(ReceiptLine(String.format(totalFormat, "รับเงิน", cashReceived), Align.RIGHT, 24f, true))
        lines.add(ReceiptLine(String.format(totalFormat, "เงินทอน", change), Align.RIGHT, 24f, true))
        
        lines.add(ReceiptLine("", Align.CENTER, 22f, false)) // บรรทัดว่าง
        
        // ขอบคุณ
        lines.add(ReceiptLine("ขอบคุณที่ใช้บริการ", Align.CENTER, 28f, true))
        lines.add(ReceiptLine("กรุณาแวะมาใหม่", Align.CENTER, 22f, false))
        lines.add(ReceiptLine("=".repeat(32), Align.CENTER, 28f, true)) // เส้นแบ่งยาวเต็ม
        
        return lines
    }

    // --- ปรับ textToBitmap ให้รองรับ ReceiptLine ---
    private fun textLinesToBitmap(lines: List<ReceiptLine>): Bitmap {
        val width = 384
        val paint = android.graphics.Paint()
        paint.isAntiAlias = true
        paint.isSubpixelText = true
        val lineSpacing = 18
        val paddingX = 16
        val paddingY = 24
        fun getTypeface(bold: Boolean): android.graphics.Typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        // 1. คำนวณความสูงที่ต้องใช้จริง
        var totalHeight = paddingY
        val lineHeights = mutableListOf<Int>()
        for (line in lines) {
            val size = when {
                line.size >= 28f -> 28f
                line.size >= 24f -> 24f
                else -> 22f
            }
            paint.textSize = size
            paint.typeface = getTypeface(line.bold)
            val h = (paint.fontMetrics.bottom - paint.fontMetrics.top + lineSpacing).toInt()
            lineHeights.add(h)
            totalHeight += h
        }
        totalHeight += paddingY
        // 2. สร้าง bitmap ด้วยความสูงที่คำนวณได้จริง
        val bmp = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = paddingY
        for ((i, line) in lines.withIndex()) {
            val size = when {
                line.size >= 28f -> 28f
                line.size >= 24f -> 24f
                else -> 22f
            }
            paint.textSize = size
            paint.typeface = getTypeface(line.bold)
            val textWidth = paint.measureText(line.text)
            val x = when (line.align) {
                Align.LEFT -> paddingX.toFloat()
                Align.CENTER -> ((width - textWidth) / 2f)
                Align.RIGHT -> (width - textWidth - paddingX)
            }
            canvas.drawText(line.text, x, y - paint.fontMetrics.ascent, paint)
            y += lineHeights[i]
        }
        return bmp
    }

    // --- ปรับ printReceipt ให้ใช้ layout ใหม่ ---
    fun printReceipt(
        storeName: String,
        storeAddress: String,
        storePhone: String,
        items: List<CartItem>,
        totalAmount: Double,
        cashReceived: Double,
        change: Double,
        purpose: String, // e.g., "ใบเสร็จ"
        tableOrNote: String = ""
    ) {
        val printersToUse = repository.getAllPrinters().filter { it.isEnabled && it.purpose.equals(purpose, ignoreCase = true) }
        if (printersToUse.isEmpty()) {
            android.util.Log.d("PrinterManager", "No printer found for purpose: $purpose")
            return
        }
        val now = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date())
        val receiptLines = buildStandardRestaurantReceiptLines(
            storeName = storeName,
            storeAddress = storeAddress,
            storePhone = storePhone,
            items = items,
            totalAmount = totalAmount,
            cashReceived = cashReceived,
            change = change,
            tableOrNote = tableOrNote,
            dateTime = now
        )
        for (printerConfig in printersToUse) {
            try {
                if (printerConfig.connectionType == "Network" || printerConfig.connectionType == "Ethernet") {
                    val logoBytes = printerConfig.logoUri?.let { uriStr ->
                        try {
                            val uri = Uri.parse(uriStr)
                            val bitmap = getBitmapFromUri(uri)
                            bitmap?.let { bmp -> escposBitmapBytes(bmp) }
                        } catch (e: Exception) { null }
                    }
                    val cutPaper = byteArrayOf(0x1D, 0x56, 0x00)
                    val bmp = textLinesToBitmap(receiptLines)
                    val bmpBytes = escposBitmapBytes(bmp)
                    val output = mutableListOf<Byte>()
                    logoBytes?.let { output.addAll(it.toList()) }
                    output.addAll(bmpBytes.toList())
                    output.addAll(cutPaper.toList())
                    sendRawBytesToPrinter(printerConfig.address, printerConfig.port, output.toByteArray())
                } else if (printerConfig.connectionType == "Bluetooth") {
                    val printer = createBluetoothPrinter(printerConfig)
                    try {
                        val text = receiptLines.joinToString("\n") { it.text }
                        printer.printFormattedTextAndCut(text)
                    } catch (e: Exception) {
                        android.util.Log.e("PrinterManager", "Bluetooth text print failed, fallback to bitmap not supported in this library: "+e.message)
                        printer.disconnectPrinter()
                        return
                    }
                    printer.disconnectPrinter()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendRawTextToPrinter(ip: String, port: Int, text: String) {
        try {
            val socket = java.net.Socket(ip, port)
            val out = socket.getOutputStream()
            // ใช้ UTF-8 encoding
            out.write(text.toByteArray(Charsets.UTF_8))
            // เพิ่มคำสั่งตัดกระดาษ (ESC/POS cut paper)
            val cutPaper = "\u001D\u0056\u0000"
            out.write(cutPaper.toByteArray(Charsets.UTF_8))
            out.flush()
            out.close()
            socket.close()
        } catch (e: Exception) {
            throw Exception("RAW Print Error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun createBluetoothPrinter(config: PrinterConnection): EscPosPrinter {
        val bluetoothDevices = bluetoothAdapter?.bondedDevices
        val bluetoothDevice = bluetoothDevices?.firstOrNull { it.address == config.address }
            ?: throw Exception("ไม่พบเครื่องพิมพ์ Bluetooth ที่จับคู่ไว้สำหรับที่อยู่: ${config.address}")

        val connection = BluetoothConnection(bluetoothDevice)
        return EscPosPrinter(connection, 203, 48f, 32)
    }

    private fun createNetworkPrinter(config: PrinterConnection): EscPosPrinter {
        val connection = TcpConnection(config.address, config.port)
        return EscPosPrinter(connection, 203, 48f, 32)
    }

    fun printTestPage(config: PrinterConnection) {
        try {
            if (config.connectionType == "Network" || config.connectionType == "Ethernet") {
                val testText = "*** RAW TEST PAGE ***\n\n\n"
                sendRawTextToPrinter(config.address, config.port, testText)
            } else if (config.connectionType == "Bluetooth") {
                val printer = createBluetoothPrinter(config)
                val testText = "*** RAW TEST PAGE ***\n\n\n"
                printer.printFormattedTextAndCut(testText)
                printer.disconnectPrinter()
            }
        } catch (e: Exception) {
            throw Exception("ไม่สามารถเชื่อมต่อกับเครื่องพิมพ์ได้: ${e.message}")
        }
    }

    // --- ฟังก์ชันช่วย ---
    private fun centerText(text: String, width: Int): String {
        val pad = (width - text.length) / 2
        return " ".repeat(pad.coerceAtLeast(0)) + text
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) { null }
    }

    // แปลง Bitmap เป็น ESC/POS bytes (bitmap mode)
    private fun escposBitmapBytes(bitmap: Bitmap): ByteArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, 384, (bitmap.height * (384.0 / bitmap.width)).toInt(), true)
        val width = scaled.width
        val height = scaled.height
        val bytes = mutableListOf<Byte>()
        // คำสั่ง ESC * m nL nH d1...dk
        for (y in 0 until height step 24) {
            bytes.add(0x1B)
            bytes.add(0x2A)
            bytes.add(33) // mode 24-dot
            bytes.add((width % 256).toByte())
            bytes.add((width / 256).toByte())
            for (x in 0 until width) {
                for (k in 0 until 3) {
                    var slice: Byte = 0
                    for (b in 0 until 8) {
                        val yy = y + k * 8 + b
                        val pixel = if (yy < height) scaled.getPixel(x, yy) else 0xFFFFFF
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b2 = pixel and 0xFF
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * b2).toInt()
                        if (luminance < 128) {
                            slice = (slice.toInt() or (1 shl (7 - b))).toByte()
                        }
                    }
                    bytes.add(slice)
                }
            }
            bytes.add(0x0A) // new line
        }
        return bytes.toByteArray()
    }

    private fun sendRawBytesToPrinter(ip: String, port: Int, bytes: ByteArray) {
        try {
            val socket = java.net.Socket(ip, port)
            val out = socket.getOutputStream()
            out.write(bytes)
            out.flush()
            out.close()
            socket.close()
        } catch (e: Exception) {
            throw Exception("RAW Print Error: ${e.message}")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    // ฟังก์ชันแปลงข้อความเป็น Bitmap (รองรับภาษาไทย)
    private fun textToBitmap(text: String): Bitmap {
        val paint = android.graphics.Paint()
        paint.textSize = 22f // ลดขนาดฟอนต์
        paint.isAntiAlias = true
        paint.color = android.graphics.Color.BLACK
        paint.typeface = android.graphics.Typeface.create("TH Sarabun New", android.graphics.Typeface.NORMAL)
        val width = 384 // ความกว้างสำหรับ 58mm
        val lineSpacing = 12 // เพิ่มระยะห่างระหว่างบรรทัด
        val lines = mutableListOf<String>()
        // wrap ข้อความอัตโนมัติ
        for (rawLine in text.split("\n")) {
            var line = rawLine
            while (paint.measureText(line) > width) {
                // ตัดบรรทัดอัตโนมัติ
                var cutIndex = line.length - 1
                while (cutIndex > 0 && paint.measureText(line.substring(0, cutIndex)) > width) {
                    cutIndex--
                }
                lines.add(line.substring(0, cutIndex))
                line = line.substring(cutIndex)
            }
            lines.add(line)
        }
        val lineHeight = (paint.fontMetrics.bottom - paint.fontMetrics.top + lineSpacing).toInt()
        val height = lineHeight * lines.size + 20
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = -paint.fontMetrics.ascent + 10
        for (line in lines) {
            canvas.drawText(line, 0f, y, paint)
            y += lineHeight
        }
        return bmp
    }

    // ฟังก์ชันจัดรูปแบบใบเสร็จแบบ Loyverse POS
    private fun buildLoyverseStyleReceiptText(
        storeName: String,
        storeAddress: String,
        storePhone: String,
        items: List<CartItem>,
        totalAmount: Double,
        cashReceived: Double,
        change: Double,
        dateTime: String
    ): String {
        val sb = StringBuilder()
        if (storeName.isNotBlank()) sb.appendLine(centerText(storeName, 32))
        if (storeAddress.isNotBlank() && storeAddress != "-") sb.appendLine(centerText(storeAddress, 32))
        if (storePhone.isNotBlank() && storePhone != "-") sb.appendLine(centerText(storePhone, 32))
        sb.appendLine("--------------------------------")
        sb.appendLine(centerText("บิล", 32))
        sb.appendLine()
        sb.appendLine("รายการออเดอร์: B1")
        sb.appendLine("พนักงาน: เจ้าของ")
        sb.appendLine("ระบบขายหน้าร้าน: POS 1|pad8")
        sb.appendLine("--------------------------------")
        sb.appendLine(centerText("เสิร์ฟในร้าน", 32))
        sb.appendLine("--------------------------------")
        // รายการสินค้า
        for (item in items) {
            sb.appendLine(centerText(item.name, 32))
            sb.appendLine(centerText("${item.quantity} x ${"%.2f".format(item.price)}    ฿${"%.2f".format(item.price * item.quantity)}", 32))
        }
        sb.appendLine("--------------------------------")
        sb.appendLine(centerText("จำนวนเงินค้างชำระ    ฿${"%.2f".format(totalAmount)}", 32))
        sb.appendLine()
        sb.appendLine(centerText("ขอบคุณที่อุดหนุนครับ", 32))
        sb.appendLine(centerText(dateTime, 32))
        return sb.toString()
    }
} 