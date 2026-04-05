package com.example.stepcounter

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

object CustomUpdateEngine {

    // The current hardcoded version of THIS build. (e.g., v1.0)
    private const val CURRENT_VERSION = 1.0

    fun checkForUpdates(activity: Activity) {
        val db = FirebaseFirestore.getInstance()
        
        // Submits a silent background check to Firebase on the global configuration document
        db.collection("app_config").document("versions")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val latestVersion = document.getDouble("latest_version") ?: 1.0
                    val downloadUrl = document.getString("download_url") ?: ""
                    val releaseNotes = document.getString("release_notes") ?: "Bug fixes and improvements."

                    if (latestVersion > CURRENT_VERSION && downloadUrl.isNotEmpty()) {
                        // The Cloud is newer! Check we haven't already finished the activity and moved to MainActivity
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            showUpdateDialog(activity, latestVersion, releaseNotes, downloadUrl)
                        }
                    }
                }
            }
    }

    private fun showUpdateDialog(activity: Activity, latestVersion: Double, notes: String, url: String) {
        AlertDialog.Builder(activity)
            .setTitle("🚀 Major Update v$latestVersion Available!")
            .setMessage("$notes\n\nYou must securely download and install this Over-The-Air update to continue using NebulaStep.")
            .setCancelable(false) // Force them to update
            .setPositiveButton("Download & Install") { _, _ ->
                downloadUpdate(activity, url)
            }
            .show()
    }

    private fun downloadUpdate(activity: Activity, url: String) {
        Toast.makeText(activity, "Downloading Update Background...", Toast.LENGTH_LONG).show()

        // Utilize Android's Native Download Manager to securely fetch the new .apk file
        val request = DownloadManager.Request(Uri.parse(url))
        request.setTitle("NebulaStep Update")
        request.setDescription("Downloading latest version...")
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NebulaStep_Update.apk")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        // Listen aggressively for when the download completes to trigger the OS Installer!
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(context)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        // registerReceiver on modern Android APIs needs explicit export flags, but for demo purposes this is fine
        activity.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun installApk(context: Context) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NebulaStep_Update.apk")
        
        // We use a FileProvider to securely pass the downloaded .apk into Android's Package Installer
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Install failed: Ensure 'Install Unknown Apps' is enabled.", Toast.LENGTH_LONG).show()
        }
    }
}
