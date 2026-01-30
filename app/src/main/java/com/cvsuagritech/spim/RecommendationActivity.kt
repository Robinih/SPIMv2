package com.cvsuagritech.spim

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.api.RetrofitClient
import com.cvsuagritech.spim.databinding.ActivityRecommendationBinding
import com.cvsuagritech.spim.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream

class RecommendationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecommendationBinding
    private lateinit var sessionManager: SessionManager
    private var selectedImageBytes: ByteArray? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleImageSelection(uri)
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            handleBitmap(bitmap)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecommendationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        setupClickListeners()
        setupAutoScroll()
    }

    private fun setupAutoScroll() {
        // Auto-scroll only for the description field as it is at the bottom
        binding.etDescription.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.scrollView.postDelayed({
                    binding.scrollView.smoothScrollTo(0, binding.tilDescription.top - 50)
                }, 300)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.cardImagePicker.setOnClickListener {
            showImageSourceDialog()
        }

        binding.btnEditImage.setOnClickListener {
            showImageSourceDialog()
        }

        binding.btnSubmit.setOnClickListener {
            validateAndSubmit()
        }
    }

    private fun showImageSourceDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_input_source)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnCamera = dialog.findViewById<Button>(R.id.btn_dialog_camera)
        val btnGallery = dialog.findViewById<Button>(R.id.btn_dialog_gallery)

        btnCamera.setOnClickListener {
            cameraLauncher.launch(null)
            dialog.dismiss()
        }

        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        btnGallery.setOnClickListener {
            galleryLauncher.launch(galleryIntent)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun handleImageSelection(uri: Uri) {
        binding.ivSelectedInsect.setImageURI(uri)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                selectedImageBytes = bitmapToByteArray(bitmap)
                withContext(Dispatchers.Main) {
                    updateImageUi(true)
                }
            } catch (e: Exception) {
                Log.e("Recommendation", "Image processing error: ${e.message}")
            }
        }
    }

    private fun handleBitmap(bitmap: Bitmap) {
        binding.ivSelectedInsect.setImageBitmap(bitmap)
        lifecycleScope.launch(Dispatchers.IO) {
            selectedImageBytes = bitmapToByteArray(bitmap)
            withContext(Dispatchers.Main) {
                updateImageUi(true)
            }
        }
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        return stream.toByteArray()
    }

    private fun updateImageUi(hasImage: Boolean) {
        if (hasImage) {
            binding.layoutPlaceholder.visibility = View.GONE
            binding.ivSelectedInsect.visibility = View.VISIBLE
            binding.btnEditImage.visibility = View.VISIBLE
            binding.tvImageError.visibility = View.GONE
        } else {
            binding.layoutPlaceholder.visibility = View.VISIBLE
            binding.ivSelectedInsect.visibility = View.GONE
            binding.btnEditImage.visibility = View.GONE
        }
    }

    private fun validateAndSubmit() {
        val name = binding.etInsectName.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        var isValid = true

        if (selectedImageBytes == null) {
            binding.tvImageError.visibility = View.VISIBLE
            isValid = false
        }

        if (description.isEmpty()) {
            binding.tilDescription.error = "Description is required"
            isValid = false
        } else {
            binding.tilDescription.error = null
        }

        if (isValid) {
            submitReport(if (name.isEmpty()) null else name, description)
        }
    }

    private fun submitReport(name: String?, description: String) {
        val userId = sessionManager.getUserId()
        if (userId == -1) {
            Toast.makeText(this, "User session error. Please login again.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmit.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userIdBody = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val nameBody = name?.toRequestBody("text/plain".toMediaTypeOrNull())
                val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
                
                val imagePart = selectedImageBytes?.let {
                    val requestFile = it.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("image", "rec_${System.currentTimeMillis()}.jpg", requestFile)
                }

                if (imagePart != null) {
                    val response = RetrofitClient.instance.submitRecommendation(userIdBody, nameBody, descBody, imagePart)
                    
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@RecommendationActivity, "Report Submitted Successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this@RecommendationActivity, "Submission failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecommendationActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnSubmit.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }
}
