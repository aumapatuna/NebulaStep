package com.example.stepcounter

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val reportView = findViewById<TextView>(R.id.tv_report_logs)
        val user = FirebaseAuth.getInstance().currentUser

        // Failsafe in case they bypassed the login screen
        if (user == null) {
            reportView.text = "You must be logged into Google Phase to see cloud history!"
            return
        }

        val db = FirebaseFirestore.getInstance()
        
        // Query the user's secure private cloud space, downloading the last 30 days!
        db.collection("users").document(user.uid)
          .collection("history")
          .orderBy("date", Query.Direction.DESCENDING)
          .limit(30)
          .get()
          .addOnSuccessListener { documents ->
              if (documents.isEmpty) {
                  reportView.text = "No history found in the cloud yet. Your first data point logs at midnight!"
              } else {
                  val outText = StringBuilder()
                  for (document in documents) {
                      val date = document.getString("date")
                      val steps = document.getLong("steps")
                      val cals = document.getLong("calories")
                      outText.append("📅 Date: $date\n👟 Steps: $steps\n🔥 Calories: $cals\n\n")
                  }
                  reportView.text = outText.toString()
              }
          }
          .addOnFailureListener { e ->
              reportView.text = "Failed to load cloud history securely: ${e.message}"
          }
    }
}
