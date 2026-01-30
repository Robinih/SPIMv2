package com.cvsuagritech.spim

import android.app.Activity
import android.content.ContentValues
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
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cvsuagritech.spim.databinding.ActivityMainBinding
import com.cvsuagritech.spim.ml.BirdsModel
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val GALLERY_REQUEST_CODE = 133
    private var isAnalyzing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupClickListeners()
        updateStatus(getString(R.string.status_ready))
    }

    private fun setupUI() {
        // Initialize UI state
        binding.loadingOverlay.visibility = View.GONE
        binding.confidenceContainer.visibility = View.GONE
        binding.resultActions.visibility = View.GONE
        
        // Set initial text
        binding.tvOutput.text = getString(R.string.placeholder_result)
        binding.confidencePercentage.text = getString(R.string.placeholder_confidence)
    }

    private fun setupClickListeners() {
        // Camera button
        binding.btnCaptureImage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                takePicturePreview.launch(null)
            } else {
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }

        // Gallery button
        binding.btnLoadImage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openGallery()
            } else {
                requestStoragePermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Auto-analyze when image is loaded
        binding.ImageView.setOnClickListener {
            if (binding.ImageView.drawable != null && 
                binding.ImageView.drawable !is android.graphics.drawable.ColorDrawable) {
                analyzeCurrentImage()
            }
        }

        // Search online button
        binding.btnSearchOnline.setOnClickListener {
            searchOnline(binding.tvOutput.text.toString())
        }

        // Retry button
        binding.btnRetry.setOnClickListener {
            clearImage()
        }

        // Result text click to search
        binding.tvOutput.setOnClickListener {
            if (binding.tvOutput.text != getString(R.string.placeholder_result)) {
                searchOnline(binding.tvOutput.text.toString())
            }
        }

        // Long press image to save
        binding.ImageView.setOnLongClickListener {
            if (binding.ImageView.drawable != null && 
                binding.ImageView.drawable !is android.graphics.drawable.ColorDrawable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showSaveDialog()
                } else {
                    storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            return@setOnLongClickListener true
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        onresult.launch(intent)
    }

    private fun clearImage() {
        binding.ImageView.setImageResource(R.drawable.place_holder)
        binding.tvOutput.text = getString(R.string.placeholder_result)
        binding.confidenceContainer.visibility = View.GONE
        binding.resultActions.visibility = View.GONE
        updateStatus(getString(R.string.status_ready))
    }

    private fun analyzeCurrentImage() {
        val drawable = binding.ImageView.drawable
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            if (bitmap != null) {
                outputGenerator(bitmap)
            } else {
                showError(getString(R.string.error_no_image))
            }
        } else {
            showError(getString(R.string.error_no_image))
        }
    }

    private fun searchOnline(query: String) {
        if (query.isNotEmpty() && query != getString(R.string.placeholder_result)) {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${query} pest identification")
            )
            startActivity(intent)
        }
    }

    private fun updateStatus(status: String) {
        binding.statusIndicator.text = status
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        updateStatus(getString(R.string.status_error))
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        updateStatus(getString(R.string.status_complete))
    }

    // Permission request launchers
    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                takePicturePreview.launch(null)
            } else {
                showError(getString(R.string.permission_denied))
            }
        }

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openGallery()
            } else {
                showError(getString(R.string.permission_denied))
            }
        }

    // Camera launcher
    private val takePicturePreview =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                binding.ImageView.setImageBitmap(bitmap)
                showImageActions()
                updateStatus(getString(R.string.status_ready))
            }
        }

    // Gallery launcher
    private val onresult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.i("TAG", "This is the result: ${result.data} ${result.resultCode}")
            onResultReceived(GALLERY_REQUEST_CODE, result)
        }

    private fun onResultReceived(requestCode: Int, result: ActivityResult?) {
        when (requestCode) {
            GALLERY_REQUEST_CODE -> {
                if (result?.resultCode == Activity.RESULT_OK) {
                    result?.data?.data?.let { uri ->
                        Log.i("TAG", "OnResultReceived: $uri")
                        try {
                            val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                            if (bitmap != null) {
                                binding.ImageView.setImageBitmap(bitmap)
                                showImageActions()
                                updateStatus(getString(R.string.status_ready))
                            } else {
                                showError(getString(R.string.error_image_load_failed))
                            }
                        } catch (e: Exception) {
                            Log.e("TAG", "Error loading image: ${e.message}")
                            showError(getString(R.string.error_image_load_failed))
                        }
                    }
                } else {
                    Log.e("TAG", "onActivityResult: error in selecting image")
                    showError(getString(R.string.error_image_load_failed))
                }
            }
        }
    }

    private fun showImageActions() {
        // Auto-analyze when image is loaded
        analyzeCurrentImage()
    }

    private fun outputGenerator(bitmap: Bitmap) {
        if (isAnalyzing) return
        
        isAnalyzing = true
        binding.loadingOverlay.visibility = View.VISIBLE
        updateStatus(getString(R.string.status_analyzing))
        
        try {
            // Declaring tensor flow lite model variable
            val model = BirdsModel.newInstance(this)

            // Converting bitmap into tensor flow image
            val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val tfimage = TensorImage.fromBitmap(newBitmap)

            // Process the image using trained model and sort it in descending order
            val outputs = model.process(tfimage)
                .probabilityAsCategoryList.apply {
                    sortByDescending { it.score }
                }

            // Getting result having high probability
            val highProbabilityOutput = outputs[0]
            val confidence = (highProbabilityOutput.score * 100).roundToInt()

            // Update UI on main thread
            runOnUiThread {
                binding.loadingOverlay.visibility = View.GONE
                
                // Set result text
                binding.tvOutput.text = highProbabilityOutput.label
                
                // Show confidence
                binding.confidenceContainer.visibility = View.VISIBLE
                binding.confidencePercentage.text = "$confidence%"
                binding.confidenceProgress.progress = confidence
                
                // Show result actions
                binding.resultActions.visibility = View.VISIBLE
                
                updateStatus(getString(R.string.status_complete))
                isAnalyzing = false
                
                Log.i("TAG", "Analysis complete: $highProbabilityOutput with confidence: $confidence%")
            }

            // Release model resources
            model.close()

        } catch (e: Exception) {
            Log.e("TAG", "Error during analysis: ${e.message}")
            runOnUiThread {
                binding.loadingOverlay.visibility = View.GONE
                showError(getString(R.string.error_analysis_failed))
                isAnalyzing = false
            }
        }
    }

    // Storage permission launcher for saving images
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                showSaveDialog()
            } else {
                showError(getString(R.string.permission_denied))
            }
        }

    // Helper function to show the save confirmation dialog
    private fun showSaveDialog() {
        val drawable = binding.ImageView.drawable as? BitmapDrawable
        val bitmapToSave = drawable?.bitmap

        if (bitmapToSave == null) {
            showError(getString(R.string.error_no_image))
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_save_title))
            .setMessage(getString(R.string.dialog_save_message))
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                downloadImage(bitmapToSave)
            }
            .setNegativeButton(getString(R.string.dialog_no)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // Modern function to save a bitmap to the device's gallery
    private fun downloadImage(bitmap: Bitmap) {
        val fileName = "SPIM_Pest_Image_${System.currentTimeMillis()}.png"
        val resolver = contentResolver

        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(imageCollection, contentValues)

        if (imageUri == null) {
            showError(getString(R.string.error_save_failed))
            Log.e("MainActivity", "Failed to create new MediaStore record.")
            return
        }

        try {
            resolver.openOutputStream(imageUri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                    throw IOException("Couldn't save bitmap.")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            showSuccess(getString(R.string.error_save_success))
        } catch (e: IOException) {
            // If something went wrong, delete the pending entry.
            resolver.delete(imageUri, null, null)
            showError(getString(R.string.error_save_failed))
            Log.e("MainActivity", "Failed to save bitmap.", e)
        }
    }
}