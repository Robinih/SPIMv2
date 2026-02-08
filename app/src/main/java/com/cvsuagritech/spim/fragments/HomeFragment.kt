package com.cvsuagritech.spim.fragments

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.cvsuagritech.spim.CountResultsActivity
import com.cvsuagritech.spim.MainNavActivity
import com.cvsuagritech.spim.RecommendationActivity
import com.cvsuagritech.spim.WelcomeActivity
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.adapters.Insect
import com.cvsuagritech.spim.adapters.InsectLibraryAdapter
import com.cvsuagritech.spim.api.AppNotification
import com.cvsuagritech.spim.api.RetrofitClient
import com.cvsuagritech.spim.databinding.FragmentHomeBinding
import com.cvsuagritech.spim.ml.PestClassification
import com.cvsuagritech.spim.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var isAnalyzing = false
    private lateinit var labels: List<String>
    private val imageSize = 224

    private enum class Mode { IDENTIFY, COUNT }
    private var currentMode = Mode.IDENTIFY
    
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
        loadLabels()
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
                val response = RetrofitClient.instance.getNotifications(userId)
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
                // 1. Fetch the latest notifications FIRST (to ensure we have history even if they are about to be marked as read)
                val response = RetrofitClient.instance.getNotifications(userId)
                
                if (response.isSuccessful && response.body() != null) {
                    notificationList = response.body()!!
                    
                    withContext(Dispatchers.Main) {
                        renderNotificationsDialog()
                    }

                    // 2. Mark all as read on server in the background AFTER we've fetched the list
                    RetrofitClient.instance.markAllNotificationsAsRead(userId)
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

        // Create custom dialog with ScrollView
        val scrollContainer = ScrollView(requireContext())
        val layoutContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        scrollContainer.addView(layoutContainer)
        
        // Show newest notifications first
        notificationList.sortedByDescending { it.id }.forEach { notif ->
            val notifView = LayoutInflater.from(requireContext()).inflate(R.layout.item_notification, layoutContainer, false)
            
            val tvLevel = notifView.findViewById<TextView>(R.id.tv_notif_level)
            val tvTimestamp = notifView.findViewById<TextView>(R.id.tv_notif_timestamp)
            val tvMessage = notifView.findViewById<TextView>(R.id.tv_notif_message)
            val tvFrom = notifView.findViewById<TextView>(R.id.tv_notif_from)
            
            // Set level badge
            tvLevel.text = notif.level.uppercase()
            val colorRes = when (notif.level.lowercase()) {
                "high" -> R.color.error_red
                "medium" -> R.color.warning_orange
                else -> R.color.primary_green
            }
            tvLevel.backgroundTintList = ContextCompat.getColorStateList(requireContext(), colorRes)
            
            // Set timestamp
            tvTimestamp.text = notif.timestamp
            
            // Set message (bold if unread)
            tvMessage.text = notif.message
            if (!notif.isRead) {
                tvMessage.setTypeface(null, android.graphics.Typeface.BOLD)
                tvMessage.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            } else {
                tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
                tvMessage.alpha = 0.7f
            }
            
            // Set from info - Show who triggered the alert
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
        val insects = listOf(
            Insect("Aphids", R.drawable.aphids),
            Insect("Leaf Beetle", R.drawable.leafbettle),
            Insect("Pygmy Grasshopper", R.drawable.pygmy),
            Insect("Slant-faced Grasshopper", R.drawable.slantfaced),
            Insect(getString(R.string.home_insect_recommendations), R.drawable.ic_info, isRecommendation = true)
        )

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
                (activity as? MainNavActivity)?.navigateToPestPage(insect.name.replace(" ", "").replace("-", ""))
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
        binding.cardIdentify.setOnClickListener {
            currentMode = Mode.IDENTIFY
            showInputSourceDialog()
        }

        binding.cardCount.setOnClickListener {
            currentMode = Mode.COUNT
            showInputSourceDialog()
        }

        binding.btnTutorial.setOnClickListener {
            (activity as? MainNavActivity)?.startTutorial()
        }
        
        binding.btnNotifications.setOnClickListener {
            showNotificationsDialog()
        }
    }

    private fun loadLabels() {
        try {
            labels = requireContext().assets.open("labels.txt").bufferedReader().use { it.readLines() }
        } catch (e: IOException) {
            labels = emptyList()
        }
    }

    private fun showInputSourceDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_input_source)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnCamera = dialog.findViewById<Button>(R.id.btn_dialog_camera)
        val btnGallery = dialog.findViewById<Button>(R.id.btn_dialog_gallery)

        btnCamera.setOnClickListener {
            requestCameraPermission()
            dialog.dismiss()
        }

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        btnGallery.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                galleryPermissionLauncher.launch(permission)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePicturePreview.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        onresult.launch(intent)
    }

    private fun handleSelectedImage(bitmap: Bitmap) {
        binding.ImageView.setImageBitmap(bitmap)
        if (currentMode == Mode.IDENTIFY) {
            analyzeCurrentImage()
        } else {
            val imagePath = saveBitmapToCache(bitmap)
            if (imagePath != null) {
                val intent = Intent(requireContext(), CountResultsActivity::class.java).apply {
                    putExtra("imagePath", imagePath)
                }
                startActivity(intent)
            }
        }
    }

    private fun analyzeCurrentImage() {
        val drawable = binding.ImageView.drawable
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            if (bitmap != null) {
                outputGenerator(bitmap)
            }
        }
    }

    private fun outputGenerator(bitmap: Bitmap) {
        if (isAnalyzing) return
        isAnalyzing = true
        binding.loadingOverlay.visibility = View.VISIBLE

        try {
            val model = PestClassification.newInstance(requireContext())
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
            val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
            byteBuffer.order(ByteOrder.nativeOrder())
            val intValues = IntArray(imageSize * imageSize)
            resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)
            var pixel = 0
            for (i in 0 until imageSize) {
                for (j in 0 until imageSize) {
                    val `val` = intValues[pixel++]
                    byteBuffer.putFloat(((`val` shr 16) and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat(((`val` shr 8) and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat((`val` and 0xFF) * (1f / 255f))
                }
            }

            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, imageSize, imageSize, 3), DataType.FLOAT32)
            inputFeature0.loadBuffer(byteBuffer)

            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences = outputFeature0.floatArray
            var maxPos = 0
            var maxConfidence = 0f
            for (i in confidences.indices) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i]
                    maxPos = i
                }
            }

            val predictedLabel = labels[maxPos].trim()
            val imagePath = saveBitmapToCache(bitmap)

            requireActivity().runOnUiThread {
                binding.loadingOverlay.visibility = View.GONE
                isAnalyzing = false
                if (imagePath != null) {
                    (activity as? MainNavActivity)?.navigateToConfirmation(imagePath, predictedLabel, maxConfidence)
                }
            }
            model.close()
        } catch (e: Exception) {
            requireActivity().runOnUiThread {
                binding.loadingOverlay.visibility = View.GONE
                isAnalyzing = false
            }
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): String? {
        val fileName = if (currentMode == Mode.IDENTIFY) "temp_confirmation_image.png" else "temp_count_image.png"
        val imageFile = File(requireContext().cacheDir, fileName)
        return try {
            FileOutputStream(imageFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            imageFile.absolutePath
        } catch (e: IOException) { null }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicturePreview.launch(null)
    }

    private val galleryPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openGallery()
    }

    private val takePicturePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) handleSelectedImage(bitmap)
    }

    private val onresult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val bitmap = BitmapFactory.decodeStream(requireContext().contentResolver.openInputStream(uri))
                handleSelectedImage(bitmap)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
