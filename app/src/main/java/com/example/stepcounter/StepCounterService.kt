package com.example.stepcounter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// THE POWERHOUSE OF THE APP
class StepCounterService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    private var previousTotalSteps = 0f
    private var totalSteps = 0f
    private var lastSavedDate = ""
    private var currentSessionSteps = 0

    private val CHANNEL_ID = "StepCounterChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        loadData()
        
        // Android requires a Notification Channel to display a persistent notification
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification(currentSessionSteps)
        
        // Pin the service to the foreground with our custom notification! 
        // This stops the battery saver from killing the app, AND shows on Lock Screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires exactly what TYPE of foreground service this is.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // We bind the hardware sensor to THIS background service.
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // If killed by extreme memory pressure, OS will try to restart it automatically
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            totalSteps = it.values[0]
            
            // Re-use our proven Midnight Rollover Logic
            handleMidnightReset()

            if (previousTotalSteps == 0f) {
                previousTotalSteps = totalSteps
                saveData()
            }

            var currentSteps = totalSteps.toInt() - previousTotalSteps.toInt()
            if (currentSteps < 0) {
                previousTotalSteps = totalSteps
                currentSteps = 0
            }
            
            // ONLY execute expensive writes if the step count actually moved upwards!
            if (currentSteps != currentSessionSteps) {
                currentSessionSteps = currentSteps
                
                // Write to phone storage so our UI screen instantly updates
                saveData() 
                
                // Blast the updated number straight to the Lock Screen / AOD!
                updateNotification() 
            }
        }
    }

    // --- MIDNIGHT LOGIC ---
    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun handleMidnightReset() {
        val today = getTodayDateString()
        if (today != lastSavedDate) {
            
            // --- NEW FIREBASE CLOUD SYNC AUTOMATION ---
            // Just before we reset the steps, grab the current user and push it to their Cloud Space!
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user != null && currentSessionSteps > 0) {
                // We securely use their Google `uid` so nobody else can see the data!
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                
                val weight = getSharedPreferences("myPrefs", Context.MODE_PRIVATE).getFloat("weight", 70f)
                
                val dailyRecord = hashMapOf(
                    "date" to lastSavedDate, // The day that just finished
                    "steps" to currentSessionSteps,
                    "calories" to (currentSessionSteps * (weight * 0.0005f)).toInt()
                )
                
                db.collection("users").document(user.uid)
                  .collection("history").document(lastSavedDate)
                  .set(dailyRecord)
            }
            // ------------------------------------------

            previousTotalSteps = totalSteps
            lastSavedDate = today
            currentSessionSteps = 0
            saveData()
            updateNotification()
        }
    }

    // --- DATA ---
    private fun saveData() {
        val prefs = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("key1", previousTotalSteps)
            putString("lastSavedDate", lastSavedDate)
            putInt("currentSteps", currentSessionSteps)
        }.apply() // .apply() is an asynchronous fast-save, preventing UI lag.
    }

    private fun loadData() {
        val prefs = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        previousTotalSteps = prefs.getFloat("key1", 0f)
        lastSavedDate = prefs.getString("lastSavedDate", getTodayDateString()) ?: getTodayDateString()
        currentSessionSteps = prefs.getInt("currentSteps", 0)
    }

    // --- ALWAYS ON DISPLAY NOTIFICATION ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Tracker Tracker",
                NotificationManager.IMPORTANCE_LOW // Low priority: Shows quietly on Lock Screen/AOD without buzzing
            ).apply {
                description = "Shows your daily steps directly on the lock screen."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(steps: Int): Notification {
        // If they tap the lock-screen widget, open the main App UI.
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mathematically animate the icon based on the parity of the current step count!
        val iconRes = if (steps % 2 == 0) R.drawable.ic_run_frame_1 else R.drawable.ic_run_frame_2

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("👟 Steps Today: $steps") 
            .setContentText("Walking to stay healthy!")
            .setSmallIcon(iconRes)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // It cannot be swiped away!
            .setOnlyAlertOnce(true) // Updates silently so it doesn't vibrate their pocket every step
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // CRUCIAL: Makes it visible on Lock Screen!
            .build()
    }

    private fun updateNotification() {
        // Fire the new Notification block up to the Android System manager
        val notification = createNotification(currentSessionSteps)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
        
        // --- NEW: Live-update the Android Home Screen Widget ---
        val intent = Intent(this, StepWidgetProvider::class.java)
        intent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = android.appwidget.AppWidgetManager.getInstance(application).getAppWidgetIds(android.content.ComponentName(application, StepWidgetProvider::class.java))
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    // We don't use direct binding. We use SharedPreferences to decouple everything!
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this) // Prevent memory leaks if the OS forcefully kills us
    }
}
