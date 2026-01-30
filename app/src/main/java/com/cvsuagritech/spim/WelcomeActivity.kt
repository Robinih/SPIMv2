package com.cvsuagritech.spim

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cvsuagritech.spim.databinding.ActivityWelcomeBinding
import com.cvsuagritech.spim.utils.LanguageManager
import com.cvsuagritech.spim.utils.ThemeManager
import com.cvsuagritech.spim.utils.SessionManager

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var sessionManager: SessionManager

    override fun attachBaseContext(newBase: Context) {
        // Critical: Apply language wrapping to the activity context
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        ThemeManager.initializeTheme(this)
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)

        if (sessionManager.isLoggedIn()) {
            val intent = Intent(this, MainNavActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentLang = LanguageManager.getCurrentLanguage(this)
        updateLanguageButtonText(currentLang)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnContinueGuest.setOnClickListener {
            val intent = Intent(this, MainNavActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        binding.btnLanguage.setOnClickListener {
            toggleLanguage()
        }
        
        binding.btnGetStarted.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }
    }

    private fun toggleLanguage() {
        val currentLang = LanguageManager.getCurrentLanguage(this)
        val newLang = if (currentLang == "en") "tl" else "en"
        
        LanguageManager.setLanguage(this, newLang)
        
        // Clean restart of the Welcome Activity
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun updateLanguageButtonText(lang: String) {
        binding.btnLanguage.text = if (lang == "en") "EN | TL" else "TL | EN"
    }
}
