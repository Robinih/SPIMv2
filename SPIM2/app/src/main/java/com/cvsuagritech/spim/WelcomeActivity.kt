package com.cvsuagritech.spim

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cvsuagritech.spim.databinding.ActivityWelcomeBinding
import com.cvsuagritech.spim.utils.LanguageManager
import com.cvsuagritech.spim.utils.ThemeManager

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved language preference
        val savedLanguage = LanguageManager.getCurrentLanguage(this)
        LanguageManager.setLanguage(this, savedLanguage)
        
        // Apply saved theme preference
        ThemeManager.initializeTheme(this)
        
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnGetStarted.setOnClickListener {
            // Navigate to main navigation activity
            val intent = Intent(this, MainNavActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
