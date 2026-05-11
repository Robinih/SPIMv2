package com.cvsuagritech.spim

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.PestDetailBottomSheet
import com.cvsuagritech.spim.models.PestRecord
import com.cvsuagritech.spim.utils.PestDictionary
import com.cvsuagritech.spim.views.ResultsOverlayView
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class InteractiveResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_DETECTIONS_JSON = "detections_json"
        const val EXTRA_LABELS_JSON = "labels_json"
    }

    data class SerializableDetection(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val confidence: Float,
        val classId: Int
    )

    private lateinit var ivImage: PhotoView
    private lateinit var overlay: ResultsOverlayView
    private lateinit var tvDetectionCount: TextView
    private lateinit var fabSaveAll: ExtendedFloatingActionButton

    private var originalBitmap: Bitmap? = null
    private var imagePath: String? = null
    private var detections: List<SerializableDetection> = emptyList()
    private var labels: List<String> = emptyList()
    
    private val currentMatrix = Matrix()

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.cvsuagritech.spim.utils.LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interactive_result)

        initViews()
        parseIntentData()
        setupOverlay()
    }

    private fun initViews() {
        ivImage = findViewById(R.id.iv_captured_image)
        overlay = findViewById(R.id.overlay)
        tvDetectionCount = findViewById(R.id.tv_detection_count)
        fabSaveAll = findViewById(R.id.fab_save_all)

        // Back button
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Send matrix updates down to drawing overlay continuously
        ivImage.setOnMatrixChangeListener {
            ivImage.getDisplayMatrix(currentMatrix)
            overlay.updateMatrix(currentMatrix)
        }

        // Catch taps and open pest detail bottom sheets
        ivImage.setOnViewTapListener { _, x, y ->
            val index = overlay.findDetectionAt(x, y)
            if (index != null) {
                showBottomSheet(detections[index])
            }
        }
    }

    private fun parseIntentData() {
        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        val detectionsJson = intent.getStringExtra(EXTRA_DETECTIONS_JSON) ?: "[]"
        val labelsJson = intent.getStringExtra(EXTRA_LABELS_JSON) ?: "[]"

        // Decode the image from the temporary file
        imagePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                originalBitmap = BitmapFactory.decodeFile(path)
                ivImage.setImageBitmap(originalBitmap)
            } else {
                Toast.makeText(this, "Failed to load image from cache", Toast.LENGTH_SHORT).show()
            }
        }

        // Parse labels
        labels = try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(labelsJson, type)
        } catch (e: Exception) {
            Log.e("InteractiveResult", "Failed to parse labels: ${e.message}")
            emptyList()
        }

        // Parse detections
        detections = try {
            val type = object : TypeToken<List<SerializableDetection>>() {}.type
            Gson().fromJson(detectionsJson, type)
        } catch (e: Exception) {
            Log.e("InteractiveResult", "Failed to parse detections: ${e.message}")
            emptyList()
        }

        // Update count badge
        val count = detections.size
        tvDetectionCount.text = when {
            count == 0 -> "No pests detected"
            count == 1 -> "1 pest detected"
            else -> "$count pests detected"
        }

        // Show Save All FAB if there are any detections
        if (detections.isNotEmpty()) {
            fabSaveAll.visibility = View.VISIBLE
            fabSaveAll.setOnClickListener {
                saveAllToHistory()
            }
        }
    }

    private fun setupOverlay() {
        originalBitmap?.let { 
            val translatedLabels = labels.map { label -> 
                val info = PestDictionary.lookup(label)
                val displayName = PestDictionary.getDisplayName(this@InteractiveResultActivity, info.scientificName)
                displayName.ifBlank { label }
            }
            overlay.setDetections(detections, translatedLabels)
            
            // Push initial frame matrix state
            ivImage.post {
                if (!isDestroyed) {
                    ivImage.getDisplayMatrix(currentMatrix)
                    overlay.updateMatrix(currentMatrix)
                }
            }
        }
    }

    private fun getCompressedImageBytes(): ByteArray? {
        return try {
            val bitmap = originalBitmap ?: return null
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun showBottomSheet(det: SerializableDetection) {
        val compressedBytes = getCompressedImageBytes()
        val rawLabel = if (det.classId in labels.indices) labels[det.classId] else "Unknown"

        val sheet = PestDetailBottomSheet.newInstance(
            rawLabel,
            det.confidence,
            compressedBytes
        )

        sheet.show(supportFragmentManager, "PestDetail")
    }

    // ============================================================
    //  SAVE ALL LOGIC
    // ============================================================

    private fun saveAllToHistory() {
        fabSaveAll.isEnabled = false
        fabSaveAll.text = "Saving..."
        
        val db = PestDatabaseHelper(this)
        val bitmap = originalBitmap

        lifecycleScope.launch(Dispatchers.IO) {
            var savedCount = 0
            
            try {
                for (det in detections) {
                    val label = if (det.classId in labels.indices) labels[det.classId] else "Unknown"
                    val pestInfo = PestDictionary.lookup(label)

                    // --- Crop bitmap to bounding box with clamping ---
                    val croppedBytes: ByteArray? = if (bitmap != null) {
                        val x = det.left.toInt().coerceAtLeast(0)
                        val y = det.top.toInt().coerceAtLeast(0)
                        val widthPx = (det.right - det.left).toInt()
                        val heightPx = (det.bottom - det.top).toInt()
                        
                        val w = widthPx.coerceAtMost(bitmap.width - x).coerceAtLeast(0)
                        val h = heightPx.coerceAtMost(bitmap.height - y).coerceAtLeast(0)

                        if (w > 0 && h > 0) {
                            val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
                            val stream = ByteArrayOutputStream()
                            cropped.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                            stream.toByteArray()
                        } else {
                            // Degenerate bbox — skip cropping, save null
                            null
                        }
                    } else {
                        // fallback if bitmap is magically null
                        getCompressedImageBytes()
                    }

                    val record = PestRecord(
                        pestName = PestDictionary.getDisplayName(this@InteractiveResultActivity, pestInfo.scientificName),
                        confidence = det.confidence,
                        imageBlob = croppedBytes,
                        timestamp = System.currentTimeMillis(),
                        notes = "Detected: ${pestInfo.scientificName} (Auto-saved from Identify session)"
                    )
                    
                    if (db.insertPestRecord(record) != -1L) {
                        savedCount++
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@InteractiveResultActivity, "Saved $savedCount detections to history!", Toast.LENGTH_SHORT).show()
                    fabSaveAll.text = "Saved"
                }
                
            } catch (e: Exception) {
                Log.e("SaveAll", "Error saving to history", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@InteractiveResultActivity, "Failed to save detections", Toast.LENGTH_SHORT).show()
                    fabSaveAll.isEnabled = true
                    fabSaveAll.text = "Save All"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Delete the temporary file from cache
        imagePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
