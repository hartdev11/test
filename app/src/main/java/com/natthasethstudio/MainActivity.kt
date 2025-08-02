package com.natthasethstudio.sethpos

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natthasethstudio.sethpos.adapter.MenuAdapter
import com.natthasethstudio.sethpos.model.MenuDataItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.firestore.DocumentReference
import android.app.AlertDialog
import android.content.Context
import android.widget.CheckBox
import android.widget.Button

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var menuAdapter: MenuAdapter
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var errorView: TextView
    private lateinit var retryButton: MaterialButton

    private lateinit var rvMenuItems: RecyclerView
    private lateinit var tvTotalPrice: TextView
    private lateinit var tvCartItemCount: TextView
    private lateinit var btnCheckout: MaterialButton
    private lateinit var chipGroupCategories: ChipGroup
    private lateinit var searchEditText: TextInputEditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var fabAddMenu: View

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentUserRef: DocumentReference? = null

    private val cartLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.clearCart()
                Toast.makeText(this, "ล้างตะกร้าเรียบร้อย", Toast.LENGTH_SHORT).show()
            }
        }

    private val addMenuLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.loadCategories()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // ตรวจสอบสิทธิ์ผู้ใช้
        auth.currentUser?.let { user ->
            firestore.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val role = document.getString("role") ?: "customer"
                        if (role != "merchant") {
                            // ถ้าเป็นลูกค้า ให้ redirect ไปที่หน้าฟีด
                            val intent = Intent(this, CustomerMainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                            return@addOnSuccessListener
                        }
                    }
                }
        }

        val repository = MenuRepository()
        val viewModelFactory = MainViewModelFactory(repository)
        viewModel = ViewModelProvider(this, viewModelFactory).get(MainViewModel::class.java)

        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        auth.currentUser?.let { user ->
            currentUserRef = firestore.collection("users").document(user.uid)
            checkSubscriptionStatus()
        }

        initializeViews()
        setupRecyclerView()
        setupSwipeRefresh()
        setupSearchBar()
        setupChipGroup()
        setupCheckoutButton()
        setupErrorHandling()
        observeViewModel()

        fabAddMenu.setOnClickListener {
            val intent = Intent(this, AddMenuActivity::class.java)
            addMenuLauncher.launch(intent)
        }
    }

    private fun initializeViews() {
        rvMenuItems = findViewById(R.id.rvMenuItems)
        tvTotalPrice = findViewById(R.id.tvTotalPrice)
        tvCartItemCount = findViewById(R.id.tvCartItemCount)
        btnCheckout = findViewById(R.id.btnCheckout)
        chipGroupCategories = findViewById(R.id.chipGroupCategories)
        searchEditText = findViewById(R.id.searchEditText)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressIndicator = findViewById(R.id.progressIndicator)
        errorView = findViewById(R.id.errorView)
        retryButton = findViewById(R.id.retryButton)
        fabAddMenu = findViewById(R.id.fabAddMenu)
    }

    private fun setupRecyclerView() {
        try {
            rvMenuItems.layoutManager = GridLayoutManager(this, 2)
            menuAdapter = MenuAdapter(mutableListOf()) { selectedItem ->
                try {
                    addToCartWithAnimation(selectedItem)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error adding item to cart: ${e.message}")
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการเพิ่มสินค้า", Toast.LENGTH_SHORT).show()
                }
            }
            rvMenuItems.adapter = menuAdapter
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up RecyclerView: ${e.message}")
            Toast.makeText(this, "เกิดข้อผิดพลาดในการแสดงสินค้า", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwipeRefresh() {
        try {
            swipeRefresh.setOnRefreshListener {
                if (isNetworkAvailable()) {
                    viewModel.loadCategories()
                    viewModel.loadMenuItems(null)
                } else {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(this, "ไม่มีการเชื่อมต่ออินเทอร์เน็ต", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up SwipeRefresh: ${e.message}")
        }
    }

    private fun setupSearchBar() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.filterMenuItems(s.toString())
            }
        })
    }

    private fun setupChipGroup() {
        viewModel.categories.observe(this, Observer { categories ->
            chipGroupCategories.removeAllViews()
            
            // Add "All" chip
            val allChip = createChip("ทั้งหมด")
            allChip.isChecked = true
            chipGroupCategories.addView(allChip)
            
            // Add category chips
            categories.forEach { category ->
                chipGroupCategories.addView(createChip(category))
            }
        })

        chipGroupCategories.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val selectedChip = group.findViewById<Chip>(checkedIds[0])
                if (selectedChip != null) {
                    val category = selectedChip.text.toString()
                    if (category == "ทั้งหมด") {
                        viewModel.loadMenuItems(null)
                    } else {
                        viewModel.loadMenuItems(category)
                    }
                }
            }
        }
    }

    private fun setupCheckoutButton() {
        try {
            btnCheckout.setOnClickListener {
                val currentCartItems = viewModel.cartItems.value
                if (currentCartItems.isNullOrEmpty()) {
                    Toast.makeText(this, "ยังไม่มีสินค้าในตะกร้า", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val intent = Intent(this, CartActivity::class.java)
                        intent.putParcelableArrayListExtra("cart_items", ArrayList(currentCartItems))
                        cartLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error launching CartActivity: ${e.message}")
                        Toast.makeText(this, "เกิดข้อผิดพลาดในการเปิดตะกร้าสินค้า", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up checkout button: ${e.message}")
        }
    }

    private fun createChip(text: String): Chip {
        return Chip(this).apply {
            this.text = text
            isCheckable = true
            isClickable = true
        }
    }

    private fun setupErrorHandling() {
        retryButton.setOnClickListener {
            if (isNetworkAvailable()) {
                viewModel.loadCategories()
                viewModel.loadMenuItems(null)
                showLoading()
            } else {
                showError("ไม่มีการเชื่อมต่ออินเทอร์เน็ต")
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }

    }

    private fun showLoading() {
        progressIndicator.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        retryButton.visibility = View.GONE
        rvMenuItems.visibility = View.GONE
        fabAddMenu.visibility = View.GONE
    }

    private fun showError(message: String) {
        progressIndicator.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        retryButton.visibility = View.VISIBLE
        rvMenuItems.visibility = View.GONE
        fabAddMenu.visibility = View.GONE
        errorView.text = message
    }

    private fun showContent() {
        progressIndicator.visibility = View.GONE
        errorView.visibility = View.GONE
        retryButton.visibility = View.GONE
        fabAddMenu.visibility = View.VISIBLE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                rvMenuItems.layoutManager = GridLayoutManager(this, 3)
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                rvMenuItems.layoutManager = GridLayoutManager(this, 2)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.menuItems.observe(this, Observer { items ->
            menuAdapter.updateItems(items)
            // ไม่ต้องแสดง layoutEmptyMenu อีกต่อไป
            rvMenuItems.visibility = if (items.isNullOrEmpty()) View.GONE else View.VISIBLE
            showContent()
        })

        viewModel.isLoading.observe(this, Observer { isLoading ->
            swipeRefresh.isRefreshing = isLoading
            if (isLoading) {
                showLoading()
            }
        })

        viewModel.error.observe(this, Observer { error ->
            if (!error.isNullOrEmpty()) {
                showError(error)
                Snackbar.make(findViewById(android.R.id.content), error, Snackbar.LENGTH_LONG).show()
            }
        })

        viewModel.cartItems.observe(this, Observer { items ->
            btnCheckout.isEnabled = items.isNotEmpty()
            tvCartItemCount.text = "${items.size} รายการ"
        })

        viewModel.totalPrice.observe(this, Observer { total ->
            tvTotalPrice.text = getString(R.string.total_price, total)
        })
    }

    private fun addToCartWithAnimation(item: MenuDataItem) {
        val view = rvMenuItems.findViewHolderForAdapterPosition(menuAdapter.getPosition(item))?.itemView
        if (view != null) {
            val animation = AnimationUtils.loadAnimation(this, R.anim.add_to_cart)
            view.startAnimation(animation)
            viewModel.addToCart(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        
        // The subscription menu item is now defined in menu_main.xml
        // No need to add it programmatically here.
        // menu.add(Menu.NONE, R.id.action_subscription, Menu.NONE, "สมัครสมาชิกพรีเมียม")
        // .setIcon(R.drawable.ic_premium_crown)
        // .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add -> {
                val intent = Intent(this, AddMenuActivity::class.java)
                addMenuLauncher.launch(intent)
                true
            }
            R.id.action_subscription -> {
                Log.d("MainActivity", "Premium icon clicked. Showing premium dialog.")
                showPremiumDialog()
                true
            }
            R.id.menu_profile -> {
                val intent = Intent(this, StoreProfileActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_create_post -> {
                val intent = Intent(this, CreatePostActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.menu_printer_settings -> {
                val intent = Intent(this, PrinterSettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.menu_logout -> {
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkSubscriptionStatus() {
        currentUserRef?.get()?.addOnSuccessListener { document ->
            if (document != null) {
                val isPremium = document.getBoolean("isPremium") ?: false
                val role = document.getString("role") ?: "customer"
                
                // ถ้าเป็นร้านค้าและไม่ใช่พรีเมียม ให้แสดง dialog
                if (role == "merchant" && !isPremium) {
                    showPremiumDialog()
                }
            }
        }
    }

    private fun showPremiumDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_premium_upgrade, null)
        val checkBox = dialogView.findViewById<CheckBox>(R.id.checkBoxDontShowToday)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.buttonUpgrade).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, SubscriptionActivity::class.java)
            startActivity(intent)
        }

        dialogView.findViewById<Button>(R.id.buttonNotNow).setOnClickListener {
            dialog.dismiss()
            // บันทึกการตั้งค่าไม่แสดง dialog วันนี้
            if (checkBox.isChecked) {
                val sharedPrefs = getSharedPreferences("premium_dialog", Context.MODE_PRIVATE)
                val editor = sharedPrefs.edit()
                editor.putLong("last_dismiss_time", System.currentTimeMillis())
                editor.apply()
            }
        }

        dialog.show()
    }
}

class MainViewModelFactory(private val repository: MenuRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

