package com.cvsuagritech.spim

import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.databinding.ActivityCountResultsBinding
import com.cvsuagritech.spim.models.HistoryItem
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class CountResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCountResultsBinding
    private var interpreter: Interpreter? = null
    private val inputSize = 640
    private lateinit var labels: List<String>
    private lateinit var databaseHelper: PestDatabaseHelper
    
    private val beneficialInsects = listOf("pygmygrasshopper", "pygmy grasshopper")
    
    // Scaling metadata for accurate box placement
    private var scale: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCountResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        databaseHelper = PestDatabaseHelper(this)
        loadLabels()
        val imagePath = intent.getStringExtra("imagePath")
        if (imagePath == null) {
            Toast.makeText(this, "No image found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            try {
                initModel()
                val bitmap = loadBitmap(imagePath)
                if (bitmap != null) {
                    processImage(bitmap)
                }
            } catch (e: Exception) {
                Log.e("CountResults", "Error: ${e.message}")
                Toast.makeText(this@CountResultsActivity, "Processing error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadLabels() {
        try {
            labels = assets.open("labels.txt").bufferedReader().use { it.readLines() }.map { it.trim() }
        } catch (e: Exception) {
            labels = listOf("leafbeetle", "leafhopper", "pygmygrasshopper", "slantfacedgrasshopper")
        }
    }

    private suspend fun initModel() = withContext(Dispatchers.IO) {
        interpreter = Interpreter(loadModelFile())
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = assets.openFd("PestDetection.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private suspend fun loadBitmap(path: String): Bitmap? = withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inMutable = true })
    }

    private suspend fun processImage(bitmap: Bitmap) {
        // Use Letterbox resizing to preserve aspect ratio
        val letterboxed = createLetterboxBitmap(bitmap)
        val byteBuffer = convertBitmapToByteBuffer(letterboxed)

        withContext(Dispatchers.Default) {
            val outputTensor = interpreter?.getOutputTensor(0)
            val shape = outputTensor?.shape() ?: intArrayOf(1, 8, 8400)
            val outputBuffer = ByteBuffer.allocateDirect(outputTensor?.numBytes() ?: 0).order(ByteOrder.nativeOrder())
            
            interpreter?.run(byteBuffer, outputBuffer)
            outputBuffer.rewind()
            
            val floatArray = FloatArray((outputTensor?.numBytes() ?: 0) / 4)
            outputBuffer.asFloatBuffer().get(floatArray)

            val detections = parseDetections(floatArray, shape)

            withContext(Dispatchers.Main) {
                displayResults(bitmap, detections)
            }
        }
    }

    private fun createLetterboxBitmap(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)

        scale = inputSize.toFloat() / source.width.coerceAtLeast(source.height)
        val w = source.width * scale
        val h = source.height * scale
        offsetX = (inputSize - w) / 2f
        offsetY = (inputSize - h) / 2f

        val matrix = Matrix()
        matrix.postScale(scale, scale)
        matrix.postTranslate(offsetX, offsetY)
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return result
    }

    private fun parseDetections(floatArray: FloatArray, shape: IntArray): List<Detection> {
        val detections = mutableListOf<Detection>()
        val confThreshold = 0.12f // Optimized threshold to catch small insects
        
        val isTransposed = shape[1] > shape[2]
        val dim1 = if (isTransposed) shape[2] else shape[1] 
        val dim2 = if (isTransposed) shape[1] else shape[2] 

        for (i in 0 until dim2) {
            var maxConf = 0f
            var classId = -1
            for (j in 4 until dim1) {
                val score = if (isTransposed) floatArray[i * dim1 + j] else floatArray[j * dim2 + i]
                if (score > maxConf) {
                    maxConf = score
                    classId = j - 4
                }
            }

            if (maxConf > confThreshold) {
                var cx = if (isTransposed) floatArray[i * dim1 + 0] else floatArray[0 * dim2 + i]
                var cy = if (isTransposed) floatArray[i * dim1 + 1] else floatArray[1 * dim2 + i]
                var w = if (isTransposed) floatArray[i * dim1 + 2] else floatArray[2 * dim2 + i]
                var h = if (isTransposed) floatArray[i * dim1 + 3] else floatArray[3 * dim2 + i]

                // Heuristic: Some models output normalized coordinates (0-1), others absolute (0-640)
                if (cx < 2f && w < 2f) {
                    cx *= inputSize
                    cy *= inputSize
                    w *= inputSize
                    h *= inputSize
                }

                // Map back to original image space
                val realX = (cx - offsetX) / scale
                val realY = (cy - offsetY) / scale
                val realW = w / scale
                val realH = h / scale

                detections.add(Detection(
                    RectF(realX - realW/2, realY - realH/2, realX + realW/2, realY + realH/2),
                    maxConf, classId
                ))
            }
        }
        return applyNMS(detections)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()
        val active = BooleanArray(sorted.size) { true }
        for (i in sorted.indices) {
            if (active[i]) {
                selected.add(sorted[i])
                for (j in i + 1 until sorted.size) {
                    if (calculateIoU(sorted[i].boundingBox, sorted[j].boundingBox) > 0.45f) active[j] = false
                }
            }
        }
        return selected
    }

    private fun calculateIoU(r1: RectF, r2: RectF): Float {
        val intersection = RectF()
        return if (intersection.setIntersect(r1, r2)) {
            val iArea = intersection.width() * intersection.height()
            iArea / (r1.width() * r1.height() + r2.width() * r2.height() - iArea)
        } else 0f
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            byteBuffer.putFloat((pixel and 0xFF) / 255f)
        }
        return byteBuffer
    }

    private fun displayResults(originalBitmap: Bitmap, detections: List<Detection>) {
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        val redBoxPaint = Paint().apply { 
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = mutableBitmap.width / 80f 
        }
        val greenBoxPaint = Paint().apply { 
            color = Color.parseColor("#4CAF50")
            style = Paint.Style.STROKE
            strokeWidth = mutableBitmap.width / 80f 
        }
        val textPaint = Paint().apply { 
            color = Color.WHITE
            textSize = mutableBitmap.width / 25f
            typeface = Typeface.DEFAULT_BOLD 
        }
        val redBgPaint = Paint().apply { 
            color = Color.RED
            style = Paint.Style.FILL 
        }
        val greenBgPaint = Paint().apply { 
            color = Color.parseColor("#4CAF50")
            style = Paint.Style.FILL 
        }

        // We'll store a list of breakdown items including name, count, and average confidence
        data class BreakdownItem(val name: String, var count: Int = 0, var totalConf: Float = 0f)
        val breakdownItems = mutableMapOf<String, BreakdownItem>()

        for (det in detections) {
            val name = if (det.classId in labels.indices) labels[det.classId] else "Pest"
            val isBeneficial = beneficialInsects.any { name.lowercase().contains(it) }
            
            // Choose colors based on beneficial status
            val boxPaint = if (isBeneficial) greenBoxPaint else redBoxPaint
            val bgPaint = if (isBeneficial) greenBgPaint else redBgPaint
            
            canvas.drawRect(det.boundingBox, boxPaint)
            
            val item = breakdownItems.getOrPut(name) { BreakdownItem(name) }
            item.count++
            item.totalConf += det.confidence
            
            val text = "$name ${"%.0f%%".format(det.confidence * 100)}"
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.textSize
            
            canvas.drawRect(
                det.boundingBox.left, 
                det.boundingBox.top - textHeight - 10, 
                det.boundingBox.left + textWidth + 10, 
                det.boundingBox.top, 
                bgPaint
            )
            canvas.drawText(text, det.boundingBox.left + 5, det.boundingBox.top - 10, textPaint)
        }

        binding.resultImageView.setImageBitmap(mutableBitmap)
        binding.tvTotalCount.text = "Total Insects Detected: ${detections.size}"
        
        // Save to history with proper JSON format including confidence
        // Format: {"name": {"count": X, "confidence": Y}}
        val exportMap = breakdownItems.mapValues { (_, item) ->
            mapOf("count" to item.count, "confidence" to (item.totalConf / item.count))
        }
        
        // Vertical list display
        val breakdownText = if (exportMap.isEmpty()) {
            "No insects detected."
        } else {
            exportMap.entries.sortedByDescending { (it.value["count"] as Int) }
                .joinToString("\n") { "• ${it.key}: ${it.value["count"]} (${"%.0f%%".format((it.value["confidence"] as Float) * 100)} conf)" }
        }
        binding.tvBreakdown.text = breakdownText
        binding.progressBar.visibility = View.GONE

        saveCountToHistory(mutableBitmap, detections.size, exportMap)
    }

    private fun saveCountToHistory(bitmap: Bitmap, totalCount: Int, countMap: Map<String, Any>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val imageByteArray = stream.toByteArray()

                // Crucial fix: Send proper JSON breakdown to the website
                val gson = Gson()
                val breakdownJson = gson.toJson(countMap)

                val countItem = HistoryItem.CountItem(
                    id = 0,
                    totalCount = totalCount,
                    breakdown = breakdownJson,
                    imageBlob = imageByteArray,
                    timestamp = System.currentTimeMillis()
                )
                
                val result = databaseHelper.insertCountRecord(countItem)
                withContext(Dispatchers.Main) {
                    if (result != -1L) {
                        Log.d("CountResults", "Count saved to history successfully")
                    } else {
                        Log.e("CountResults", "Failed to save count to history")
                    }
                }
            } catch (e: Exception) {
                Log.e("CountResults", "Error saving to history: ${e.message}")
            }
        }
    }

    data class Detection(val boundingBox: RectF, val confidence: Float, val classId: Int)

    override fun onDestroy() {
        interpreter?.close()
        super.onDestroy()
    }
}
