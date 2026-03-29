package com.example.stepcounter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    // With the new architecture, MainActivity is just a "Dumb View".
    // It doesn't touch sensors anymore! It just reads from SharedPreferences.

    private lateinit var stepsTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stepsTextView = findViewById(R.id.tv_steps)
        titleTextView = findViewById(R.id.tv_title)
        sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)

        findViewById<android.widget.ImageView>(R.id.iv_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        findViewById<android.widget.ImageView>(R.id.iv_report).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }

        updateStepUI() // Load whatever is saved right now
        requestPermissions() // Start permission chain
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Sensor Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        
        // Needed for Android 13+ AOD Persistent Lock-screen Notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // We have all permissions! Boot up the background service.
            startStepService()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) allGranted = false
        }
        if (allGranted) {
            startStepService()
        } else {
            Toast.makeText(this, "Permissions required for Step Counter", Toast.LENGTH_LONG).show()
        }
    }

    private fun startStepService() {
        // Only boot up the background engine if the user hasn't explicitly disabled it in settings
        val isTracking = sharedPreferences.getBoolean("isTracking", true)
        if (isTracking) {
            val serviceIntent = Intent(this, StepCounterService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Listen dynamically for whenever the Background Service updates the number!
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        updateStepUI()
    }

    override fun onPause() {
        super.onPause()
        // Detach UI listener to save battery. The Service still counts in the background!
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // The background service just posted a new step! Update our massive white text.
        if (key == "currentSteps" || key == "stepGoal" || key == "animStyle" || key == "weight") {
            updateStepUI()
        }
    }

    private fun updateStepUI() {
        val currentSteps = sharedPreferences.getInt("currentSteps", 0)
        val goal = sharedPreferences.getInt("stepGoal", 10000)
        val weight = sharedPreferences.getFloat("weight", 70f)
        
        val oldSteps = stepsTextView.text.toString().toIntOrNull() ?: 0
        stepsTextView.text = currentSteps.toString()
        titleTextView.text = "Today's Progress (Goal: $goal)"
        
        // Pop heartbeat animation when steps increase
        if (currentSteps > oldSteps && currentSteps > 0) {
            stepsTextView.animate()
                .scaleX(1.3f).scaleY(1.3f)
                .setDuration(150)
                .withEndAction {
                    stepsTextView.animate()
                        .scaleX(1.0f).scaleY(1.0f)
                        .setDuration(150)
                        .start()
                }.start()
        }
        
        // Accurate Caloric Equation relative to body mass:
        // Calories = Steps * (Weight(kg) * 0.0005)
        val calories = currentSteps * (weight * 0.0005f)
        val caloriesTextView = findViewById<TextView>(R.id.tv_calories)
        caloriesTextView.text = "🔥 ${calories.toInt()} kcal burned"
        
        val lottieView = findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.lottie_walking)
        val animStyle = sharedPreferences.getString("animStyle", "walking")
        
        val resName = when(animStyle) {
            "jogging" -> "jogging_animation"
            "sprinting" -> "sprinting_animation"
            else -> "walking_animation"
        }
        
        // Safely attempts to load the new JSON animation dynamically by filename
        val animResId = resources.getIdentifier(resName, "raw", packageName)
        if (animResId != 0) {
            lottieView.setAnimation(animResId)
            lottieView.playAnimation() // Ensure it loops properly
        }
    }
}
