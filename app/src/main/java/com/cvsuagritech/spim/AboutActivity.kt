package com.cvsuagritech.spim

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cvsuagritech.spim.databinding.ActivityAboutBinding
import com.cvsuagritech.spim.utils.LanguageManager
import com.cvsuagritech.spim.utils.ThemeManager

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.initializeTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set version info
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvAboutVersion.text = "Version ${packageInfo.versionName}"
        } catch (_: Exception) {
            binding.tvAboutVersion.text = "Version 1.0"
        }

        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
