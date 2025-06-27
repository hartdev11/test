package com.natthasethstudio.sethpos

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natthasethstudio.sethpos.adapter.MenuAdapter
import com.natthasethstudio.sethpos.model.MenuDataItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.util.Log

class MenuActivity : AppCompatActivity() {

    private lateinit var rvMenuItems: RecyclerView
    private lateinit var menuAdapter: MenuAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        rvMenuItems = findViewById(R.id.rvMenuItems)
        rvMenuItems.layoutManager = GridLayoutManager(this, 2)

        menuAdapter = MenuAdapter(mutableListOf()) { selectedItem ->
            // Handle item click
            Toast.makeText(this, "Selected: ${selectedItem.name}", Toast.LENGTH_SHORT).show()
        }
        rvMenuItems.adapter = menuAdapter

        loadMenuItems()
    }

    private var menuListener: ListenerRegistration? = null

    private fun loadMenuItems() {
        val storeId = auth.currentUser?.uid ?: return
        Log.d("MenuActivity", "Loading menu items for storeId: $storeId")

        menuListener?.remove()
        menuListener = db.collection("menu_items")
            .whereEqualTo("storeId", storeId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("MenuActivity", "Error loading menu: ${e.message}")
                    Toast.makeText(this, "Error loading menu: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshot?.let { docs ->
                    Log.d("MenuActivity", "Received ${docs.size()} menu items")
                    val menuItems = docs.mapNotNull { doc ->
                        Log.d("MenuActivity", "Processing doc: ${doc.id}")
                        val name = doc.getString("name") ?: return@mapNotNull null
                        val price = doc.getDouble("price") ?: return@mapNotNull null
                        val imageUrl = doc.getString("imageUrl")
                        val category = doc.getString("category") ?: ""
                        val menuItem = MenuDataItem(name, price, imageUrl, storeId, category)
                        Log.d("MenuActivity", "Added menu item: $menuItem")
                        menuItem
                    }
                    menuAdapter.updateItems(menuItems)
                    Log.d("MenuActivity", "Updated menu items count: ${menuItems.size}")
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MenuActivity", "Removing menu listener")
        menuListener?.remove()
    }
}
