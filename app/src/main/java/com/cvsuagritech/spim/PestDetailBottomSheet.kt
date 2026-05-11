package com.cvsuagritech.spim

import android.app.SearchManager
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cvsuagritech.spim.adapters.ReferenceImageAdapter
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.models.PestRecord
import com.cvsuagritech.spim.utils.PestDictionary
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet displaying detailed pest information for a selected detection.
 * Shows localized name, confidence, action buttons, description, and reference images.
 */
class PestDetailBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_LABEL = "label"
        private const val ARG_CONFIDENCE = "confidence"
        private const val ARG_IMAGE_BYTES = "image_bytes"
        private const val ARG_FROM_HISTORY = "from_history"

        fun newInstance(
            label: String,
            confidence: Float,
            imageBytes: ByteArray? = null,
            isFromHistory: Boolean = false
        ): PestDetailBottomSheet {
            return PestDetailBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_LABEL, label)
                    putFloat(ARG_CONFIDENCE, confidence)
                    putBoolean(ARG_FROM_HISTORY, isFromHistory)
                    if (imageBytes != null) {
                        putByteArray(ARG_IMAGE_BYTES, imageBytes)
                    }
                }
            }
        }
    }

    /** Callback when save completes */
    var onSaved: (() -> Unit)? = null

    /** Callback when the sheet is dismissed (swiped down or back pressed) */
    var onDismissed: (() -> Unit)? = null

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissed?.invoke()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_bottom_sheet_pest, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as com.google.android.material.bottomsheet.BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<android.view.View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) as? android.widget.FrameLayout
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }
        return dialog
    }

    // Removed dead setupPestInfo method that was causing compile errors

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val label = arguments?.getString(ARG_LABEL) ?: "Unknown"
        val confidence = arguments?.getFloat(ARG_CONFIDENCE, 0f) ?: 0f
        val imageBytes = arguments?.getByteArray(ARG_IMAGE_BYTES)
        val isFromHistory = arguments?.getBoolean(ARG_FROM_HISTORY, false) ?: false

        val pestInfo = PestDictionary.lookup(label)

        // Header
        val tvPestName = view.findViewById<TextView>(R.id.tv_pest_name)
        val tvConfidence = view.findViewById<TextView>(R.id.tv_confidence)
        val tvScientificName = view.findViewById<TextView>(R.id.tv_scientific_name)

        tvPestName.text = PestDictionary.getDisplayName(requireContext(), label)
        tvConfidence.text = "${"%.0f".format(confidence * 100)}%"
        tvScientificName.text = "Family: ${pestInfo.scientificName}"

        // Color confidence badge based on level
        val confBadge = tvConfidence
        val confColor = when {
            confidence >= 0.8f -> requireContext().getColor(R.color.primary_green)
            confidence >= 0.6f -> requireContext().getColor(R.color.warning_orange)
            else -> requireContext().getColor(R.color.error_red)
        }
        confBadge.background.setTint(confColor)

        // Populate descriptions and detailed sections
        val tvGeneralInfo = view.findViewById<TextView>(R.id.tv_general_info)
        tvGeneralInfo.text = if (pestInfo.generalInfoRes != 0) getString(pestInfo.generalInfoRes) else "No information available."

        // Helper to bind text and toggle visibility
        fun bindSection(textId: Int, labelId: Int, content: String) {
            val tvContent = view.findViewById<TextView>(textId)
            val vLabel = view.findViewById<View>(labelId)
            if (content.isNotBlank()) {
                tvContent.text = content
                tvContent.visibility = View.VISIBLE
                vLabel.visibility = View.VISIBLE
            } else {
                tvContent.visibility = View.GONE
                vLabel.visibility = View.GONE
            }
        }

        bindSection(R.id.tv_life_cycle, R.id.label_life_cycle, if (pestInfo.lifeCycleRes != 0) getString(pestInfo.lifeCycleRes) else "")
        bindSection(R.id.tv_bio_control, R.id.label_bio_control, if (pestInfo.bioControlsRes != 0) getString(pestInfo.bioControlsRes) else "")
        bindSection(R.id.tv_cultural, R.id.label_cultural, if (pestInfo.culturalRes != 0) getString(pestInfo.culturalRes) else "")
        bindSection(R.id.tv_chemical, R.id.label_chemical, if (pestInfo.chemicalRes != 0) getString(pestInfo.chemicalRes) else "")

        // Save to History
        val btnSave = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_history)
        if (isFromHistory) {
            btnSave.visibility = View.GONE
        } else {
            btnSave.setOnClickListener {
                btnSave.isEnabled = false
                btnSave.text = "Saving..."
                saveToHistory(pestInfo, confidence, imageBytes, btnSave)
            }
        }

        // Show captured image if available
        val cvCapturedImage = view.findViewById<View>(R.id.cv_captured_image)
        val ivCapturedImage = view.findViewById<android.widget.ImageView>(R.id.iv_captured_image)
        if (imageBytes != null && cvCapturedImage != null && ivCapturedImage != null) {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ivCapturedImage.setImageBitmap(bitmap)
            cvCapturedImage.visibility = View.VISIBLE
        }

        // Search Web
        val btnSearch = view.findViewById<MaterialButton>(R.id.btn_search_web)
        btnSearch.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, PestDictionary.getSearchQuery(pestInfo.scientificName))
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback: open browser directly
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.google.com/search?q=${PestDictionary.getSearchQuery(pestInfo.scientificName)}")
                )
                startActivity(browserIntent)
            }
        }

        // Reference Images
        setupReferenceGallery(view, pestInfo.scientificName)
    }

    private fun setupReferenceGallery(view: View, label: String) {
        val rvReferences = view.findViewById<RecyclerView>(R.id.rv_reference_images)
        rvReferences.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )

        // Map pest labels to drawable resources (using placeholders for now)
        val drawableIds = getReferenceDrawables(label)
        rvReferences.adapter = ReferenceImageAdapter(drawableIds)
    }

    /**
     * Dynamically loads reference images from the drawable folder based on a naming convention.
     * Looks for images named: ref_[insect_name]_1, ref_[insect_name]_2, etc. (e.g., ref_aphids_1.jpg)
     * If none are found, falls back to placeholders.
     */
    private fun getReferenceDrawables(label: String): List<Int> {
        val drawableIds = mutableListOf<Int>()
        val packageName = requireContext().packageName
        val cleanLabel = label.lowercase().replace(" ", "_").replace("-", "_")

        // Look for up to 5 reference images
        for (i in 1..5) {
            val resourceName = "ref_${cleanLabel}_$i"
            val resId = resources.getIdentifier(resourceName, "drawable", packageName)
            if (resId != 0) {
                drawableIds.add(resId)
            }
        }

        // Fallback to placeholders if no specific images are found
        if (drawableIds.isEmpty()) {
            return listOf(
                R.drawable.place_holder,
                R.drawable.place_holder,
                R.drawable.place_holder
            )
        }

        return drawableIds
    }

    private fun saveToHistory(
        pestInfo: PestDictionary.PestInfo,
        confidence: Float,
        imageBytes: ByteArray?,
        btnSave: com.google.android.material.button.MaterialButton
    ) {
        val db = PestDatabaseHelper(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val record = PestRecord(
                    pestName = PestDictionary.getDisplayName(requireContext(), pestInfo.scientificName),
                    confidence = confidence,
                    imageBlob = imageBytes,
                    timestamp = System.currentTimeMillis(),
                    notes = "Detected: ${pestInfo.scientificName}"
                )

                val result = db.insertPestRecord(record)

                withContext(Dispatchers.Main) {
                    if (result != -1L) {
                        Toast.makeText(
                            requireContext(),
                            "Saved ${pestInfo.commonName} to history!",
                            Toast.LENGTH_SHORT
                        ).show()
                        btnSave.text = "Saved"
                        onSaved?.invoke()
                    } else {
                        btnSave.isEnabled = true
                        btnSave.text = "Save to History"
                        Toast.makeText(
                            requireContext(),
                            "Failed to save",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnSave.isEnabled = true
                    btnSave.text = "Save to History"
                    Toast.makeText(
                        requireContext(),
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Update the sheet's content without recreating it.
     * Called when the user navigates to a different detection.
     */
    fun updateContent(label: String, confidence: Float, imageBytes: ByteArray? = null, isFromHistory: Boolean = false) {
        arguments = Bundle().apply {
            putString(ARG_LABEL, label)
            putFloat(ARG_CONFIDENCE, confidence)
            putBoolean(ARG_FROM_HISTORY, isFromHistory)
            if (imageBytes != null) {
                putByteArray(ARG_IMAGE_BYTES, imageBytes)
            }
        }

        view?.let { v ->
            val pestInfo = PestDictionary.lookup(label)

            v.findViewById<TextView>(R.id.tv_pest_name)?.text = PestDictionary.getDisplayName(v.context, label)
            v.findViewById<TextView>(R.id.tv_confidence)?.text =
                "${"%.0f".format(confidence * 100)}%"
            v.findViewById<TextView>(R.id.tv_scientific_name)?.text =
                "Family: ${pestInfo.scientificName}"
            v.findViewById<TextView>(R.id.tv_general_info)?.text = if (pestInfo.generalInfoRes != 0) getString(pestInfo.generalInfoRes) else "No information available."

            fun updateSection(textId: Int, labelId: Int, content: String) {
                val tvContent = v.findViewById<TextView>(textId)
                val vLabel = v.findViewById<View>(labelId)
                if (content.isNotBlank()) {
                    tvContent?.text = content
                    tvContent?.visibility = View.VISIBLE
                    vLabel?.visibility = View.VISIBLE
                } else {
                    tvContent?.visibility = View.GONE
                    vLabel?.visibility = View.GONE
                }
            }

            updateSection(R.id.tv_life_cycle, R.id.label_life_cycle, if (pestInfo.lifeCycleRes != 0) getString(pestInfo.lifeCycleRes) else "")
            updateSection(R.id.tv_bio_control, R.id.label_bio_control, if (pestInfo.bioControlsRes != 0) getString(pestInfo.bioControlsRes) else "")
            updateSection(R.id.tv_cultural, R.id.label_cultural, if (pestInfo.culturalRes != 0) getString(pestInfo.culturalRes) else "")
            updateSection(R.id.tv_chemical, R.id.label_chemical, if (pestInfo.chemicalRes != 0) getString(pestInfo.chemicalRes) else "")

            // Update confidence badge color
            val confColor = when {
                confidence >= 0.8f -> requireContext().getColor(R.color.primary_green)
                confidence >= 0.6f -> requireContext().getColor(R.color.warning_orange)
                else -> requireContext().getColor(R.color.error_red)
            }
            v.findViewById<TextView>(R.id.tv_confidence)?.background?.setTint(confColor)

            // Update reference images
            setupReferenceGallery(v, pestInfo.scientificName)

            // Show captured image if available
            val cvCapturedImage = v.findViewById<View>(R.id.cv_captured_image)
            val ivCapturedImage = v.findViewById<android.widget.ImageView>(R.id.iv_captured_image)
            if (imageBytes != null && cvCapturedImage != null && ivCapturedImage != null) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ivCapturedImage.setImageBitmap(bitmap)
                cvCapturedImage.visibility = View.VISIBLE
            } else {
                cvCapturedImage?.visibility = View.GONE
            }

            // Update button listeners with new data
            val btnSave = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_history)
            if (isFromHistory) {
                btnSave.visibility = View.GONE
            } else {
                btnSave.visibility = View.VISIBLE
                btnSave?.setOnClickListener {
                    btnSave.isEnabled = false
                    btnSave.text = "Saving..."
                    saveToHistory(pestInfo, confidence, imageBytes, btnSave)
                }
            }
            v.findViewById<MaterialButton>(R.id.btn_search_web)?.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, PestDictionary.getSearchQuery(pestInfo.scientificName))
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.google.com/search?q=${PestDictionary.getSearchQuery(pestInfo.scientificName)}")
                    )
                    startActivity(browserIntent)
                }
            }
        }
    }
}
