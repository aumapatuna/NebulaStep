package com.example.stepcounter

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.NumberFormat
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val tvStatus = findViewById<TextView>(R.id.tv_report_logs)
        val tvTotalSteps = findViewById<TextView>(R.id.tv_total_steps)
        val kpi1Val = findViewById<TextView>(R.id.kpi1_val) // Active Days
        val kpi2Val = findViewById<TextView>(R.id.kpi2_val) // Goal Success
        val kpi3Val = findViewById<TextView>(R.id.kpi3_val) // Total Cals
        val container = findViewById<android.widget.LinearLayout>(R.id.history_list_container)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            tvStatus.text = "You must be logged into Google Phase to see cloud history!"
            return
        }

        val db = FirebaseFirestore.getInstance()
        
        // Fetch last 30 days of data
        db.collection("users").document(user.uid)
          .collection("history")
          .orderBy("date", Query.Direction.DESCENDING)
          .limit(30)
          .get()
          .addOnSuccessListener { documents ->
              if (documents.isEmpty) {
                  tvStatus.text = "No history found in the cloud yet. Start walking to see charts!"
                  tvTotalSteps.text = "0"
              } else {
                  tvStatus.text = "All systems synced. Displaying live cloud analytics."

                  var totalSteps = 0L
                  var totalCals = 0L
                  var activeDays = 0
                  var goalsHit = 0
                  var highestDailyStep = 10L

                  val dataList = mutableListOf<Map<String, Any>>()

                  for (document in documents) {
                      val date = document.getString("date") ?: ""
                      val steps = document.getLong("steps") ?: 0L
                      val cals = document.getLong("calories") ?: 0L

                      totalSteps += steps
                      totalCals += cals
                      activeDays++
                      
                      if (steps >= 6000) goalsHit++
                      if (steps > highestDailyStep) highestDailyStep = steps

                      dataList.add(mapOf("date" to date, "steps" to steps, "cals" to cals))
                  }

                  // Update KPI Headers
                  val formatter = NumberFormat.getNumberInstance(Locale.US)
                  tvTotalSteps.text = formatter.format(totalSteps)
                  kpi1Val.text = activeDays.toString()
                  val successRate = if (activeDays > 0) ((goalsHit.toFloat() / activeDays) * 100).toInt() else 0
                  kpi2Val.text = "$successRate%"
                  kpi3Val.text = formatter.format(totalCals)

                  // Dynamically construct pure native UI bars for each day!
                  for (item in dataList) {
                      val dateStr = item["date"] as String
                      val stepLong = item["steps"] as Long
                      val calsLong = item["cals"] as Long
                      
                      // Outer layout for the day row
                      val row = android.widget.LinearLayout(this).apply {
                          orientation = android.widget.LinearLayout.VERTICAL
                          background = androidx.core.content.ContextCompat.getDrawable(this@ReportActivity, R.drawable.bg_dashboard_card)
                          setPadding(32, 24, 32, 24)
                          layoutParams = android.widget.LinearLayout.LayoutParams(
                              android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                              android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                          ).apply { setMargins(0, 0, 0, 16) }
                          elevation = 2f
                      }
                      
                      // Top header text
                      val header = android.widget.LinearLayout(this).apply {
                          orientation = android.widget.LinearLayout.HORIZONTAL
                          weightSum = 2f
                          layoutParams = android.widget.LinearLayout.LayoutParams(
                              android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                              android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                          )
                      }
                      
                      val shortDate = if (dateStr.length >= 10) dateStr.substring(5) else dateStr
                      val tvDate = TextView(this).apply {
                          text = "📅 $shortDate"
                          setTextColor(Color.parseColor("#88A0B8"))
                          textSize = 14f
                          layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                      }
                      
                      val tvStats = TextView(this).apply {
                          text = "${formatter.format(stepLong)} steps"
                          setTextColor(Color.WHITE)
                          textSize = 14f
                          gravity = android.view.Gravity.END
                          layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                      }
                      
                      header.addView(tvDate)
                      header.addView(tvStats)
                      row.addView(header)
                      
                      // Render the visual bar ratio native to Android without Chart Engines!
                      val barContainer = android.widget.LinearLayout(this).apply {
                          orientation = android.widget.LinearLayout.HORIZONTAL
                          background = android.graphics.drawable.ColorDrawable(Color.parseColor("#151D26"))
                          layoutParams = android.widget.LinearLayout.LayoutParams(
                              android.view.ViewGroup.LayoutParams.MATCH_PARENT, 24
                          ).apply { setMargins(0, 16, 0, 0) }
                      }
                      
                      val widthRatio = (stepLong.toFloat() / highestDailyStep.toFloat()).coerceIn(0.01f, 1.0f)
                      val colorHex = if (stepLong >= 6000) "#89C74A" else "#2494F2"
                      
                      val barFill = android.view.View(this).apply {
                          background = android.graphics.drawable.ColorDrawable(Color.parseColor(colorHex))
                          layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, widthRatio)
                      }
                      
                      val emptySpace = android.view.View(this).apply {
                          layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1f - widthRatio)
                      }
                      
                      barContainer.addView(barFill)
                      barContainer.addView(emptySpace)
                      
                      row.addView(barContainer)
                      container.addView(row)
                  }
              }
          }
          .addOnFailureListener { e ->
              tvStatus.text = "Failed to load cloud history securely: ${e.message}"
          }
    }
}
