package com.cvsuagritech.spim.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.cvsuagritech.spim.InteractiveResultActivity
import com.cvsuagritech.spim.R

class ResultsOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val transformationMatrix = Matrix()
    private var detections: List<InteractiveResultActivity.SerializableDetection> = emptyList()
    private var labels: List<String> = emptyList()

    private val paintBox = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val paintBg = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintText = Paint().apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val classColorResIds = intArrayOf(
        R.color.bbox_class_0,  // Crambidae
        R.color.bbox_class_1,  // Noctuidae
        R.color.bbox_class_2,  // Delphacidae
        R.color.bbox_class_3,  // Cicadellidae
        R.color.bbox_class_4,  // Hesperiidae
        R.color.bbox_class_5,  // Ampullariidae
        R.color.bbox_class_6,  // Pentatomidae
        R.color.bbox_class_7,  // Pyrgomorphidae
        R.color.bbox_class_8,  // Chrysomelidae
        R.color.bbox_class_9,  // Acrididae
        R.color.bbox_class_10  // Coreidae
    )

    fun setDetections(
        newDetections: List<InteractiveResultActivity.SerializableDetection>,
        newLabels: List<String>
    ) {
        this.detections = newDetections
        this.labels = newLabels
        invalidate()
    }

    fun updateMatrix(matrix: Matrix) {
        this.transformationMatrix.set(matrix)
        invalidate()
    }

    fun findDetectionAt(x: Float, y: Float): Int? {
        val mappedRect = RectF()
        // Search backwards so topmost drawn detection is matched first
        for (i in detections.indices.reversed()) {
            val det = detections[i]
            val originalRect = RectF(det.left, det.top, det.right, det.bottom)
            transformationMatrix.mapRect(mappedRect, originalRect)
            
            // Inflate boundary slightly to make tapping easier (e.g. 25px pad)
            mappedRect.inset(-25f, -25f)
            
            if (mappedRect.contains(x, y)) {
                return i
            }
        }
        return null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty()) return

        // We want the text and stroke width to remain consistent physically on the screen,
        // regardless of zoom level. So instead of canvas.concat(transformationMatrix) which scales
        // everything (including line width), we map the YOLO box's points manually to the View's coordinates.

        val mappedRect = RectF()
        // Calculate a reasonable stroke and text size based on view width
        paintBox.strokeWidth = width / 100f
        paintText.textSize = width / 30f

        for (det in detections) {
            val colorResId = if (det.classId in classColorResIds.indices) classColorResIds[det.classId] else R.color.primary_green
            val color = ContextCompat.getColor(context, colorResId)

            paintBox.color = color
            paintBg.color = color

            val originalRect = RectF(det.left, det.top, det.right, det.bottom)
            transformationMatrix.mapRect(mappedRect, originalRect)

            canvas.drawRect(mappedRect, paintBox)

            val name = if (det.classId in labels.indices) labels[det.classId] else "Unknown"
            val text = "$name ${"%.0f%%".format(det.confidence * 100)}"
            val textWidth = paintText.measureText(text)
            val textHeight = paintText.textSize

            // Draw label background
            canvas.drawRect(
                mappedRect.left,
                mappedRect.top - textHeight - 10,
                mappedRect.left + textWidth + 10,
                mappedRect.top,
                paintBg
            )
            // Draw label text
            canvas.drawText(text, mappedRect.left + 5, mappedRect.top - 10, paintText)
        }
    }
}
