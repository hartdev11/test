package com.natthasethstudio.sethpos

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.Timestamp
import com.natthasethstudio.sethpos.adapter.TopSellingItemAdapter
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tvTotalSales: TextView
    private lateinit var tvOrderCount: TextView
    private lateinit var tvAverageOrder: TextView
    private lateinit var tvNewCustomers: TextView
    private lateinit var tvReturningCustomers: TextView
    private lateinit var rvTopSellingItems: RecyclerView
    private lateinit var lineChart: LineChart
    private lateinit var timeRangeChipGroup: ChipGroup
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoData: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    private var selectedTimeRange: TimeRange = TimeRange.TODAY

    enum class TimeRange {
        TODAY, WEEK, MONTH
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        initializeViews()
        setupToolbar()
        setupTimeRangeChips()
        setupRealTimeListener()

        // Setup back press callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        // ยกเลิก listener เมื่อออกจากหน้า
        listenerRegistration?.remove()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        tvTotalSales = findViewById(R.id.tvTotalSales)
        tvOrderCount = findViewById(R.id.tvOrderCount)
        tvAverageOrder = findViewById(R.id.tvAverageOrder)
        tvNewCustomers = findViewById(R.id.tvNewCustomers)
        tvReturningCustomers = findViewById(R.id.tvReturningCustomers)
        rvTopSellingItems = findViewById(R.id.rvTopSellingItems)
        lineChart = findViewById(R.id.lineChart)
        timeRangeChipGroup = findViewById(R.id.timeRangeChipGroup)
        progressBar = findViewById(R.id.progressBar)
        tvNoData = findViewById(R.id.tvNoData)

        rvTopSellingItems.layoutManager = LinearLayoutManager(this)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "แดชบอร์ด"
    }

    private fun setupTimeRangeChips() {
        // ตั้งค่าเริ่มต้นเป็นวันนี้
        timeRangeChipGroup.check(R.id.chipToday)
        selectedTimeRange = TimeRange.TODAY
        setupRealTimeListener()

        timeRangeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipToday -> {
                    selectedTimeRange = TimeRange.TODAY
                    setupRealTimeListener()
                }
                R.id.chipWeek -> {
                    selectedTimeRange = TimeRange.WEEK
                    setupRealTimeListener()
                }
                R.id.chipMonth -> {
                    selectedTimeRange = TimeRange.MONTH
                    setupRealTimeListener()
                }
            }
        }
    }

    private fun setupRealTimeListener() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) return

        // แสดง loading
        showLoading(true)
        hideNoData()

        val (startTime, endTime) = when (selectedTimeRange) {
            TimeRange.TODAY -> {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val start = Timestamp(calendar.time)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val end = Timestamp(calendar.time)
                Pair(start, end)
            }
            TimeRange.WEEK -> {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val start = Timestamp(calendar.time)
                calendar.add(Calendar.DAY_OF_WEEK, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val end = Timestamp(calendar.time)
                Pair(start, end)
            }
            TimeRange.MONTH -> {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val start = Timestamp(calendar.time)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val end = Timestamp(calendar.time)
                Pair(start, end)
            }
        }

        Log.d("DashboardActivity", "Time range: ${startTime.toDate()} to ${endTime.toDate()}")

        // ยกเลิก listener เก่า
        listenerRegistration?.remove()

        // เพิ่ม real-time listener
        listenerRegistration = db.collection("orders")
            .whereEqualTo("storeId", currentUser.uid)
            .whereGreaterThanOrEqualTo("timestamp", startTime)
            .whereLessThanOrEqualTo("timestamp", endTime)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                // ซ่อน loading
                showLoading(false)

                if (e != null) {
                    Log.e("DashboardActivity", "Listen failed", e)
                    showError()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    Log.d("DashboardActivity", "Received ${snapshot.size()} orders")
                    if (snapshot.isEmpty) {
                        showNoData()
                    } else {
                        hideNoData()
                        updateDashboardData(snapshot)
                    }
                }
            }
    }

    private fun getTimeRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endTime = calendar.time

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        when (selectedTimeRange) {
            TimeRange.TODAY -> {
                // ไม่ต้องทำอะไรเพิ่ม เพราะเริ่มต้นที่วันนี้อยู่แล้ว
            }
            TimeRange.WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            }
            TimeRange.MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return Pair(calendar.time, endTime)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showNoData() {
        tvNoData.visibility = View.VISIBLE
        val message = when (selectedTimeRange) {
            TimeRange.TODAY -> "ยังไม่มีข้อมูลยอดขายวันนี้"
            TimeRange.WEEK -> "ยังไม่มีข้อมูลยอดขายในสัปดาห์นี้"
            TimeRange.MONTH -> "ยังไม่มีข้อมูลยอดขายในเดือนนี้"
        }
        tvNoData.text = message
    }

    private fun hideNoData() {
        tvNoData.visibility = View.GONE
    }

    private fun showError() {
        tvTotalSales.text = "เกิดข้อผิดพลาด"
        tvOrderCount.text = "0"
        tvAverageOrder.text = "0.00 บาท"
        tvNewCustomers.text = "0"
        tvReturningCustomers.text = "0"
        showNoData()
    }

    private fun updateDashboardData(documents: QuerySnapshot) {
        var totalSales = 0.0
        val orderCount = documents.size()
        val menuCount = mutableMapOf<String, Int>()
        val customerIds = mutableSetOf<String>()
        val returningCustomerIds = mutableSetOf<String>()
        val salesData = mutableMapOf<String, Double>()

        // สร้างช่วงเวลาสำหรับกราฟ
        val timeSlots = when (selectedTimeRange) {
            TimeRange.TODAY -> {
                val slots = mutableMapOf<String, Double>()
                for (hour in 0..23) {
                    val timeStr = String.format("%02d:00", hour)
                    slots[timeStr] = 0.0
                }
                slots
            }
            TimeRange.WEEK -> {
                val slots = mutableMapOf<String, Double>()
                val days = listOf("อา.", "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.")
                days.forEach { day -> slots[day] = 0.0 }
                slots
            }
            TimeRange.MONTH -> {
                val slots = mutableMapOf<String, Double>()
                val calendar = Calendar.getInstance()
                val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                for (day in 1..daysInMonth) {
                    val dateStr = String.format("%02d/%02d", day, calendar.get(Calendar.MONTH) + 1)
                    slots[dateStr] = 0.0
                }
                slots
            }
        }

        for (document in documents) {
            @Suppress("UNCHECKED_CAST")
            val items = document.get("items") as? List<Map<String, Any>>
            val customerId = document.getString("customerId")
            val timestamp = document.getTimestamp("timestamp")
            
            customerId?.let {
                customerIds.add(it)
                if (isReturningCustomer(it)) {
                    returningCustomerIds.add(it)
                }
            }

            var orderTotal = 0.0
            items?.forEach { item ->
                val price = (item["price"] as? Number)?.toDouble() ?: 0.0
                val quantity = (item["quantity"] as? Number)?.toInt() ?: 0
                orderTotal += price * quantity

                val menuName = item["name"] as? String
                menuName?.let {
                    menuCount[it] = (menuCount[it] ?: 0) + quantity
                }
            }
            totalSales += orderTotal

            // เพิ่มข้อมูลสำหรับกราฟ
            timestamp?.let {
                val date = when (selectedTimeRange) {
                    TimeRange.TODAY -> {
                        val calendar = Calendar.getInstance()
                        calendar.time = it.toDate()
                        String.format("%02d:00", calendar.get(Calendar.HOUR_OF_DAY))
                    }
                    TimeRange.WEEK -> {
                        val calendar = Calendar.getInstance()
                        calendar.time = it.toDate()
                        val days = listOf("อา.", "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.")
                        days[calendar.get(Calendar.DAY_OF_WEEK) - 1]
                    }
                    TimeRange.MONTH -> {
                        val calendar = Calendar.getInstance()
                        calendar.time = it.toDate()
                        String.format("%02d/%02d", 
                            calendar.get(Calendar.DAY_OF_MONTH),
                            calendar.get(Calendar.MONTH) + 1
                        )
                    }
                }
                timeSlots[date] = (timeSlots[date] ?: 0.0) + orderTotal
            }
        }

        // คำนวณค่าเฉลี่ยต่อออเดอร์
        val averageOrder = if (orderCount > 0) totalSales / orderCount else 0.0

        // อัพเดท UI
        runOnUiThread {
            tvTotalSales.text = String.format("%.2f บาท", totalSales)
            tvOrderCount.text = orderCount.toString()
            tvAverageOrder.text = String.format("%.2f บาท", averageOrder)
            tvNewCustomers.text = (customerIds.size - returningCustomerIds.size).toString()
            tvReturningCustomers.text = returningCustomerIds.size.toString()

            // อัพเดทเมนูขายดี
            val topSellingItems = menuCount.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value }
            val adapter = TopSellingItemAdapter(topSellingItems)
            rvTopSellingItems.adapter = adapter

            // อัพเดทกราฟ
            setupSalesChart(timeSlots)
        }
    }

    private fun isReturningCustomer(customerId: String): Boolean {
        // ตรวจสอบว่าลูกค้าเคยสั่งซื้อมาก่อนหรือไม่
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        val oneMonthAgo = Timestamp(calendar.time)

        var isReturning = false
        db.collection("orders")
            .whereEqualTo("customerId", customerId)
            .whereLessThan("timestamp", oneMonthAgo)
            .get()
            .addOnSuccessListener { documents ->
                isReturning = !documents.isEmpty
            }
        return isReturning
    }

    private fun setupSalesChart(salesData: Map<String, Double>) {
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()

        // เรียงข้อมูลตามเวลา
        val sortedData = when (selectedTimeRange) {
            TimeRange.TODAY -> salesData.toList().sortedBy { 
                it.first.split(":").first().toInt()
            }
            TimeRange.WEEK -> salesData.toList().sortedBy { 
                val day = it.first
                when (day) {
                    "อา." -> 0
                    "จ." -> 1
                    "อ." -> 2
                    "พ." -> 3
                    "พฤ." -> 4
                    "ศ." -> 5
                    "ส." -> 6
                    else -> 0
                }
            }
            TimeRange.MONTH -> salesData.toList().sortedBy { 
                it.first.split("/").first().toInt()
            }
        }

        sortedData.forEachIndexed { index, (date, sales) ->
            entries.add(Entry(index.toFloat(), sales.toFloat()))
            labels.add(date)
        }

        val dataSet = LineDataSet(entries, "ยอดขาย").apply {
            color = ContextCompat.getColor(this@DashboardActivity, R.color.colorPrimary)
            setCircleColor(ContextCompat.getColor(this@DashboardActivity, R.color.colorPrimary))
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(false)
        }

        val lineData = LineData(dataSet)
        lineChart.apply {
            data = lineData
            description.isEnabled = false
            legend.isEnabled = true
            setTouchEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                valueFormatter = IndexAxisValueFormatter(labels)
                labelRotationAngle = -45f
            }
            axisRight.isEnabled = false
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(context, android.R.color.darker_gray)
            }
            invalidate()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
} 