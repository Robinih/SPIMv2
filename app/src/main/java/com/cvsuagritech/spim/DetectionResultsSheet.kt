package com.cvsuagritech.spim

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.models.HistoryItem
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetectionResultsSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_TOTAL = "total_count"
        private const val ARG_BREAKDOWN = "breakdown_json"
        private const val ARG_IMAGE = "image_bytes"

        fun newInstance(totalCount: Int, breakdownJson: String, imageBytes: ByteArray): DetectionResultsSheet {
            return DetectionResultsSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TOTAL, totalCount)
                    putString(ARG_BREAKDOWN, breakdownJson)
                    putByteArray(ARG_IMAGE, imageBytes)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_detection_results, container, false)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val totalCount = arguments?.getInt(ARG_TOTAL, 0) ?: 0
        val breakdownJson = arguments?.getString(ARG_BREAKDOWN) ?: "{}"
        val imageBytes = arguments?.getByteArray(ARG_IMAGE)

        val tvTotal = view.findViewById<TextView>(R.id.tv_total_count)
        val breakdownContainer = view.findViewById<LinearLayout>(R.id.breakdown_container)
        val btnSave = view.findViewById<MaterialButton>(R.id.btn_save)
        val tvNoDetections = view.findViewById<TextView>(R.id.tv_no_detections)

        tvTotal.text = totalCount.toString()

        // Parse breakdown JSON: {"name": {"count": X, "confidence": Y}}
        val breakdownMap: Map<String, Map<String, Any>> = try {
            val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            Gson().fromJson(breakdownJson, type)
        } catch (e: Exception) {
            Log.e("DetectionSheet", "Parse error: ${e.message}")
            emptyMap()
        }

        if (breakdownMap.isEmpty()) {
            tvNoDetections.visibility = View.VISIBLE
            btnSave.isEnabled = false
            btnSave.alpha = 0.5f
        } else {
            tvNoDetections.visibility = View.GONE

            // Sort by count descending
            val sorted = breakdownMap.entries.sortedByDescending {
                (it.value["count"] as? Double)?.toInt() ?: 0
            }

            sorted.forEach { (name, details) ->
                val rowView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_detection_row, breakdownContainer, false)

                val tvName = rowView.findViewById<TextView>(R.id.tv_pest_name)
                val tvCount = rowView.findViewById<TextView>(R.id.tv_count)
                val tvConf = rowView.findViewById<TextView>(R.id.tv_confidence)
                val indicator = rowView.findViewById<View>(R.id.indicator)

                tvName.text = name
                val count = (details["count"] as? Double)?.toInt() ?: 0
                tvCount.text = "×$count"

                val conf = (details["confidence"] as? Double) ?: 0.0
                tvConf.text = "${"%.0f".format(conf * 100)}%"

                // Color indicator (all pests = red for now, update when beneficials are defined)
                indicator.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.error_red)
                )
                tvConf.background.setTint(
                    ContextCompat.getColor(requireContext(), R.color.error_red)
                )

                breakdownContainer.addView(rowView)
            }
        }

        btnSave.setOnClickListener {
            saveToHistory(totalCount, breakdownJson, imageBytes)
        }
    }

    private fun saveToHistory(totalCount: Int, breakdownJson: String, imageBytes: ByteArray?) {
        val databaseHelper = PestDatabaseHelper(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val countItem = HistoryItem.CountItem(
                    id = 0,
                    totalCount = totalCount,
                    breakdown = breakdownJson,
                    imageBlob = imageBytes,
                    timestamp = System.currentTimeMillis()
                )

                val result = databaseHelper.insertCountRecord(countItem)

                withContext(Dispatchers.Main) {
                    if (result != -1L) {
                        Toast.makeText(requireContext(), "Saved to history!", Toast.LENGTH_SHORT).show()

                        // Navigate to pest detail page of highest confidence detection
                        val breakdownMap: Map<String, Map<String, Any>> = try {
                            val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                            Gson().fromJson(breakdownJson, type)
                        } catch (e: Exception) {
                            emptyMap()
                        }

                        val highestConfEntry = breakdownMap.maxByOrNull {
                            (it.value["confidence"] as? Double) ?: 0.0
                        }

                        dismiss()

                        // Navigate to pest page and finish scanner
                        if (highestConfEntry != null) {
                            (activity as? MainNavActivity)?.navigateToPestPage(highestConfEntry.key)
                            activity?.finish()
                        } else {
                            activity?.finish()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Failed to save", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("DetectionSheet", "Save error: ${e.message}")
                }
            }
        }
    }
}
