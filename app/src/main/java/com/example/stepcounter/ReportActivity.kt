package com.example.stepcounter

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
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
        val tvQuote = findViewById<TextView>(R.id.tv_motivation)
        val tvTotalSteps = findViewById<TextView>(R.id.tv_total_steps)
        val tvTotalCals = findViewById<TextView>(R.id.tv_total_cals)
        val barChart = findViewById<BarChart>(R.id.bar_chart)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            tvStatus.text = "Error: Authentication missing."
            tvQuote.text = "You are not logged in."
            return
        }

        val db = FirebaseFirestore.getInstance()
        
        // Grab the last 14 days chronologically to plot a clean progression
        db.collection("users").document(user.uid)
          .collection("history")
          .orderBy("date", Query.Direction.ASCENDING)
          .limit(14)
          .get()
          .addOnSuccessListener { documents ->
              if (documents.isEmpty) {
                  tvQuote.text = "A New Journey Begins! 🚀"
                  tvStatus.text = "Your first data points will synchronize at midnight."
              } else {
                  tvStatus.text = "Cloud secure. Here is your recent activity:"

                  var totalSteps = 0L
                  var totalCals = 0L
                  val entries = ArrayList<BarEntry>()
                  val dateLabels = ArrayList<String>()

                  var index = 0f
                  for (document in documents) {
                      val date = document.getString("date") ?: ""
                      val steps = document.getLong("steps") ?: 0L
                      val cals = document.getLong("calories") ?: 0L

                      totalSteps += steps
                      totalCals += cals

                      // Formatter MM-DD
                      val shortDate = if (date.length >= 10) date.substring(5) else date
                      dateLabels.add(shortDate)
                      entries.add(BarEntry(index, steps.toFloat()))
                      index++
                  }

                  // Update Numbers
                  val formatter = NumberFormat.getNumberInstance(Locale.US)
                  tvTotalSteps.text = formatter.format(totalSteps)
                  tvTotalCals.text = formatter.format(totalCals)
                  
                  // Compute Motivation Phrase
                  tvQuote.text = when {
                      totalSteps > 50_000 -> "Absolute Legend! 🔥 You are crushing your goals."
                      totalSteps > 20_000 -> "Incredible momentum! Keep this streak alive. ⭐"
                      totalSteps > 5_000 -> "You're on the right track! Every step counts. 👟"
                      else -> "A great start! Keep moving to unlock your potential. 🌱"
                  }

                  // Setup Gorgeous Bar Chart safely
                  barChart.description.isEnabled = false
                  barChart.setDrawGridBackground(false)
                  barChart.axisRight.isEnabled = false
                  barChart.legend.textColor = Color.WHITE
                  
                  barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
                  barChart.xAxis.textColor = Color.parseColor("#88A0B8")
                  barChart.xAxis.gridColor = Color.parseColor("#33FFFFFF")
                  barChart.xAxis.valueFormatter = IndexAxisValueFormatter(dateLabels)
                  barChart.xAxis.isGranularityEnabled = true
                  barChart.xAxis.granularity = 1f
                  
                  barChart.axisLeft.textColor = Color.parseColor("#88A0B8")
                  barChart.axisLeft.gridColor = Color.parseColor("#33FFFFFF")
                  barChart.axisLeft.axisMinimum = 0f

                  // Populate
                  if (entries.isNotEmpty()) {
                      val dataset = BarDataSet(entries, "Daily Steps")
                      dataset.color = Color.parseColor("#00E5FF")
                      dataset.valueTextColor = Color.WHITE
                      dataset.valueTextSize = 10f
                      
                      val barData = BarData(dataset)
                      barData.barWidth = 0.6f
                      barChart.data = barData
                      barChart.animateY(1200)
                      barChart.invalidate()
                  } else {
                      barChart.clear()
                  }
              }
          }
          .addOnFailureListener { e ->
              tvStatus.text = "Connection Failed: ${e.message}"
              tvQuote.text = "Cloud Sync Error"
          }
    }
}
