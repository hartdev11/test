package com.natthasethstudio.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.natthasethstudio.sethpos.R
import com.natthasethstudio.model.PrinterConnection

class PrinterAdapter(
    private var printers: MutableList<PrinterConnection>,
    private val onAction: (PrinterConnection, String) -> Unit
) : RecyclerView.Adapter<PrinterAdapter.PrinterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrinterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_printer_connection, parent, false)
        return PrinterViewHolder(view)
    }

    override fun onBindViewHolder(holder: PrinterViewHolder, position: Int) {
        val printer = printers[position]
        holder.bind(printer)
    }

    override fun getItemCount(): Int = printers.size

    fun updateData(newPrinters: List<PrinterConnection>) {
        this.printers.clear()
        this.printers.addAll(newPrinters)
        notifyDataSetChanged()
    }

    inner class PrinterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.printerName)
        private val infoTextView: TextView = itemView.findViewById(R.id.printerInfo)
        private val iconImageView: ImageView = itemView.findViewById(R.id.printerIcon)
        private val enableSwitch: SwitchMaterial = itemView.findViewById(R.id.enableSwitch)
        private val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)
        private val testButton: View = itemView.findViewById(R.id.testButton)

        fun bind(printer: PrinterConnection) {
            nameTextView.text = printer.name
            val info = "${printer.purpose} (${printer.connectionType}: ${printer.address})"
            infoTextView.text = info
            enableSwitch.isChecked = printer.isEnabled

            val iconRes = when (printer.connectionType) {
                "Network" -> R.drawable.ic_store
                "Bluetooth" -> R.drawable.ic_shop
                else -> R.drawable.ic_shop
            }
            iconImageView.setImageResource(iconRes)


            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (printer.isEnabled != isChecked) {
                    onAction(printer, if (isChecked) "enable" else "disable")
                }
            }
            
            testButton.setOnClickListener {
                onAction(printer, "test")
            }

            menuButton.setOnClickListener {
                showPopupMenu(it, printer)
            }
        }
        
        private fun showPopupMenu(view: View, printer: PrinterConnection) {
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.printer_options_menu) // Use the new menu
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit_printer -> {
                        onAction(printer, "edit")
                        true
                    }
                    R.id.action_delete_printer -> {
                        onAction(printer, "delete")
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
} 