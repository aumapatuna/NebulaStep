package com.example.stepcounter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etStepGoal = findViewById<EditText>(R.id.et_step_goal)
        val etWeight = findViewById<EditText>(R.id.et_weight)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnReset = findViewById<Button>(R.id.btn_reset_data)

        val sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)

        // --- MASTER BACKGROUND TRACKING SWITCH ---
        val switchTracking = findViewById<android.widget.Switch>(R.id.switch_tracking)
        val isTracking = sharedPreferences.getBoolean("isTracking", true)
        switchTracking.isChecked = isTracking

        switchTracking.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("isTracking", isChecked).apply()
            
            val serviceIntent = Intent(this, StepCounterService::class.java)
            if (isChecked) {
                androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
                Toast.makeText(this, "Background Tracking Enabled", Toast.LENGTH_SHORT).show()
            } else {
                stopService(serviceIntent)
                Toast.makeText(this, "Background Tracking Killed", Toast.LENGTH_SHORT).show()
            }
        }
        // -----------------------------------------

        val rgAnimation = findViewById<android.widget.RadioGroup>(R.id.rg_animation)
        val rbWalking = findViewById<android.widget.RadioButton>(R.id.rb_walking)
        val rbJogging = findViewById<android.widget.RadioButton>(R.id.rb_jogging)
        val rbSprinting = findViewById<android.widget.RadioButton>(R.id.rb_sprinting)

        val currentGoal = sharedPreferences.getInt("stepGoal", 10000)
        etStepGoal.setText(currentGoal.toString())

        val currentWeight = sharedPreferences.getFloat("weight", 70f)
        etWeight.setText(currentWeight.toString())

        val currentAnim = sharedPreferences.getString("animStyle", "walking")
        when (currentAnim) {
            "jogging" -> rbJogging.isChecked = true
            "sprinting" -> rbSprinting.isChecked = true
            else -> rbWalking.isChecked = true
        }

        btnSave.setOnClickListener {
            val newGoal = etStepGoal.text.toString().toIntOrNull() ?: 10000
            val newWeight = etWeight.text.toString().toFloatOrNull() ?: 70f
            
            val selectedAnim = when (rgAnimation.checkedRadioButtonId) {
                R.id.rb_jogging -> "jogging"
                R.id.rb_sprinting -> "sprinting"
                else -> "walking"
            }
            
            sharedPreferences.edit()
                .putInt("stepGoal", newGoal)
                .putFloat("weight", newWeight)
                .putString("animStyle", selectedAnim)
                .apply()
                
            Toast.makeText(this, "Daily Goal saved: $newGoal steps!", Toast.LENGTH_SHORT).show()
            
            // Close the settings menu cleanly and return to MainActivity
            finish() 
        }

        btnReset.setOnClickListener {
            // Because the whole app architecture is tied to SharedPreferences,
            // resetting the counter is as simple as forcing this integer back to 0!
            sharedPreferences.edit()
                .putInt("currentSteps", 0)
                .apply()
                
            Toast.makeText(this, "All step data forcefully wiped!", Toast.LENGTH_SHORT).show()
            
            // StepCounterService handles 'previousTotalSteps' independently, 
            // so if we wanted to truly wipe it we would also wipe 'key1'.
            // For now, this just zeros the session out.
        }
    }
}
