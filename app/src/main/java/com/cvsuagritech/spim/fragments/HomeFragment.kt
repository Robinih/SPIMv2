package com.cvsuagritech.spim.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.cvsuagritech.spim.MainNavActivity
import com.cvsuagritech.spim.RecommendationActivity
import com.cvsuagritech.spim.ScannerActivity
import com.cvsuagritech.spim.WelcomeActivity
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.adapters.Insect
import com.cvsuagritech.spim.adapters.InsectLibraryAdapter
import com.cvsuagritech.spim.api.AppNotification
import com.cvsuagritech.spim.api.RetrofitClient
import com.cvsuagritech.spim.databinding.FragmentHomeBinding
import com.cvsuagritech.spim.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var notificationList: List<AppNotification> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUserHeader()
        setupInsectLibrary()
        setupClickListeners()
        loadNotifications()
    }

    private fun setupUserHeader() {
        val sessionManager = SessionManager(requireContext())
        val username = sessionManager.getUsername()
        if (username != null) {
            binding.tvWelcomeUser.text = getString(R.string.home_welcome_user, username)
        } else {
            binding.tvWelcomeUser.text = getString(R.string.home_welcome_guest)
        }
    }

    private fun loadNotifications() {
        val sessionManager = SessionManager(requireContext())
        if (!sessionManager.isLoggedIn()) return
        val userId = sessionManager.getUserId()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.getNotifications(userId, true)
                if (response.isSuccessful && response.body() != null) {
                    notificationList = response.body()!!

                    val unreadCount = notificationList.count { !it.isRead }
                    withContext(Dispatchers.Main) {
                        binding.notificationBadge.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error loading notifications: ${e.message}")
            }
        }
    }

    private fun showNotificationsDialog() {
        val sessionManager = SessionManager(requireContext())
        if (!sessionManager.isLoggedIn()) return
        val userId = sessionManager.getUserId()

        // Facebook Style: Hide badge immediately when user clicks the bell
        binding.notificationBadge.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.getNotifications(userId, true)

                if (response.isSuccessful && response.body() != null) {
                    notificationList = response.body()!!

                    withContext(Dispatchers.Main) {
                        renderNotificationsDialog()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (notificationList.isNotEmpty()) renderNotificationsDialog()
                        else Toast.makeText(requireContext(), "No notifications available", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Dialog Load Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (notificationList.isNotEmpty()) renderNotificationsDialog()
                    else Toast.makeText(requireContext(), "Error connecting to server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderNotificationsDialog() {
        if (notificationList.isEmpty()) {
            Toast.makeText(requireContext(), "No notifications available", Toast.LENGTH_SHORT).show()
            return
        }

        val scrollContainer = ScrollView(requireContext())
        val layoutContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        scrollContainer.addView(layoutContainer)

        notificationList.sortedByDescending { it.id }.forEach { notif ->
            val notifView = LayoutInflater.from(requireContext()).inflate(R.layout.item_notification, layoutContainer, false)

            val tvLevel = notifView.findViewById<TextView>(R.id.tv_notif_level)
            val tvTimestamp = notifView.findViewById<TextView>(R.id.tv_notif_timestamp)
            val tvMessage = notifView.findViewById<TextView>(R.id.tv_notif_message)
            val tvFrom = notifView.findViewById<TextView>(R.id.tv_notif_from)

            tvLevel.text = notif.level.uppercase()
            val colorRes = when (notif.level.lowercase()) {
                "high" -> R.color.error_red
                "medium" -> R.color.warning_orange
                "low" -> R.color.warning_yellow
                else -> R.color.primary_green
            }
            tvLevel.backgroundTintList = ContextCompat.getColorStateList(requireContext(), colorRes)

            tvTimestamp.text = notif.timestamp
            tvMessage.text = notif.message
            if (!notif.isRead) {
                tvMessage.setTypeface(null, android.graphics.Typeface.BOLD)
                tvMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            } else {
                tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
                tvMessage.alpha = 0.7f
            }

            tvFrom.text = "From: ${notif.fromUser ?: "System"}"
            layoutContainer.addView(notifView)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Notification History")
            .setView(scrollContainer)
            .setPositiveButton("Close") { _, _ ->
                loadNotifications()
            }
            .setNeutralButton("Clear All") { _, _ ->
                showClearNotificationsWarning()
            }
            .show()
    }

    private fun showClearNotificationsWarning() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Notifications")
            .setMessage("Are you sure you want to clear all your notification history? This cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearNotifications()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearNotifications() {
        val sessionManager = SessionManager(requireContext())
        val userId = sessionManager.getUserId()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.markAllNotificationsAsRead(userId)
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        notificationList = emptyList()
                        binding.notificationBadge.visibility = View.GONE
                        Toast.makeText(requireContext(), "Notification history cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to clear notifications", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupInsectLibrary() {
        val insects = mutableListOf<Insect>()
        
        // Dynamically add all pests from the dictionary
        val allEntries = com.cvsuagritech.spim.utils.PestDictionary.getAllEntries()
        for ((label, info) in allEntries) {
            val safeLabel = label.trim().lowercase().replace(" ", "_").replace("-", "")
            val drawableName = "ref_${safeLabel}_1"
            var resId = resources.getIdentifier(drawableName, "drawable", requireContext().packageName)
            if (resId == 0) resId = R.drawable.place_holder // fallback
            
            insects.add(Insect(info.commonName, resId, isRecommendation = false, pestLabel = label))
        }

        // Add the recommendation card at the end
        insects.add(Insect(getString(R.string.home_insect_recommendations), R.drawable.ic_info, isRecommendation = true))

        val adapter = InsectLibraryAdapter(insects) { insect ->
            if (insect.isRecommendation) {
                val sessionManager = SessionManager(requireContext())
                if (sessionManager.isLoggedIn()) {
                    val intent = Intent(requireContext(), RecommendationActivity::class.java)
                    startActivity(intent)
                } else {
                    showLoginRequiredDialog()
                }
            } else {
                (activity as? MainNavActivity)?.navigateToPestPage(insect.pestLabel)
            }
        }

        binding.rvPestLibrary.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPestLibrary.adapter = adapter
    }

    private fun showLoginRequiredDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_login_required_title))
            .setMessage(getString(R.string.dialog_login_required_message))
            .setPositiveButton(getString(R.string.btn_login)) { _, _ ->
                val intent = Intent(requireContext(), WelcomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun setupClickListeners() {
        // Both "Identify Pest" and "Count Insects" now launch the unified scanner
        binding.cardIdentify.setOnClickListener {
            launchScanner(ScannerActivity.MODE_IDENTIFY)
        }

        binding.cardCount.setOnClickListener {
            launchScanner(ScannerActivity.MODE_COUNT)
        }

        binding.btnTutorial.setOnClickListener {
            (activity as? MainNavActivity)?.startTutorial()
        }

        binding.btnNotifications.setOnClickListener {
            showNotificationsDialog()
        }
    }

    private fun launchScanner(mode: Int) {
        val intent = Intent(requireContext(), ScannerActivity::class.java).apply {
            putExtra(ScannerActivity.EXTRA_SCANNER_MODE, mode)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
