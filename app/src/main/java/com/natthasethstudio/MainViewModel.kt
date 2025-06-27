package com.natthasethstudio.sethpos

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.natthasethstudio.sethpos.model.MenuDataItem

class MainViewModel(private val menuRepository: MenuRepository) : ViewModel() {

    private val _categories = MutableLiveData<List<String>>()
    val categories: LiveData<List<String>> = _categories

    private val _menuItems = MutableLiveData<List<MenuDataItem>>()
    val menuItems: LiveData<List<MenuDataItem>> = _menuItems

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Add LiveData for cart and total price
    private val _cartItems = MutableLiveData<List<MenuDataItem>>(emptyList())
    val cartItems: LiveData<List<MenuDataItem>> = _cartItems

    private val _totalPrice = MutableLiveData<Double>()
    val totalPrice: LiveData<Double> = _totalPrice

    private val _isPremiumUser = MutableLiveData<Boolean>()
    val isPremiumUser: LiveData<Boolean> = _isPremiumUser

    private var allMenuItems: List<MenuDataItem> = emptyList()
    private var currentCategory: String? = null
    private var currentSearchQuery: String = ""

    init {
        loadCategories()
        loadMenuItems(null)
    }

    fun loadCategories() {
        _isLoading.value = true
        menuRepository.getCategories { result ->
            _isLoading.value = false
            result.onSuccess { snapshot ->
                val categoryList = snapshot.documents
                    .mapNotNull { it.getString("category") }
                    .distinct()
                    .toList()
                _categories.value = listOf("ทั้งหมด") + categoryList
            }.onFailure { error ->
                _error.value = error.message
            }
        }
    }

    fun loadMenuItems(category: String?) {
        _isLoading.value = true
        currentCategory = category
        menuRepository.getMenuItems(category) { result ->
            _isLoading.value = false
            result.onSuccess { snapshot ->
                val items = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val price = doc.getDouble("price") ?: return@mapNotNull null
                    val imageUrl = doc.getString("imageUrl")
                    val storeId = doc.getString("storeId") ?: ""
                    val category = doc.getString("category") ?: ""
                    MenuDataItem(name, price, imageUrl, storeId, category)
                }
                allMenuItems = items
                applyFilters()
            }.onFailure { error ->
                _error.value = error.message
            }
        }
    }

    fun filterMenuItems(query: String) {
        currentSearchQuery = query
        applyFilters()
    }

    private fun applyFilters() {
        var filteredItems = allMenuItems

        // Apply category filter
        if (currentCategory != null && currentCategory != "ทั้งหมด") {
            filteredItems = filteredItems.filter { it.category == currentCategory }
        }

        // Apply search filter
        if (currentSearchQuery.isNotEmpty()) {
            filteredItems = filteredItems.filter {
                it.name.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        _menuItems.value = filteredItems
    }

    fun addToCart(item: MenuDataItem) {
        val currentItems = _cartItems.value?.toMutableList() ?: mutableListOf()
        currentItems.add(item)
        _cartItems.value = currentItems
        updateTotalPrice()
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        updateTotalPrice()
    }

    private fun updateTotalPrice() {
        val total = _cartItems.value?.sumOf { it.price } ?: 0.0
        _totalPrice.value = total
    }

    fun setIsPremiumUser(isPremium: Boolean) {
        _isPremiumUser.value = isPremium
    }
} 