package com.cvsuagritech.spim

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.cvsuagritech.spim.api.RegisterDeviceTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainNavActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainNavBinding
    private lateinit var sessionManager: SessionManager
    private var currentTutorialStep = 0
    
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

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_time_global_tutorial", true)) {
            binding.root.postDelayed({ startTutorial() }, 1000)
            prefs.edit().putBoolean("first_time_global_tutorial", false).apply()
        }

        if (sessionManager.isLoggedIn()) {
            checkAndRequestNotificationPermission()
            startNotificationPolling()
            
            // Register FCM token with backend
            registerFcmToken()
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
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
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
                            
                            Log.d("Notifications", "Fetched ${notifications.size} alerts. lastSeenId=$lastId")

                            // Only show system tray notifications for UNREAD alerts that are NEWER than what we've seen.
                            // Facebook-style: alerts stay in history, but only pop up in bar once.
                            val toNotify = notifications.filter { !it.isRead && it.id > lastId }
                            
                            if (toNotify.isNotEmpty()) {
                                Log.d("Notifications", "Pushing ${toNotify.size} to system bar")
                                toNotify.forEach { showSystemNotification(it) }
                            }

                            // Update the tracking ID to ensure we don't repeat alerts in the next poll
                            notifications.maxByOrNull { it.id }?.let { maxNotif ->
                                if (maxNotif.id > lastId) {
                                    sessionManager.setLastNotificationId(maxNotif.id)
                                }
                            }
                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e("Notifications", "Server returned error: $errorBody")
                        }
                    } catch (e: Exception) {
                        Log.e("Notifications", "Polling network/parsing error: ${e.message}")
                    }
                }
                delay(30 * 1000) 
            }
        }
    }

    private fun showSystemNotification(notification: AppNotification) {
        val intent = Intent(this, MainNavActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "SPIM_ALERTS")
            .setSmallIcon(R.drawable.ic_notifications) 
            .setContentTitle("${notification.level} Alert")
            .setContentText(notification.message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(this, R.color.primary_green))
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        with(NotificationManagerCompat.from(this)) {
            val isPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this@MainNavActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (isPermissionGranted) {
                notify(notification.id, builder.build())
            } else {
                Log.w("Notifications", "Permission not granted to show notification bar alert")
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {}
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    showNotificationRationaleDialog()
                }
                else -> {
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
        }
    }

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d("FCM", "FCM Token: $token")

            // Send token to backend
            val userId = sessionManager.getUserId()
            if (userId != -1) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val response = RetrofitClient.instance.registerDeviceToken(
                            RegisterDeviceTokenRequest(userId, token)
                        )
                        if (response.isSuccessful) {
                            Log.d("FCM", "Token registered with server successfully")
                            sessionManager.clearPendingFcmToken()
                        } else {
                            Log.e("FCM", "Failed to register token: ${response.errorBody()?.string()}")
                        }
                    } catch (e: Exception) {
                        Log.e("FCM", "Error registering FCM token: ${e.message}")
                    }
                }
            }
        }
    }
}
