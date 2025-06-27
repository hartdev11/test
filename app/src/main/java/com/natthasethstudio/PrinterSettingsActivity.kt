package com.natthasethstudio.sethpos

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.natthasethstudio.PrinterRepository
import com.natthasethstudio.adapter.PrinterAdapter
import com.natthasethstudio.model.PrinterConnection
import com.natthasethstudio.sethpos.databinding.ActivityPrinterSettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrinterSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrinterSettingsBinding
    private lateinit var printerRepository: PrinterRepository
    private lateinit var printerAdapter: PrinterAdapter
    private var printerList = mutableListOf<PrinterConnection>()

    // For Network Discovery
    private lateinit var nsdManager: NsdManager
    private val foundServices = mutableListOf<NsdServiceInfo>()
    private var isDiscovering = false
    private lateinit var discoveryListener: NsdManager.DiscoveryListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrinterSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        printerRepository = PrinterRepository(this)
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager

        setupRecyclerView()
        loadPrinters()

        binding.addPrinterFab.setOnClickListener {
            showAddEditPrinterDialog(null)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isDiscovering) {
            stopNetworkDiscovery()
        }
    }

    private fun setupRecyclerView() {
        printerAdapter = PrinterAdapter(printerList) { printer, action ->
            when (action) {
                "edit" -> showAddEditPrinterDialog(printer)
                "delete" -> showDeleteConfirmationDialog(printer)
                "enable" -> togglePrinterState(printer, true)
                "disable" -> togglePrinterState(printer, false)
                "test" -> testPrinterConnection(printer)
            }
        }
        binding.printersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PrinterSettingsActivity)
            adapter = printerAdapter
        }
    }

    private fun loadPrinters() {
        printerList.clear()
        printerList.addAll(printerRepository.getAllPrinters())
        printerAdapter.notifyDataSetChanged()
        binding.emptyView.isVisible = printerList.isEmpty()
    }

    private fun testPrinterConnection(printer: PrinterConnection) {
        Toast.makeText(this, "กำลังทดสอบการพิมพ์กับ '${printer.name}'...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val printerManager = PrinterManager(this@PrinterSettingsActivity)
                printerManager.printTestPage(printer)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PrinterSettingsActivity, "ส่งข้อมูลทดสอบไปยัง '${printer.name}' สำเร็จ", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(this@PrinterSettingsActivity)
                        .setTitle("การทดสอบล้มเหลว")
                        .setMessage("ไม่สามารถเชื่อมต่อกับเครื่องพิมพ์ '${printer.name}'.\nข้อผิดพลาด: ${e.message}")
                        .setPositiveButton("ตกลง", null)
                        .show()
                }
            }
        }
    }

    private fun togglePrinterState(printer: PrinterConnection, isEnabled: Boolean) {
        val updatedPrinter = printer.copy(isEnabled = isEnabled)
        printerRepository.updatePrinter(updatedPrinter)
        loadPrinters()
        val status = if (isEnabled) "เปิดใช้งาน" else "ปิดใช้งาน"
        Toast.makeText(this, "เครื่องพิมพ์ '${printer.name}' $status", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteConfirmationDialog(printer: PrinterConnection) {
        MaterialAlertDialogBuilder(this)
            .setTitle("ยืนยันการลบ")
            .setMessage("คุณแน่ใจหรือไม่ว่าต้องการลบเครื่องพิมพ์ '${printer.name}'?")
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("ลบ") { _, _ ->
                printerRepository.deletePrinter(printer.id)
                loadPrinters()
                Toast.makeText(this, "ลบเครื่องพิมพ์แล้ว", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showAddEditPrinterDialog(printerToEdit: PrinterConnection?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_edit_printer, null)
        val printerNameEt = dialogView.findViewById<EditText>(R.id.etPrinterName)
        val printerPurposeEt = dialogView.findViewById<EditText>(R.id.etPrinterPurpose)
        val connectionTypeAct = dialogView.findViewById<AutoCompleteTextView>(R.id.actConnectionType)
        val printerAddressEt = dialogView.findViewById<EditText>(R.id.etPrinterAddress)
        val portTil = dialogView.findViewById<TextInputLayout>(R.id.tilPrinterPort)
        val printerPortEt = dialogView.findViewById<EditText>(R.id.etPrinterPort)
        val scanButton = dialogView.findViewById<Button>(R.id.btnScanNetwork)


        // Setup Connection Type Dropdown
        val connectionTypes = arrayOf("Bluetooth", "Network", "Ethernet")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, connectionTypes)
        connectionTypeAct.setAdapter(adapter)
        connectionTypeAct.setOnItemClickListener { _, _, position, _ ->
            val isNetwork = connectionTypes[position] == "Network" || connectionTypes[position] == "Ethernet"
            portTil.isVisible = isNetwork
            scanButton.isVisible = isNetwork
        }

        scanButton.setOnClickListener {
            startNetworkDiscovery(printerAddressEt, printerPortEt)
        }

        val dialogTitle = if (printerToEdit == null) "เพิ่มเครื่องพิมพ์ใหม่" else "แก้ไขเครื่องพิมพ์"

        printerToEdit?.let {
            printerNameEt.setText(it.name)
            printerPurposeEt.setText(it.purpose)
            connectionTypeAct.setText(it.connectionType, false)
            val isNetwork = it.connectionType == "Network" || it.connectionType == "Ethernet"
            portTil.isVisible = isNetwork
            scanButton.isVisible = isNetwork
            printerAddressEt.setText(it.address)
            printerPortEt.setText(it.port.toString())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("บันทึก") { _, _ ->
                val name = printerNameEt.text.toString().trim()
                val purpose = printerPurposeEt.text.toString().trim()
                val connectionType = connectionTypeAct.text.toString()
                val address = printerAddressEt.text.toString().trim()
                val port = printerPortEt.text.toString().toIntOrNull() ?: 9100

                if (name.isBlank() || purpose.isBlank() || connectionType.isBlank() || address.isBlank()) {
                    Toast.makeText(this, "กรุณากรอกข้อมูลให้ครบถ้วน", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newPrinter = printerToEdit?.copy(
                    name = name,
                    purpose = purpose,
                    connectionType = connectionType,
                    address = address,
                    port = port
                ) ?: PrinterConnection(
                    name = name,
                    purpose = purpose,
                    connectionType = connectionType,
                    address = address,
                    port = port
                )

                if (printerToEdit == null) {
                    printerRepository.addPrinter(newPrinter)
                } else {
                    printerRepository.updatePrinter(newPrinter)
                }
                loadPrinters()
            }
            .setOnDismissListener {
                 if (isDiscovering) stopNetworkDiscovery()
            }
            .show()
    }

    private fun initializeDiscoveryListener(addressEt: EditText, portEt: EditText) {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                isDiscovering = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (foundServices.none { it.serviceName == serviceInfo.serviceName }) {
                            foundServices.add(serviceInfo)
                            runOnUiThread {
                                showNetworkPrinterSelectionDialog(addressEt, portEt)
                            }
                        }
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                 foundServices.removeAll { it.serviceName == service.serviceName }
                 // Optionally update the dialog if it's still open
            }
            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
    }


    private fun startNetworkDiscovery(addressEt: EditText, portEt: EditText) {
        if (isDiscovering) return
        
        foundServices.clear()
        initializeDiscoveryListener(addressEt, portEt)
        nsdManager.discoverServices("_printer._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        Toast.makeText(this, "กำลังสแกนหาเครื่องพิมพ์...", Toast.LENGTH_SHORT).show()
        // Simple timeout to stop discovery
        CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(10000) // Scan for 10 seconds
            if(isDiscovering) {
                stopNetworkDiscovery()
                Toast.makeText(this@PrinterSettingsActivity, "หยุดการสแกน", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopNetworkDiscovery() {
        if (isDiscovering) {
            nsdManager.stopServiceDiscovery(discoveryListener)
            isDiscovering = false
        }
    }

     private fun showNetworkPrinterSelectionDialog(addressEt: EditText, portEt: EditText) {
        if (foundServices.isEmpty()) {
            Toast.makeText(this, "ไม่พบเครื่องพิมพ์ในเครือข่าย", Toast.LENGTH_SHORT).show()
            return
        }

        @Suppress("DEPRECATION")
        val printerList = foundServices.map { "${it.serviceName} (${it.host.hostAddress})" }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("เลือกเครื่องพิมพ์ในเครือข่าย")
            .setItems(printerList) { dialog, which ->
                val selectedPrinter = foundServices[which]
                @Suppress("DEPRECATION")
                addressEt.setText(selectedPrinter.host.hostAddress)
                portEt.setText(selectedPrinter.port.toString())
                stopNetworkDiscovery()
                dialog.dismiss()
            }
            .setNegativeButton("ยกเลิก") { _, _ -> stopNetworkDiscovery() }
            .setOnCancelListener { stopNetworkDiscovery() }
            .show()
    }
}
