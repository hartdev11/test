package com.natthasethstudio.sethpos

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natthasethstudio.sethpos.model.MenuDataItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.natthasethstudio.PrinterRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CartActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var rvCartItems: RecyclerView
    private lateinit var tvTotalPrice: TextView
    private lateinit var btnPrintReceipt: Button
    private lateinit var tvCartEmpty: TextView
    private lateinit var rgOrderType: RadioGroup
    private lateinit var etNote: EditText

    private val cartItems = mutableListOf<MenuDataItem>()
    private var totalPrice = 0.0

    private lateinit var adapter: CartAdapter
    private lateinit var printerRepository: PrinterRepository

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        toolbar = findViewById(R.id.toolbarCart)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setHomeAsUpIndicator(android.R.drawable.ic_menu_revert)
        }

        rvCartItems = findViewById(R.id.rvCartItems)
        tvTotalPrice = findViewById(R.id.tvTotalPrice)
        btnPrintReceipt = findViewById(R.id.btnPrintReceipt)
        tvCartEmpty = findViewById(R.id.tvCartEmpty)
        rgOrderType = findViewById(R.id.rgOrderType)
        etNote = findViewById(R.id.etNote)

        printerRepository = PrinterRepository(this)
        rvCartItems.layoutManager = LinearLayoutManager(this)

        val receivedCart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("cart_items", MenuDataItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<MenuDataItem>("cart_items")
        }

        if (receivedCart != null && receivedCart.isNotEmpty()) {
            cartItems.addAll(receivedCart)
            tvCartEmpty.visibility = View.GONE
            rvCartItems.visibility = View.VISIBLE
            btnPrintReceipt.isEnabled = true
        } else {
            tvCartEmpty.visibility = View.VISIBLE
            rvCartItems.visibility = View.GONE
            btnPrintReceipt.isEnabled = false
        }

        adapter = CartAdapter(cartItems, { _, _ ->
            calculateTotalPrice()
        }, { position ->
            // Handle item deletion
            cartItems.removeAt(position)
            adapter.notifyItemRemoved(position)
            adapter.notifyItemRangeChanged(position, cartItems.size) // To update positions
            calculateTotalPrice()
            checkIfCartIsEmpty()
        })
        rvCartItems.adapter = adapter

        calculateTotalPrice()

        btnPrintReceipt.setOnClickListener {
            checkPrintersBeforePrinting()
        }
    }

    private fun checkPrintersBeforePrinting() {
        if (printerRepository.getAllPrinters().isNotEmpty()) {
            // มีเครื่องพิมพ์, ทำการบันทึกและพิมพ์
            saveOrderAndPrint()
        } else {
            // ไม่มีเครื่องพิมพ์, แสดง dialog ถาม
            showNoPrinterDialog()
        }
    }

    private fun showNoPrinterDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("ยังไม่ได้ตั้งค่าเครื่องพิมพ์")
            .setMessage("คุณต้องการพิมพ์ใบเสร็จหรือไม่?")
            .setPositiveButton("พิมพ์") { _, _ ->
                // ผู้ใช้ต้องการพิมพ์, ไปหน้าตั้งค่า
                val intent = Intent(this, PrinterSettingsActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("ไม่พิมพ์") { _, _ ->
                // ผู้ใช้ไม่ต้องการพิมพ์, แค่บันทึกออเดอร์แล้วจบ
                saveOrderAndFinish()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveOrderAndPrint() {
        // ดึงข้อมูลและ validate
        val selectedOrderTypeId = rgOrderType.checkedRadioButtonId
        if (selectedOrderTypeId == -1) {
            Toast.makeText(this, "กรุณาเลือกประเภทการสั่ง", Toast.LENGTH_SHORT).show()
            return
        }
        val orderType = findViewById<RadioButton>(selectedOrderTypeId).text.toString()
        val note = etNote.text.toString()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "ผู้ใช้ยังไม่ได้เข้าสู่ระบบ", Toast.LENGTH_SHORT).show()
            return
        }

        // บันทึกและพิมพ์
        saveOrderToFirebase(currentUser.uid, orderType, note)
        printReceipt(currentUser.uid, orderType, note)
    }
    
    private fun saveOrderAndFinish() {
        // ดึงข้อมูลและ validate
        val selectedOrderTypeId = rgOrderType.checkedRadioButtonId
        if (selectedOrderTypeId == -1) {
            Toast.makeText(this, "กรุณาเลือกประเภทการสั่ง", Toast.LENGTH_SHORT).show()
            return
        }
        val orderType = findViewById<RadioButton>(selectedOrderTypeId).text.toString()
        val note = etNote.text.toString()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "ผู้ใช้ยังไม่ได้เข้าสู่ระบบ", Toast.LENGTH_SHORT).show()
            return
        }

        // บันทึกอย่างเดียว
        saveOrderToFirebase(currentUser.uid, orderType, note)
        Toast.makeText(this, "บันทึกออเดอร์เรียบร้อย", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun calculateTotalPrice() {
        totalPrice = cartItems.sumOf { item ->
            val quantity = adapter.getQuantities().getOrElse(cartItems.indexOf(item)) { 1 }
            item.price * quantity
        }
        tvTotalPrice.text = String.format("%.2f", totalPrice)
    }

    private fun saveOrderToFirebase(storeId: String, orderType: String, note: String) {
        val orderData = hashMapOf(
            "storeId" to storeId,
            "orderType" to orderType,
            "note" to note,
            "items" to cartItems.map { item ->
                val quantity = adapter.getQuantities().getOrElse(cartItems.indexOf(item)) { 1 }
                hashMapOf(
                    "name" to item.name,
                    "price" to item.price,
                    "quantity" to quantity
                )
            },
            "totalAmount" to totalPrice,
            "timestamp" to Timestamp.now(),
            "status" to "completed"
        )

        db.collection("orders")
            .add(orderData)
            .addOnSuccessListener { documentReference ->
                Log.d("CartActivity", "Order saved with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("CartActivity", "Error saving order", e)
            }
    }

    private fun printReceipt(storeId: String, orderType: String, note: String) {
        // ดึงข้อมูลร้านค้า
        db.collection("stores").document(storeId).get()
            .addOnSuccessListener { storeDoc ->
                if (storeDoc.exists()) {
                    val storeName = storeDoc.getString("storeName") ?: "ร้านค้า"
                    val storeAddress = storeDoc.getString("address") ?: "-"
                    val storePhone = storeDoc.getString("phone") ?: "-"

                    // สร้างข้อมูลใบเสร็จ
                    val receiptItems = cartItems.map { item ->
                        val quantity = adapter.getQuantities()[cartItems.indexOf(item)] ?: 1
                        CartItem(
                            name = item.name,
                            quantity = quantity,
                            price = item.price
                        )
                    }

                    // พิมพ์ใบเสร็จใน CoroutineScope
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val printerManager = PrinterManager(this@CartActivity)
                            printerManager.printReceipt(
                                storeName = storeName,
                                storeAddress = storeAddress,
                                storePhone = storePhone,
                                items = receiptItems,
                                totalAmount = totalPrice,
                                cashReceived = totalPrice,
                                change = 0.0,
                                purpose = "ใบเสร็จ"
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@CartActivity, "พิมพ์ใบเสร็จเรียบร้อย", Toast.LENGTH_SHORT).show()
                                // เคลียร์ตระกร้าและกลับไปหน้าเมนู
                                cartItems.clear()
                                adapter.notifyDataSetChanged()
                                setResult(RESULT_OK)
                                finish()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@CartActivity, "เกิดข้อผิดพลาดในการพิมพ์ใบเสร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                                // ถ้าเกิด Exception ให้เคลียร์ตระกร้าและกลับไปหน้าเมนูเลย
                                cartItems.clear()
                                adapter.notifyDataSetChanged()
                                setResult(RESULT_OK)
                                finish()
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "เกิดข้อผิดพลาดในการดึงข้อมูลร้านค้า: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateReceiptNumber(): String {
        val timestamp = System.currentTimeMillis()
        return "RCP${timestamp}"
    }

    private fun checkIfCartIsEmpty() {
        if (cartItems.isEmpty()) {
            tvCartEmpty.visibility = View.VISIBLE
            rvCartItems.visibility = View.GONE
            btnPrintReceipt.isEnabled = false
        }
    }

    // CartAdapter class (inner or separate file)
    class CartAdapter(
        private val items: MutableList<MenuDataItem>,
        private val onQuantityChanged: (Int, Int) -> Unit,
        private val onItemDeleted: (Int) -> Unit
    ) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

        private val quantities = mutableMapOf<Int, Int>()

        init {
            items.forEachIndexed { index, _ -> quantities[index] = 1 }
        }

        fun getQuantities(): Map<Int, Int> = quantities

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false)
            return CartViewHolder(view)
        }

        override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
            val item = items[position]
            holder.tvItemName.text = item.name
            holder.tvItemPrice.text = String.format("%.2f", item.price)
            holder.tvQuantity.text = quantities[position].toString()

            holder.btnDecreaseQuantity.setOnClickListener {
                val currentQuantity = quantities[position] ?: 1
                if (currentQuantity > 1) {
                    quantities[position] = currentQuantity - 1
                    holder.tvQuantity.text = (currentQuantity - 1).toString()
                    onQuantityChanged(position, currentQuantity - 1)
                }
            }

            holder.btnIncreaseQuantity.setOnClickListener {
                val currentQuantity = quantities[position] ?: 1
                quantities[position] = currentQuantity + 1
                holder.tvQuantity.text = (currentQuantity + 1).toString()
                onQuantityChanged(position, currentQuantity + 1)
            }
            
            holder.btnDeleteItem.setOnClickListener {
                onItemDeleted(position)
            }
        }

        override fun getItemCount(): Int = items.size

        class CartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvItemName: TextView = itemView.findViewById(R.id.tvItemName)
            val tvItemPrice: TextView = itemView.findViewById(R.id.tvItemPrice)
            val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
            val btnDecreaseQuantity: Button = itemView.findViewById(R.id.btnMinus)
            val btnIncreaseQuantity: Button = itemView.findViewById(R.id.btnPlus)
            val btnDeleteItem: ImageButton = itemView.findViewById(R.id.btnDeleteItem)
        }
    }
}
