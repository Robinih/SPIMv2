package com.cvsuagritech.spim

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cvsuagritech.spim.adapters.BreakdownItem
import com.cvsuagritech.spim.adapters.CountBreakdownAdapter
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.databinding.ActivityCountResultBinding
import com.cvsuagritech.spim.models.HistoryItem
import com.cvsuagritech.spim.utils.PestDictionary
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class CountResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCountResultBinding
    private lateinit var dbHelper: PestDatabaseHelper

    private var rawBitmap: Bitmap? = null
    private var imagePath: String? = null
    private var annotatedBitmap: Bitmap? = null
    private var totalCount: Int = 0
    private var breakdownString: String = ""

    // Class-specific bounding box color resources (classId 0–10)
    private val classColorResIds = intArrayOf(
        R.color.bbox_class_0,  // Curculionidae → Red
        R.color.bbox_class_1,  // Delphacidae → Blue
        R.color.bbox_class_2,  // Cicadellidae → Green
        R.color.bbox_class_3,  // Phlaeothripidae → Orange
        R.color.bbox_class_4,  // Cecidomyiidae → Purple
        R.color.bbox_class_5,  // Hesperiidae → Teal
        R.color.bbox_class_6,  // Crambidae → Pink
        R.color.bbox_class_7,  // Chloropidae → Indigo
        R.color.bbox_class_8,  // Ephydridae → Amber
        R.color.bbox_class_9,  // Noctuidae → Cyan
        R.color.bbox_class_10  // Thripidae → Light Green
    )

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.cvsuagritech.spim.utils.LanguageManager.wrap(newBase))
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_DETECTIONS_JSON = "extra_detections_json"
        const val EXTRA_LABELS_JSON = "extra_labels_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCountResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = PestDatabaseHelper(this)

        setupToolbar()
        loadAndProcessData()
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.btnDone.setOnClickListener {
            finish()
        }

        binding.btnSaveHistory.setOnClickListener {
            saveToHistory()
        }

        // Full-screen image viewing on annotated image tap
        binding.ivAnnotatedImage.setOnClickListener {
            val bmp = annotatedBitmap ?: return@setOnClickListener
            binding.ivAnnotatedImage.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val file = java.io.File(cacheDir, "fullscreen_temp.jpg")
                    val stream = java.io.FileOutputStream(file)
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    stream.flush()
                    stream.close()

                    withContext(Dispatchers.Main) {
                        binding.ivAnnotatedImage.isEnabled = true
                        val intent = Intent(this@CountResultActivity, FullScreenImageActivity::class.java)
                        intent.putExtra(FullScreenImageActivity.EXTRA_IMAGE_PATH, file.absolutePath)
                        startActivity(intent)
                    }
                } catch(e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.ivAnnotatedImage.isEnabled = true
                        Toast.makeText(this@CountResultActivity, "Error opening image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadAndProcessData() {
        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        val detectionsJson = intent.getStringExtra(EXTRA_DETECTIONS_JSON)
        val labelsJson = intent.getStringExtra(EXTRA_LABELS_JSON)

        if (imagePath == null || detectionsJson == null || labelsJson == null) {
            Toast.makeText(this, "Failed to load count data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        rawBitmap = BitmapFactory.decodeFile(imagePath)

        val detectionListType = object : TypeToken<List<InteractiveResultActivity.SerializableDetection>>() {}.type
        val detections: List<InteractiveResultActivity.SerializableDetection> = Gson().fromJson(detectionsJson, detectionListType)

        val labelsListType = object : TypeToken<List<String>>() {}.type
        val labels: List<String> = Gson().fromJson(labelsJson, labelsListType)

        totalCount = detections.size
        binding.tvTotalCount.text = totalCount.toString()

        // Group by label ID
        val groupedCount = detections.groupingBy { it.classId }.eachCount()

        val breakdownItems = mutableListOf<BreakdownItem>()
        val breakdownMap = mutableMapOf<String, Int>()

        for ((classId, count) in groupedCount) {
            val label = if (classId in labels.indices) labels[classId] else "Unknown"
            // Use PestDictionary to get the friendly common name!
            val pestInfo = PestDictionary.lookup(label)
            val displayName = pestInfo.commonName.ifBlank { label }

            val colorResId = if (classId in classColorResIds.indices) classColorResIds[classId] else R.color.primary_green

            breakdownItems.add(BreakdownItem(displayName, count, colorResId))
            breakdownMap[displayName] = count
        }
        
        breakdownItems.sortByDescending { it.count }

        breakdownString = Gson().toJson(breakdownMap)

        // Setup RecyclerView
        binding.rvBreakdown.layoutManager = LinearLayoutManager(this)
        binding.rvBreakdown.adapter = CountBreakdownAdapter(breakdownItems) { clickedItem ->
            val bottomSheet = PestDetailBottomSheet.newInstance(
                label = clickedItem.name,
                confidence = 0f, // No individual confidence available in group count
                imageBytes = null, // Can't easily crop individual insect, so pass null
                isFromHistory = true // Treat as history so it hides save buttons
            )
            bottomSheet.show(supportFragmentManager, "PestDetailBottomSheet")
        }

        if (breakdownItems.isEmpty()) {
            binding.tvBreakdownLabel.visibility = View.GONE
            binding.rvBreakdown.visibility = View.GONE
        }

        // Draw bounding boxes on rawBitmap with class-specific colors
        annotatedBitmap = drawDetections(rawBitmap!!, detections, labels)
        binding.ivAnnotatedImage.setImageBitmap(annotatedBitmap)
    }

    private fun drawDetections(
        originalBitmap: Bitmap,
        detections: List<InteractiveResultActivity.SerializableDetection>,
        labels: List<String>
    ): Bitmap {
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val strokeWidth = mutableBitmap.width / 100f

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = mutableBitmap.width / 30f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        for (det in detections) {
            // Get class-specific color
            val colorResId = if (det.classId in classColorResIds.indices) classColorResIds[det.classId] else R.color.primary_green
            val boxColor = ContextCompat.getColor(this, colorResId)

            val boxPaint = Paint().apply {
                color = boxColor
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
            }

            val bgPaint = Paint().apply {
                color = boxColor
                style = Paint.Style.FILL
            }

            canvas.drawRect(det.left, det.top, det.right, det.bottom, boxPaint)

            // Draw label text above box
            val name = if (det.classId in labels.indices) {
                val pestInfo = PestDictionary.lookup(labels[det.classId])
                pestInfo.commonName.ifBlank { labels[det.classId] }
            } else "Unknown"

            val text = "$name ${"%.0f%%".format(det.confidence * 100)}"
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.textSize

            canvas.drawRect(
                det.left,
                det.top - textHeight - 10,
                det.left + textWidth + 10,
                det.top,
                bgPaint
            )
            canvas.drawText(text, det.left + 5, det.top - 10, textPaint)
        }

        return mutableBitmap
    }

    private fun saveToHistory() {
        val finalBitmap = annotatedBitmap ?: rawBitmap ?: return

        binding.btnSaveHistory.isEnabled = false
        binding.btnSaveHistory.text = "Saving..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Compress for DB
                val stream = ByteArrayOutputStream()
                val resizedBitmap = getResizedBitmap(finalBitmap, 800)
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                val imageBlob = stream.toByteArray()

                val item = HistoryItem.CountItem(
                    id = 0, // Auto-generated by DB
                    totalCount = totalCount,
                    breakdown = breakdownString,
                    imagePath = "", // Unused now in favor of BLOB
                    imageBlob = imageBlob,
                    timestamp = System.currentTimeMillis()
                )

                dbHelper.insertCountRecord(item)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CountResultActivity, "Saved to History!", Toast.LENGTH_SHORT).show()
                    binding.btnSaveHistory.isEnabled = false
                    binding.btnSaveHistory.text = "Saved"
                }
            } catch (e: Exception) {
                Log.e("CountResultActivity", "Failed to save history: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.btnSaveHistory.isEnabled = true
                    binding.btnSaveHistory.text = "Save to History"
                    Toast.makeText(this@CountResultActivity, "Failed to save history", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getResizedBitmap(image: Bitmap, maxSize: Int): Bitmap {
        var width = image.width
        var height = image.height
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        imagePath?.let { path ->
            val file = java.io.File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}

