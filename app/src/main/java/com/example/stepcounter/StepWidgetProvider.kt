package com.example.stepcounter

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class StepWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Iterate through all instances of the widget placed on the home screen
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
            val steps = prefs.getInt("currentSteps", 0)

            val views = RemoteViews(context.packageName, R.layout.widget_step_counter)
            views.setTextViewText(R.id.tv_widget_steps, "$steps")

            // Clicking the widget boldly returns you to the main app dashboard
            val intent = Intent(context, LoginActivity::class.java) // Use LoginActivity to respect routing
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            
            // Attach the click handler to the entire layout
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
