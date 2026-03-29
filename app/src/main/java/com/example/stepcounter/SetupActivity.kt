package com.example.stepcounter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val etWeight = findViewById<EditText>(R.id.et_setup_weight)
        val etGoal = findViewById<EditText>(R.id.et_setup_goal)
        val btnFinish = findViewById<Button>(R.id.btn_setup_finish)

        val sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)

        btnFinish.setOnClickListener {
            val weightInput = etWeight.text.toString()
            val goalInput = etGoal.text.toString()

            if (weightInput.isEmpty() || goalInput.isEmpty()) {
                Toast.makeText(this, "Please enter both values!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newGoal = goalInput.toIntOrNull() ?: 10000
            val newWeight = weightInput.toFloatOrNull() ?: 70f

            sharedPreferences.edit()
                .putInt("stepGoal", newGoal)
                .putFloat("weight", newWeight)
                .putBoolean("isSetupComplete", true)
                .apply()

            Toast.makeText(this, "Profile Saved!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
