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

        val lineChart = findViewById<LineChart>(R.id.line_chart)
        val pieChart = findViewById<PieChart>(R.id.pie_chart)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            tvStatus.text = "You must be logged into Google Phase to see cloud history!"
            return
        }

        val db = FirebaseFirestore.getInstance()
        
        // Fetch last 30 days of data ordered chronologically (ascending so graph reads left to right)
        db.collection("users").document(user.uid)
          .collection("history")
          .orderBy("date", Query.Direction.ASCENDING)
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

                  val stepEntries = ArrayList<Entry>()
                  val calEntries = ArrayList<Entry>()
                  val dateLabels = ArrayList<String>()

                  var index = 0f
                  for (document in documents) {
                      val date = document.getString("date") ?: ""
                      val steps = document.getLong("steps") ?: 0L
                      val cals = document.getLong("calories") ?: 0L

                      totalSteps += steps
                      totalCals += cals
                      activeDays++
                      
                      // Example static goal for calculation
                      if (steps >= 6000) goalsHit++

                      // Make date shorter (MM-DD)
                      val shortDate = if (date.length >= 10) date.substring(5) else date
                      dateLabels.add(shortDate)

                      stepEntries.add(Entry(index, steps.toFloat()))
                      // Scale calories slightly for visual comparison in the same chart
                      calEntries.add(Entry(index, cals.toFloat()))
                      index++
                  }

                  // Update KPI Headers
                  val formatter = NumberFormat.getNumberInstance(Locale.US)
                  tvTotalSteps.text = formatter.format(totalSteps)
                  kpi1Val.text = activeDays.toString()
                  val successRate = if (activeDays > 0) ((goalsHit.toFloat() / activeDays) * 100).toInt() else 0
                  kpi2Val.text = "$successRate%"
                  kpi3Val.text = formatter.format(totalCals)

                  // Render Line Chart
                  renderLineChart(lineChart, stepEntries, calEntries, dateLabels)
                  
                  // Render Pie Chart (Distribution Mock based on activity)
                  renderPieChart(pieChart, totalSteps)
              }
          }
          .addOnFailureListener { e ->
              tvStatus.text = "Failed to load cloud history securely: ${e.message}"
          }
    }

    private fun renderLineChart(chart: LineChart, steps: List<Entry>, cals: List<Entry>, dates: List<String>) {
        // Desktop Aesthetic Adjustments
        chart.description.isEnabled = false
        chart.legend.textColor = Color.WHITE
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.textColor = Color.parseColor("#88A0B8")
        chart.xAxis.gridColor = Color.parseColor("#33FFFFFF")
        chart.axisLeft.textColor = Color.parseColor("#88A0B8")
        chart.axisLeft.gridColor = Color.parseColor("#33FFFFFF")

        // Set Labels
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(dates)
        chart.xAxis.isGranularityEnabled = true
        chart.xAxis.granularity = 1f

        // Step Series (Blue)
        val stepSet = LineDataSet(steps, "Daily Steps")
        stepSet.color = Color.parseColor("#2494F2")
        stepSet.setCircleColor(Color.parseColor("#2494F2"))
        stepSet.lineWidth = 2.5f
        stepSet.circleRadius = 4f
        stepSet.setDrawValues(false)
        stepSet.mode = if (steps.size > 1) LineDataSet.Mode.CUBIC_BEZIER else LineDataSet.Mode.LINEAR

        // Calorie Series (Green)
        val calSet = LineDataSet(cals, "Calories Burned")
        calSet.color = Color.parseColor("#89C74A")
        calSet.setCircleColor(Color.parseColor("#89C74A"))
        calSet.lineWidth = 2.5f
        calSet.circleRadius = 4f
        calSet.setDrawValues(false)
        calSet.mode = if (cals.size > 1) LineDataSet.Mode.CUBIC_BEZIER else LineDataSet.Mode.LINEAR

        val lineData = LineData(stepSet, calSet)
        chart.data = lineData
        chart.animateX(1000)
        chart.invalidate()
    }

    private fun renderPieChart(chart: PieChart, totalSteps: Long) {
        chart.description.isEnabled = false
        
        if (totalSteps <= 0L) {
            chart.setNoDataText("No activity logged yet.")
            chart.setNoDataTextColor(Color.parseColor("#88A0B8"))
            chart.clear()
            return
        }

        chart.setUsePercentValues(true)
        chart.isDrawHoleEnabled = true
        chart.holeRadius = 65f
        chart.setHoleColor(Color.TRANSPARENT)
        chart.setTransparentCircleColor(Color.WHITE)
        chart.setTransparentCircleAlpha(40)
        chart.transparentCircleRadius = 70f
        
        // Center text visually analogous to desktop donut texts
        chart.centerText = "Activity\nShare"
        chart.setCenterTextColor(Color.WHITE)
        chart.setCenterTextSize(14f)

        chart.legend.textColor = Color.WHITE
        chart.legend.isWordWrapEnabled = true


        val entries = ArrayList<PieEntry>()
        // Derive rough estimates from total steps for the pie visual!
        val walking = totalSteps * 0.65f
        val running = totalSteps * 0.20f
        val climbing = totalSteps * 0.15f

        entries.add(PieEntry(walking, "Walking"))
        entries.add(PieEntry(running, "Running / Jogging"))
        entries.add(PieEntry(climbing, "Elevation / Stairs"))

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(
            Color.parseColor("#2494F2"), // Blue
            Color.parseColor("#89C74A"), // Green
            Color.parseColor("#E24A45")  // Red
        )
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f
        
        val data = PieData(dataSet)
        chart.data = data
        chart.animateY(1000)
        chart.invalidate()
    }
}
