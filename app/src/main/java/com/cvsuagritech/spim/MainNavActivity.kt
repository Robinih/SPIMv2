package com.cvsuagritech.spim

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.api.AppNotification
import com.cvsuagritech.spim.api.RetrofitClient
import com.cvsuagritech.spim.databinding.ActivityMainNavBinding
import com.cvsuagritech.spim.fragments.ConfirmationFragment
import com.cvsuagritech.spim.fragments.HistoryFragment
import com.cvsuagritech.spim.fragments.HomeFragment
import com.cvsuagritech.spim.fragments.SettingsFragment
import com.cvsuagritech.spim.fragments.StatisticsFragment
import com.cvsuagritech.spim.fragments.pestpages.AphidsDetailsFragment
import com.cvsuagritech.spim.fragments.pestpages.LeafBeetleDetailsFragment
import com.cvsuagritech.spim.fragments.pestpages.PygmyGrasshopperDetailsFragment
import com.cvsuagritech.spim.fragments.pestpages.SlantFacedGrasshopperDetailsFragment
import com.cvsuagritech.spim.utils.LanguageManager
import com.cvsuagritech.spim.utils.SessionManager
import com.cvsuagritech.spim.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainNavActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainNavBinding
    private lateinit var sessionManager: SessionManager
    private var currentTutorialStep = 0
    private var isFirstSync = true
    
    private val tutorialSteps by lazy {
        listOf(
            TutorialStep(R.id.nav_home, getString(R.string.tutorial_step1_title), getString(R.string.tutorial_step1_desc), R.id.header_layout),
            TutorialStep(R.id.nav_home, getString(R.string.tutorial_step2_title), getString(R.string.tutorial_step2_desc), R.id.card_identify),
            TutorialStep(R.id.nav_home, getString(R.string.tutorial_step3_title), getString(R.string.tutorial_step3_desc), R.id.card_count),
            TutorialStep(R.id.nav_home, getString(R.string.tutorial_step1_title), getString(R.string.tutorial_step1_desc), R.id.tv_library_label),
            
            TutorialStep(R.id.nav_history, getString(R.string.tutorial_step5_title), getString(R.string.tutorial_step5_desc), R.id.nav_history),
            TutorialStep(R.id.nav_history, getString(R.string.tutorial_step5_sync_title), getString(R.string.tutorial_step5_sync_desc), R.id.btn_sync),
            
            TutorialStep(R.id.nav_stats, getString(R.string.tutorial_step6_title), getString(R.string.tutorial_step6_desc), R.id.nav_stats),
            
            TutorialStep(R.id.nav_settings, getString(R.string.tutorial_step7_title), getString(R.string.tutorial_step7_desc), R.id.nav_settings),
            
            TutorialStep(R.id.nav_home, getString(R.string.tutorial_step8_title), getString(R.string.tutorial_step8_desc), R.id.btn_tutorial)
        )
    }

    data class TutorialStep(val tabId: Int, val title: String, val desc: String, val viewId: Int)

    override fun attachBaseContext(newBase: Context) {
        // Critical: Apply the language wrap before the activity initializes
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.initializeTheme(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainNavBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupBottomNavigation()
        setupTutorialLogic()
        createNotificationChannel()

        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = R.id.nav_home
        }

        // Auto-start tutorial for first-time users
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_time_global_tutorial", true)) {
            binding.root.postDelayed({ startTutorial() }, 1000)
            prefs.edit().putBoolean("first_time_global_tutorial", false).apply()
        }

        // Start notification polling if logged in
        if (sessionManager.isLoggedIn()) {
            checkAndRequestNotificationPermission()
            isFirstSync = true
            startNotificationPolling()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_history -> HistoryFragment()
                R.id.nav_stats -> StatisticsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> null
            }
            fragment?.let { 
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, it)
                    .commit()
            }
            true
        }
    }

    private fun setupTutorialLogic() {
        binding.btnTutorialNext.setOnClickListener {
            currentTutorialStep++
            if (currentTutorialStep < tutorialSteps.size) {
                updateTutorialStep()
            } else {
                endTutorial()
            }
        }

        binding.btnTutorialSkip.setOnClickListener {
            endTutorial()
        }
    }

    fun startTutorial() {
        currentTutorialStep = 0
        binding.tutorialOverlay.visibility = View.VISIBLE
        updateTutorialStep()
    }

    private fun updateTutorialStep() {
        val step = tutorialSteps[currentTutorialStep]
        
        if (binding.bottomNavigation.selectedItemId != step.tabId) {
            binding.bottomNavigation.selectedItemId = step.tabId
            binding.root.postDelayed({ performHighlight(step) }, 400)
        } else {
            performHighlight(step)
        }
    }

    private fun performHighlight(step: TutorialStep) {
        binding.tvTutorialTitle.text = step.title
        binding.tvTutorialDesc.text = step.desc
        
        val isLastStep = currentTutorialStep == tutorialSteps.size - 1
        binding.btnTutorialNext.text = if (isLastStep) getString(R.string.tutorial_btn_finish) else getString(R.string.tutorial_btn_next)
        binding.btnTutorialSkip.text = getString(R.string.tutorial_btn_skip)
        
        binding.btnTutorialSkip.visibility = if (isLastStep) View.GONE else View.VISIBLE

        val targetView = findViewById<View>(step.viewId) ?: return

        targetView.post {
            val location = IntArray(2)
            targetView.getLocationOnScreen(location)
            
            val overlayLocation = IntArray(2)
            binding.tutorialOverlay.getLocationOnScreen(overlayLocation)

            val padding = 20
            val x = (location[0] - overlayLocation[0]) - padding
            val y = (location[1] - overlayLocation[1]) - padding
            val width = targetView.width + (padding * 2)
            val height = targetView.height + (padding * 2)

            binding.tutorialHighlight.apply {
                val params = layoutParams as ViewGroup.MarginLayoutParams
                params.width = width
                params.height = height
                params.leftMargin = x
                params.topMargin = y
                layoutParams = params
            }
            
            val cardParams = binding.tutorialCard.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (y > binding.tutorialOverlay.height / 2) {
                cardParams.verticalBias = 0.2f
            } else {
                cardParams.verticalBias = 0.75f
            }
            binding.tutorialCard.layoutParams = cardParams
        }
    }

    private fun endTutorial() {
        binding.tutorialOverlay.visibility = View.GONE
        binding.bottomNavigation.selectedItemId = R.id.nav_home
    }

    fun navigateToConfirmation(imagePath: String, pestName: String, confidence: Float) {
        val fragment = ConfirmationFragment()
        val args = Bundle().apply {
            putString("imagePath", imagePath)
            putString("pestName", pestName)
            putFloat("confidence", confidence)
        }
        fragment.arguments = args
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun navigateToPestPage(pestName: String) {
        val fragment = when (pestName.lowercase()) {
            "leafbeetle" -> LeafBeetleDetailsFragment()
            "leafhopper", "aphids" -> AphidsDetailsFragment()
            "pygmygrasshopper" -> PygmyGrasshopperDetailsFragment()
            "slantfacedgrasshopper" -> SlantFacedGrasshopperDetailsFragment()
            else -> HomeFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    // --- Notification System ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SPIM Alerts"
            val descriptionText = "Important alerts about pest activity and weather"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("SPIM_ALERTS", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startNotificationPolling() {
        lifecycleScope.launch {
            while (true) {
                val userId = sessionManager.getUserId()
                if (userId != -1) {
                    try {
                        val response = RetrofitClient.instance.getNotifications(userId)
                        if (response.isSuccessful) {
                            val notifications = response.body() ?: emptyList()
                            val lastId = sessionManager.getLastNotificationId()
                            
                            if (isFirstSync) {
                                notifications.maxByOrNull { it.id }?.let { 
                                    sessionManager.setLastNotificationId(it.id)
                                }
                                isFirstSync = false
                                Log.d("Notifications", "Initial sync complete.")
                            } else {
                                notifications.filter { it.id > lastId && !it.isRead }.forEach { 
                                    showSystemNotification(it)
                                    sessionManager.setLastNotificationId(it.id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Notifications", "Polling error: ${e.message}")
                    }
                }
                delay(30 * 1000) // Poll every 30 seconds
            }
        }
    }

    private fun showSystemNotification(notification: AppNotification) {
        val builder = NotificationCompat.Builder(this, "SPIM_ALERTS")
            .setSmallIcon(R.drawable.logo)
            .setContentTitle("${notification.level} Alert")
            .setContentText(notification.message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            val isPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this@MainNavActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (isPermissionGranted) {
                notify(notification.id, builder.build())
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    // Already granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show custom explanation dialog to user
                    showNotificationRationaleDialog()
                }
                else -> {
                    // Direct request
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun showNotificationRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notifications Required")
            .setMessage("SPIM needs notification access to send you real-time alerts about high pest activity in your area.")
            .setPositiveButton("Allow") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("No Thanks", null)
            .show()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            // Only toast if we're on Android 13+ where this matters
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // If they denied, we don't spam them, just log it.
                Log.w("Notifications", "User denied notification permission")
            }
        }
    }
}
