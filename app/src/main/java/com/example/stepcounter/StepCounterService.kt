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

    private val CHANNEL_ID = "StepCounterChannel_V2"
    private val NOTIFICATION_ID = 1

    private val timeBoundaryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Fires natively periodically by Android OS (every minute or date change).
            // We use this to detect sharp 12:00 AM midnights even if the user is sleeping and not generating sensor events!
            handleMidnightReset()
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        loadData()
        
        // Android requires a Notification Channel to display a persistent notification
        createNotificationChannel()

        // Register clock listener to catch exactly 12:00 AM without relying on sensor events!
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)       // Fires every minute exactly on the zero second
            addAction(Intent.ACTION_DATE_CHANGED)    // System date change
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        
        // Fix for Xiaomi / Android 14+ strict requirements for dynamic receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeBoundaryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timeBoundaryReceiver, filter)
        }
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
            // SENSOR_DELAY_UI requests a reliable hardware speed without triggering OS battery-throttling!
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
                // First ever boot!
                previousTotalSteps = totalSteps
                saveData()
            }

            var currentSteps = totalSteps.toInt() - previousTotalSteps.toInt()
            if (currentSteps < 0) {
                // ⚠️ THE USER REBOOTED THEIR PHONE! The hardware sensor dropped back to 0.
                // We MUST mathematically shift the previousTotalSteps down into the negative
                // so that when we subtract it from the NEW totalSteps, we get the exact same currentSessionSteps we had before the reboot!
                previousTotalSteps = totalSteps - currentSessionSteps
                currentSteps = totalSteps.toInt() - previousTotalSteps.toInt()
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
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createDynamicSmallIcon(steps: Int): androidx.core.graphics.drawable.IconCompat {
        val size = 128
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Draw the animating vector character firmly in the top half
        val iconRes = if (steps % 2 == 0) R.drawable.ic_run_frame_1 else R.drawable.ic_run_frame_2
        val drawable = androidx.core.content.ContextCompat.getDrawable(this, iconRes)
        if (drawable != null) {
            drawable.setBounds(32, 4, size - 32, (size / 2) + 20)
            drawable.setTint(android.graphics.Color.WHITE)
            drawable.draw(canvas)
        }

        // Draw the pure numeric step count in the bottom half
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textSize = if (steps.toString().length >= 5) 36f else 46f
        }

        val text = steps.toString()
        val xPos = (size / 2).toFloat()
        val yPos = size - 12f
        canvas.drawText(text, xPos, yPos, paint)

        return androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap)
    }

    private fun createNotification(steps: Int): Notification {
        // If they tap the lock-screen widget, open the main App UI.
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use our dynamic canvas injection to fuse the animation + steps into the AOD slot completely natively!
        val dynamicIcon = createDynamicSmallIcon(steps)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("👟 Steps Today: $steps") 
            .setContentText("Walking to stay healthy!")
            .setSmallIcon(dynamicIcon)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // It cannot be swiped away!
            .setOnlyAlertOnce(true) // Updates silently so it doesn't vibrate their pocket every step
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // CRUCIAL: Makes it visible on Lock Screen!
            .setCategory(NotificationCompat.CATEGORY_STATUS) // Pushes it further into persistent UI bounds
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
        unregisterReceiver(timeBoundaryReceiver) // Detach our midnight listener cleanly
    }
}
