package com.cvsuagritech.spim

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.databinding.ActivityScannerBinding
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var interpreter: Interpreter? = null
    private lateinit var labels: List<String>

    // Model specs
    private val inputSize = 640
    private val iouThreshold = 0.45f   // NMS threshold to merge overlapping boxes for the same object
    private val MAX_DETECTIONS_PER_FRAME = 20  // Hard cap to prevent UI canvas OOM crashes

    // Beneficial insect families (update this list as needed)
    private val beneficialFamilies = listOf<String>()

    // Letterbox scaling metadata
    private var scale: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    // Mode flag from Intent
    private var scannerMode: Int = MODE_IDENTIFY

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.cvsuagritech.spim.utils.LanguageManager.wrap(newBase))
    }

    companion object {
        const val EXTRA_SCANNER_MODE = "extra_scanner_mode"
        const val MODE_IDENTIFY = 0
        const val MODE_COUNT = 1
        const val EXTRA_IMAGE_PATH = "extra_image_path"
    }

    // Store results for bottom sheet
    private var lastAnnotatedBitmap: Bitmap? = null
    private var lastDetections: List<Detection> = emptyList()

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    processImage(bitmap)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                Log.e("Scanner", "Gallery error: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scannerMode = intent.getIntExtra(EXTRA_SCANNER_MODE, MODE_IDENTIFY)

        cameraExecutor = Executors.newSingleThreadExecutor()
        loadLabels()

        setupClickListeners()
        checkCameraPermission()

        // Pre-load model on background thread
        lifecycleScope.launch {
            initModel()
        }

        setupOrientationListener()
    }

    private fun loadLabels() {
        labels = try {
            assets.open("labels.txt").bufferedReader().use { it.readLines() }.map { it.trim() }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e("Scanner", "Failed to load labels: ${e.message}")
            emptyList()
        }
        Log.d("Scanner", "Loaded ${labels.size} labels: $labels")
    }

    private suspend fun initModel() = withContext(Dispatchers.IO) {
        try {
            interpreter = Interpreter(loadModelFile())
            Log.d("Scanner", "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.e("Scanner", "Failed to load model: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ScannerActivity, "Failed to load detection model", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = assets.openFd("PestDetection.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        binding.btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.btnRetake.setOnClickListener {
            switchToCamera()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("Scanner", "Camera bind failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupOrientationListener() {
        val orientationEventListener = object : android.view.OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) {
                    return
                }
                
                val rotation = when (orientation) {
                    in 45..134 -> android.view.Surface.ROTATION_270
                    in 135..224 -> android.view.Surface.ROTATION_180
                    in 225..314 -> android.view.Surface.ROTATION_90
                    else -> android.view.Surface.ROTATION_0
                }
                imageCapture?.targetRotation = rotation
            }
        }
        orientationEventListener.enable()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        showLoading(true)

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        // Use CameraX's built-in toBitmap() — handles all format conversions
                        val rawBitmap = imageProxy.toBitmap()
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        imageProxy.close()

                        // Apply rotation if needed
                        val rotatedBitmap = if (rotationDegrees != 0) {
                            val matrix = Matrix()
                            matrix.postRotate(rotationDegrees.toFloat())
                            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                        } else {
                            rawBitmap
                        }

                        // Downscale if needed to avoid OOM during inference
                        val finalBitmap = downscaleBitmap(rotatedBitmap, 2048)
                        processImage(finalBitmap)
                    } catch (e: Exception) {
                        imageProxy.close()
                        showLoading(false)
                        Log.e("Scanner", "Failed to process captured image: ${e.message}", e)
                        Toast.makeText(this@ScannerActivity, "Failed to capture image", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    showLoading(false)
                    Log.e("Scanner", "Photo capture failed: ${exception.message}", exception)
                    Toast.makeText(this@ScannerActivity, "Photo capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    /**
     * Downscale a bitmap so its largest dimension does not exceed maxSize.
     */
    private fun downscaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun processImage(bitmap: Bitmap) {
        showLoading(true)

        lifecycleScope.launch {
            try {
                if (interpreter == null) {
                    initModel()
                }

                val detections = withContext(Dispatchers.Default) {
                    runInference(bitmap)
                }

                lastDetections = detections

                withContext(Dispatchers.Main) {
                    showLoading(false)

                    // Show dialog if no insects detected
                    if (detections.isEmpty()) {
                        showNoDetectionDialog()
                        return@withContext
                    }

                    if (scannerMode == MODE_COUNT) {
                        launchCountResult(bitmap, detections)
                    } else {
                        launchInteractiveResult(bitmap, detections)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@ScannerActivity, "Processing error: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("Scanner", "Inference error", e)
                }
            }
        }
    }

    private fun showNoDetectionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.no_detection_title))
            .setMessage(getString(R.string.no_detection_message))
            .setPositiveButton("Retake Photo") { dialog, _ ->
                dialog.dismiss()
                switchToCamera()
            }
            .setNegativeButton("Choose from Gallery") { dialog, _ ->
                dialog.dismiss()
                galleryLauncher.launch("image/*")
            }
            .setCancelable(true)
            .show()
    }

    private fun runInference(bitmap: Bitmap): List<Detection> {
        val letterboxed = createLetterboxBitmap(bitmap)
        val byteBuffer = convertBitmapToByteBuffer(letterboxed)

        val outputTensor = interpreter?.getOutputTensor(0)
        val shape = outputTensor?.shape() ?: intArrayOf(1, 15, 8400)
        val outputBuffer = ByteBuffer.allocateDirect(outputTensor?.numBytes() ?: 0)
            .order(ByteOrder.nativeOrder())

        interpreter?.run(byteBuffer, outputBuffer)
        outputBuffer.rewind()

        val floatArray = FloatArray((outputTensor?.numBytes() ?: 0) / 4)
        outputBuffer.asFloatBuffer().get(floatArray)

        Log.d("Scanner", "Output shape: ${shape.contentToString()}")
        return parseDetections(floatArray, shape)
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

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            byteBuffer.putFloat((pixel and 0xFF) / 255f)
        }
        return byteBuffer
    }

    /**
     * Returns the class-specific adaptive confidence threshold.
     * Tuned to morphological constraints from Chapter 4 Error Analysis.
     *
     * Tier 1 (High - 0.50): High Confidence Performers (TPR >= 95%)
     *   Crambidae (97%), Ampullariidae (96%), Hesperiidae (95%)
     * Tier 2 (Moderate - 0.40): Stable Performers (76-89% TPR)
     *   Coreidae (89%), Noctuidae (81%), Pentatomidae (81%),
     *   Pyrgomorphidae (77%), Delphacidae (76%)
     * Tier 3 (Sensitive - 0.25): Struggling Performers (<= 70% TPR)
     *   Cicadellidae (70%), Chrysomelidae (70%), Acrididae (69%)
     */
    private fun getAdaptiveThreshold(classId: Int): Float = when (classId) {
        // Tier 1 — High Confidence Performers
        0, 5, 4 -> 0.50f   // crambidae(0), ampullariidae(5), hesperiidae(4)
        // Tier 2 — Stable Performers
        10, 1, 6, 7, 2 -> 0.40f  // coreidae(10), noctuidae(1), pentatomidae(6), pyrgomorphidae(7), delphacidae(2)
        // Tier 3 — Struggling Performers (camouflaged pests)
        3, 8, 9 -> 0.25f  // cicadellidae(3), chrysomelidae(8), acrididae(9)
        // Fallback for any future classes
        else -> 0.40f
    }

    private fun parseDetections(floatArray: FloatArray, shape: IntArray): List<Detection> {
        val detections = mutableListOf<Detection>()

        // YOLOv8 output: [1, 4+numClasses, 8400]
        // shape[1] = 4 + numClasses (bbox coords + class confidences)
        // shape[2] = 8400 (number of detections)
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

            // --- Class-specific adaptive thresholding ---
            if (classId >= 0 && maxConf > getAdaptiveThreshold(classId)) {
                var cx = if (isTransposed) floatArray[i * dim1 + 0] else floatArray[0 * dim2 + i]
                var cy = if (isTransposed) floatArray[i * dim1 + 1] else floatArray[1 * dim2 + i]
                var w = if (isTransposed) floatArray[i * dim1 + 2] else floatArray[2 * dim2 + i]
                var h = if (isTransposed) floatArray[i * dim1 + 3] else floatArray[3 * dim2 + i]

                // Handle normalized coordinates (0-1) versus absolute (0-640)
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

                detections.add(
                    Detection(
                        boundingBox = RectF(
                            realX - realW / 2,
                            realY - realH / 2,
                            realX + realW / 2,
                            realY + realH / 2
                        ),
                        confidence = maxConf,
                        classId = classId
                    )
                )
            }
        }

        return applyNMS(detections)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (!active[i]) continue
            selected.add(sorted[i])

            // Hard cap: stop immediately once we hit the max
            if (selected.size >= MAX_DETECTIONS_PER_FRAME) break

            for (j in i + 1 until sorted.size) {
                if (active[j] && calculateIoU(sorted[i].boundingBox, sorted[j].boundingBox) > iouThreshold) {
                    active[j] = false
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

    // Class-specific bounding box colors (classId 0–10)
    private val classColors = intArrayOf(
        0xFFD81B60.toInt(), // 0: Crambidae → Pink
        0xFF00ACC1.toInt(), // 1: Noctuidae → Cyan
        0xFF1E88E5.toInt(), // 2: Delphacidae → Blue
        0xFF43A047.toInt(), // 3: Cicadellidae → Green
        0xFF00897B.toInt(), // 4: Hesperiidae → Teal
        0xFFE53935.toInt(), // 5: Ampullariidae → Red
        0xFF3949AB.toInt(), // 6: Pentatomidae → Indigo
        0xFFFB8C00.toInt(), // 7: Pyrgomorphidae → Orange
        0xFFFFB300.toInt(), // 8: Chrysomelidae → Amber
        0xFF8E24AA.toInt(), // 9: Acrididae → Purple
        0xFF7CB342.toInt()  // 10: Coreidae → Light Green
    )

    private fun drawDetections(originalBitmap: Bitmap, detections: List<Detection>): Bitmap {
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val strokeWidth = mutableBitmap.width / 80f

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = mutableBitmap.width / 30f
            typeface = Typeface.DEFAULT_BOLD
        }

        for (det in detections) {
            val boxColor = if (det.classId in classColors.indices) classColors[det.classId] else Color.RED

            val boxPaint = Paint().apply {
                color = boxColor
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
            }
            val bgPaint = Paint().apply {
                color = boxColor
                style = Paint.Style.FILL
            }

            canvas.drawRect(det.boundingBox, boxPaint)

            val name = if (det.classId in labels.indices) labels[det.classId] else "Unknown"
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

        return mutableBitmap
    }

    private fun launchInteractiveResult(originalBitmap: Bitmap, detections: List<Detection>) {
        // Serialize detections to JSON
        val serializableDetections = detections.map { det ->
            InteractiveResultActivity.SerializableDetection(
                left = det.boundingBox.left,
                top = det.boundingBox.top,
                right = det.boundingBox.right,
                bottom = det.boundingBox.bottom,
                confidence = det.confidence,
                classId = det.classId
            )
        }
        val detectionsJson = Gson().toJson(serializableDetections)
        val labelsJson = Gson().toJson(labels)

        // Compress original bitmap and save to temp file to avoid TransactionTooLargeException
        val tempFile = java.io.File(cacheDir, "spim_interactive_temp.jpg")
        java.io.FileOutputStream(tempFile).use { out ->
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        val intent = Intent(this, InteractiveResultActivity::class.java).apply {
            putExtra(InteractiveResultActivity.EXTRA_IMAGE_PATH, tempFile.absolutePath)
            putExtra(InteractiveResultActivity.EXTRA_DETECTIONS_JSON, detectionsJson)
            putExtra(InteractiveResultActivity.EXTRA_LABELS_JSON, labelsJson)
        }
        startActivity(intent)
    }

    /**
     * Serialize detections and launch the Count Result Activity.
     */
    private fun launchCountResult(originalBitmap: Bitmap, detections: List<Detection>) {
        // We'll use the same SerializableDetection structure for simplicity
        val serializableDetections = detections.map { det ->
            InteractiveResultActivity.SerializableDetection(
                left = det.boundingBox.left,
                top = det.boundingBox.top,
                right = det.boundingBox.right,
                bottom = det.boundingBox.bottom,
                confidence = det.confidence,
                classId = det.classId
            )
        }
        val detectionsJson = Gson().toJson(serializableDetections)
        val labelsJson = Gson().toJson(labels)

        // Compress original bitmap and save to temp file
        val tempFile = java.io.File(cacheDir, "spim_count_temp.jpg")
        java.io.FileOutputStream(tempFile).use { out ->
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        val intent = Intent(this, CountResultActivity::class.java).apply {
            putExtra(CountResultActivity.EXTRA_IMAGE_PATH, tempFile.absolutePath)
            putExtra(CountResultActivity.EXTRA_DETECTIONS_JSON, detectionsJson)
            putExtra(CountResultActivity.EXTRA_LABELS_JSON, labelsJson)
        }
        startActivity(intent)
    }

    private fun switchToResults(annotatedBitmap: Bitmap) {
        binding.previewView.visibility = View.GONE
        binding.controlsCamera.visibility = View.GONE
        binding.ivResult.setImageBitmap(annotatedBitmap)
        binding.ivResult.visibility = View.VISIBLE
        binding.controlsResult.visibility = View.VISIBLE
    }

    private fun switchToCamera() {
        binding.ivResult.visibility = View.GONE
        binding.controlsResult.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE
        binding.controlsCamera.visibility = View.VISIBLE
        lastAnnotatedBitmap = null
        lastDetections = emptyList()
    }

    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    data class Detection(val boundingBox: RectF, val confidence: Float, val classId: Int)

    override fun onDestroy() {
        interpreter?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
