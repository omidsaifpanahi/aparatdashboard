// src/main/com/example/aparatdashboard/MainActivity.kt
package com.example.aparatdashboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.aparatdashboard.databinding.ActivityMainBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient()

    private val handler = Handler(Looper.getMainLooper())
    private val autoRefreshMillis = 300000L

    private var lastWatchTime = 0
    private var lastTotalViews = 0

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!CookieStore.hasCookie(this)) {
            startActivity(Intent(this, CookieInputActivity::class.java))
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        setupChart()

        binding.refreshButton.setOnClickListener {
            fetchDashboardData(showToastOnSuccess = true)
        }

        fetchDashboardData(showToastOnSuccess = false)
        startAutoRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun startAutoRefresh() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchDashboardData(showToastOnSuccess = false, fromAutoRefresh = true)
                handler.postDelayed(this, autoRefreshMillis)
            }
        }, autoRefreshMillis)
    }

    private fun fetchDashboardData(
        showToastOnSuccess: Boolean,
        fromAutoRefresh: Boolean = false
    ) {
        binding.statusText.text = "در حال دریافت اطلاعات..."
        binding.refreshButton.isEnabled = false

        val cookieString = CookieStore.getCookie(this)

        val request = Request.Builder()
            .url("https://www.aparat.com/api/fa/v1/user/dashboard/stat")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0 (Android)")
            .addHeader("Cookie", cookieString)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    binding.statusText.text = "خطا در دریافت اطلاعات: ${e.message}"
                    binding.refreshButton.isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()

                if (!response.isSuccessful || body == null) {
                    runOnUiThread {
                        binding.statusText.text = "خطا در پاسخ سرور: ${response.code}"
                        binding.refreshButton.isEnabled = true
                    }
                    return
                }

                try {
                    val root = JSONObject(body)
                    val attributes = root
                        .getJSONObject("data")
                        .getJSONObject("attributes")

                    val dashboardData = attributes.optJSONObject("dashboardData") ?: JSONObject()
                    val chartData = attributes.optJSONObject("chartData") ?: JSONObject()

                    val newWatchTime = parseSafeInt(dashboardData.optString("ChannelWatchTime", "0"))
                    val newTotalViews = parseSafeInt(dashboardData.optString("ChannelImp", "0"))

                    val watchTimeDiff = newWatchTime - lastWatchTime
                    val viewsDiff = newTotalViews - lastTotalViews

                    runOnUiThread {
                        updateDashboard(dashboardData)
                        updateChart(chartData)
                        binding.statusText.text = "آخرین به‌روزرسانی با موفقیت انجام شد"
                        binding.refreshButton.isEnabled = true

                        if (showToastOnSuccess) {
                            Toast.makeText(
                                this@MainActivity,
                                "آمار به‌روزرسانی شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    if (lastWatchTime != 0 || lastTotalViews != 0) {
                        if (viewsDiff > 0 || watchTimeDiff > 0) {
                            showNotification(
                                "آمار جدید کانال",
                                "+$viewsDiff بازدید | +$watchTimeDiff دقیقه تماشا"
                            )
                        }
                    }

                    lastWatchTime = newWatchTime
                    lastTotalViews = newTotalViews

                } catch (e: Exception) {
                    runOnUiThread {
                        binding.statusText.text = "خطا در پردازش داده: ${e.message}"
                        binding.refreshButton.isEnabled = true
                    }
                }
            }
        })
    }

    private fun updateDashboard(dashboardData: JSONObject) {
        val datesObj = dashboardData.optJSONObject("channel_watch_time_dates")
        val fromDate = datesObj?.optString("from", "-") ?: "-"
        val toDate = datesObj?.optString("to", "-") ?: "-"
        binding.dateRangeText.text = "بازه زمانی: $fromDate — $toDate"

        binding.followersValue.text = dashboardData.optString("ChannelFollower", "0")
        binding.totalViewsValue.text = dashboardData.optString("ChannelImp", "0")
        binding.watchTimeValue.text = dashboardData.optString("ChannelWatchTime", "0")
        binding.todayViewsValue.text = dashboardData.optString("ChannelTodayImp", "0")
        binding.monthViewsValue.text = dashboardData.optString("ChannelMonthImp", "0")
        binding.notificationCountValue.text =
            dashboardData.optString("ChannelPushNotificationCount", "0")
    }

    private fun setupChart() {
        with(binding.lineChart) {
            description.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            legend.isEnabled = true
            axisRight.isEnabled = false
            setNoDataText("داده‌ای برای نمایش وجود ندارد")

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)

            axisLeft.setDrawGridLines(true)
            animateX(700)
        }
    }

    private fun updateChart(chartData: JSONObject) {
        val categories = jsonArrayToStringList(chartData.optJSONArray("category"))

        val seriesObj = chartData.optJSONObject("series") ?: JSONObject()

        val viewsData = extractSeriesData(seriesObj.optJSONObject("srcprofile"))
        val watchData = extractSeriesData(seriesObj.optJSONObject("srcprofilewatch"))
        val followerData = extractSeriesData(seriesObj.optJSONObject("follower"))

        val viewsEntries = viewsData.mapIndexed { index, value -> Entry(index.toFloat(), value.toFloat()) }
        val watchEntries = watchData.mapIndexed { index, value -> Entry(index.toFloat(), value.toFloat()) }
        val followerEntries = followerData.mapIndexed { index, value -> Entry(index.toFloat(), value.toFloat()) }

        val viewsSet = LineDataSet(viewsEntries, "بازدید").apply {
            color = Color.parseColor("#667EEA")
            setCircleColor(Color.parseColor("#667EEA"))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(false)
        }

        val watchSet = LineDataSet(watchEntries, "مدت تماشا").apply {
            color = Color.parseColor("#10B981")
            setCircleColor(Color.parseColor("#10B981"))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(false)
        }

        val followerSet = LineDataSet(followerEntries, "دنبال‌کننده جدید").apply {
            color = Color.parseColor("#F59E0B")
            setCircleColor(Color.parseColor("#F59E0B"))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(false)
        }

        binding.lineChart.xAxis.valueFormatter =
            com.github.mikephil.charting.formatter.IndexAxisValueFormatter(categories)

        binding.lineChart.data = LineData(viewsSet, watchSet, followerSet)
        binding.lineChart.invalidate()
    }

    private fun extractSeriesData(seriesObject: JSONObject?): List<Int> {
        if (seriesObject == null) return emptyList()
        val dataArray = seriesObject.optJSONArray("data") ?: JSONArray()
        val result = mutableListOf<Int>()

        for (i in 0 until dataArray.length()) {
            result.add(parseSafeInt(dataArray.optString(i, "0")))
        }

        return result
    }

    private fun jsonArrayToStringList(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.optString(i, ""))
        }
        return list
    }

    private fun parseSafeInt(value: String?): Int {
        if (value.isNullOrBlank()) return 0
        return value.replace(",", "").trim().toIntOrNull() ?: 0
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "aparat_updates",
                "Aparat Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notification channel for Aparat dashboard updates"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) return
        }

        val builder = NotificationCompat.Builder(this, "aparat_updates")
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(this).notify(1001, builder.build())
    }
}
