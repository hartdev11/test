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

class PrinterManager(private val context: Context) {
    private val repository = PrinterRepository(context)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    fun printReceipt(
        storeName: String,
        storeAddress: String,
        storePhone: String,
        items: List<CartItem>,
        totalAmount: Double,
        cashReceived: Double,
        change: Double,
        purpose: String // e.g., "ใบเสร็จ"
    ) {
        val printersToUse = repository.getAllPrinters().filter { it.isEnabled && it.purpose.equals(purpose, ignoreCase = true) }

        if (printersToUse.isEmpty()) {
            // Optionally, show a toast or log that no printer is configured for this purpose
            return
        }
        
        val formattedText = "[C]<b>$storeName</b>\n" +
                "[C]$storeAddress\n" +
                "[C]$storePhone\n" +
                "[C]${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n" +
                "[C]--------------------------------\n" +
                "[L]รายการ[L]จำนวน[R]ราคา\n" +
                "[C]--------------------------------\n" +
                items.joinToString("\n") { item ->
                    "[L]${item.name}[L]${item.quantity}[R]${String.format("%.2f", item.price * item.quantity)}"
                } + "\n" +
                "[C]--------------------------------\n" +
                "[L]รวมทั้งหมด[R]${String.format("%.2f", totalAmount)}\n" +
                "[L]รับเงิน[R]${String.format("%.2f", cashReceived)}\n" +
                "[L]เงินทอน[R]${String.format("%.2f", change)}\n" +
                "[C]--------------------------------\n" +
                "[C]ขอบคุณที่ใช้บริการ\n" +
                "[C]--------------------------------\n\n\n"

        for (printerConfig in printersToUse) {
            try {
                val printer = when (printerConfig.connectionType) {
                    "Bluetooth" -> createBluetoothPrinter(printerConfig)
                    "Network" -> createNetworkPrinter(printerConfig)
                    else -> null
                }
                printer?.printFormattedTextAndCut(formattedText)
                printer?.disconnectPrinter()
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle individual printer failure
            }
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
            val printer = when (config.connectionType) {
                "Bluetooth" -> createBluetoothPrinter(config)
                "Network" -> createNetworkPrinter(config)
                else -> throw IllegalArgumentException("Unsupported printer type")
            }

            val testText = "[C]================================\n" +
                    "[C]ทดสอบการเชื่อมต่อ\n" +
                    "[C]================================\n\n" +
                    "[L]ชื่อ: ${config.name}\n" +
                    "[L]ประเภท: ${config.purpose}\n" +
                    "[L]การเชื่อมต่อ: ${config.connectionType}\n" +
                    "[L]ที่อยู่: ${config.address}\n" +
                    "[L]พอร์ต: ${config.port}\n" +
                    "[L]เวลา: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n\n" +
                    "[C]================================\n" +
                    "[C]การเชื่อมต่อสำเร็จ\n" +
                    "[C]================================\n\n\n\n"

            printer.printFormattedTextAndCut(testText)
            printer.disconnectPrinter()
        } catch (e: Exception) {
            throw Exception("ไม่สามารถเชื่อมต่อกับเครื่องพิมพ์ได้: ${e.message}")
        }
    }
} 