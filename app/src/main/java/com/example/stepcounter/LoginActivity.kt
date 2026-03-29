package com.example.stepcounter

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        
        // --- CUSTOM UPDATER ENGINE ---
        // Quietly pings your Firebase config to see if a massive new OTA update exists!
        CustomUpdateEngine.checkForUpdates(this)
        // -----------------------------
        
        // If the user's phone already has an active Cloud Login Token, instantly skip this screen!
        if (auth.currentUser != null) {
            proceedToNextScreen()
            return
        }

        setContentView(R.layout.activity_login)

        // Floating logo animation
        val ivLogo = findViewById<ImageView>(R.id.iv_logo)
        ObjectAnimator.ofFloat(ivLogo, "translationY", -20f, 20f).apply {
            duration = 2500
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }

        // The default_web_client_id is safely read directly from google-services.json
        val clientIdRes = resources.getIdentifier("default_web_client_id", "string", packageName)
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail()
        
        if (clientIdRes != 0) {
            gsoBuilder.requestIdToken(getString(clientIdRes))
        }
        
        val gso = gsoBuilder.build()
            
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<Button>(R.id.btn_google_sign_in).setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, 9001)
        }
        
        // Allows testing the hardware sensor immediately without configuring SHA-1 keys!
        findViewById<Button>(R.id.btn_guest).setOnClickListener {
            proceedToNextScreen()
        }
    }
    
    private fun proceedToNextScreen() {
        val sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val isSetupComplete = sharedPreferences.getBoolean("isSetupComplete", false)
        
        if (isSetupComplete) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...)
        if (requestCode == 9001) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful! Now authenticate with Firebase using those credentials.
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed: Are your API keys setup? ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Cloud Sync Enabled!", Toast.LENGTH_SHORT).show()
                    proceedToNextScreen()
                } else {
                    Toast.makeText(this, "Firebase Authentication Blocked. Check Console Rules.", Toast.LENGTH_LONG).show()
                }
            }
    }
}
