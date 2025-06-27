package com.natthasethstudio

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.natthasethstudio.model.PrinterConnection

class PrinterRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAllPrinters(): MutableList<PrinterConnection> {
        val json = prefs.getString(KEY_PRINTERS, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<PrinterConnection>>() {}.type
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }
    }

    fun savePrinters(printers: List<PrinterConnection>) {
        val json = gson.toJson(printers)
        prefs.edit().putString(KEY_PRINTERS, json).apply()
    }

    fun addPrinter(printer: PrinterConnection) {
        val printers = getAllPrinters()
        printers.add(printer)
        savePrinters(printers)
    }

    fun updatePrinter(printer: PrinterConnection) {
        val printers = getAllPrinters()
        val index = printers.indexOfFirst { it.id == printer.id }
        if (index != -1) {
            printers[index] = printer
            savePrinters(printers)
        }
    }

    fun deletePrinter(printerId: String) {
        val printers = getAllPrinters()
        printers.removeAll { it.id == printerId }
        savePrinters(printers)
    }

    companion object {
        private const val PREFS_NAME = "printer_connections_prefs"
        private const val KEY_PRINTERS = "printer_list"
    }
} 