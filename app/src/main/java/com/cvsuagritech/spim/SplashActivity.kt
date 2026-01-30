package com.cvsuagritech.spim

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.utils.UpdateManager
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Experimental: Check for updates in the background
        val updateManager = UpdateManager(this)
        lifecycleScope.launch {
            updateManager.checkForUpdates()
        }

        // Delay for 3 seconds then navigate to WelcomeActivity
        // Increased delay slightly to give update dialog a chance to appear
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
            finish()
        }, 3000)
    }
}
