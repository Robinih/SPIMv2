package com.cvsuagritech.spim.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.adapters.HistoryAdapter
import com.cvsuagritech.spim.api.RetrofitClient
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.databinding.FragmentHistoryBinding
import com.cvsuagritech.spim.models.HistoryItem
import com.cvsuagritech.spim.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var databaseHelper: PestDatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var historyAdapter: HistoryAdapter
    private var allItems = listOf<HistoryItem>()

    private val beneficialInsects = listOf("pygmygrasshopper", "pygmy grasshopper")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        databaseHelper = PestDatabaseHelper(requireContext())
        sessionManager = SessionManager(requireContext())
        setupRecyclerView()
        setupFilters()
        setupClickListeners()
        loadHistory()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(listOf()) { item ->
            when (item) {
                is HistoryItem.IdentificationItem -> {
                    Toast.makeText(requireContext(), "Details for ${item.insectName}", Toast.LENGTH_SHORT).show()
                }
                is HistoryItem.CountItem -> {
                    showCountDetailDialog(item)
                }
            }
        }
        
        binding.recyclerHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun showCountDetailDialog(item: HistoryItem.CountItem) {
        val breakdownMap = item.getBreakdownMap()
        
        // Custom View for the Dialog
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_count_details, null)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tv_detail_total)
        val container = dialogView.findViewById<ViewGroup>(R.id.breakdown_container)
        
        tvTotal.text = getString(R.string.count_total_label, item.totalCount)
        
        if (breakdownMap.isEmpty()) {
            val emptyTv = TextView(requireContext())
            emptyTv.text = "No insect details available."
            container.addView(emptyTv)
        } else {
            breakdownMap.forEach { (name, count) ->
                val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_breakdown_row, container, false)
                val tvName = row.findViewById<TextView>(R.id.tv_insect_name)
                val tvCount = row.findViewById<TextView>(R.id.tv_insect_count)
                val tvType = row.findViewById<TextView>(R.id.tv_insect_type)
                
                tvName.text = name
                tvCount.text = count.toString()
                
                if (beneficialInsects.any { name.lowercase().contains(it) }) {
                    tvType.text = getString(R.string.stats_beneficial)
                    tvType.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_green))
                } else {
                    tvType.text = getString(R.string.stats_pests)
                    tvType.setTextColor(ContextCompat.getColor(requireContext(), R.color.error_red))
                }
                
                container.addView(row)
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Count Report Details")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun setupFilters() {
        binding.toggleGroupFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                filterList(checkedId)
            }
        }
    }

    private fun filterList(checkedId: Int) {
        val filtered = when (checkedId) {
            R.id.btn_filter_identified -> allItems.filterIsInstance<HistoryItem.IdentificationItem>()
            R.id.btn_filter_counted -> allItems.filterIsInstance<HistoryItem.CountItem>()
            else -> allItems
        }
        historyAdapter.updateData(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    private fun setupClickListeners() {
        binding.fabClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }

        binding.btnSync.setOnClickListener {
            if (sessionManager.isLoggedIn()) {
                performSync()
            } else {
                Toast.makeText(requireContext(), "Please login to sync data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performSync() {
        val userId = sessionManager.getUserId()
        if (userId == -1) return

        binding.btnSync.isEnabled = false
        binding.syncProgress.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val unsyncedPests = databaseHelper.getUnsyncedPestRecords()
            val unsyncedCounts = databaseHelper.getUnsyncedCountRecords()
            
            var successCount = 0
            var failCount = 0

            // 1. Sync Identifications
            unsyncedPests.forEach { pest ->
                try {
                    val userIdBody = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val nameBody = pest.pestName.toRequestBody("text/plain".toMediaTypeOrNull())
                    val confBody = pest.confidence.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    
                    val imagePart = pest.imageBlob?.let {
                        val requestFile = it.toRequestBody("image/jpeg".toMediaTypeOrNull())
                        MultipartBody.Part.createFormData("image", "sync_img_${pest.id}.jpg", requestFile)
                    }

                    if (imagePart != null) {
                        val response = RetrofitClient.instance.syncIdentify(userIdBody, nameBody, confBody, imagePart)
                        if (response.isSuccessful) {
                            databaseHelper.markPestRecordSynced(pest.id)
                            successCount++
                        } else {
                            failCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Sync", "Pest Sync Error: ${e.message}")
                    failCount++
                }
            }

            // 2. Sync Counts
            unsyncedCounts.forEach { count ->
                try {
                    val userIdBody = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val totalBody = count.totalCount.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    
                    // Logic to extract average confidence
                    val detailedBreakdown = count.getDetailedBreakdown()
                    var avgConf = 0f
                    if (detailedBreakdown.isNotEmpty()) {
                        val totalConf = detailedBreakdown.values.mapNotNull { it["confidence"] }.sum()
                        avgConf = totalConf / detailedBreakdown.size
                    }
                    val confBody = avgConf.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    
                    val breakdownBody = (count.breakdown ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
                    
                    val imagePart = count.imageBlob?.let {
                        val requestFile = it.toRequestBody("image/jpeg".toMediaTypeOrNull())
                        MultipartBody.Part.createFormData("image", "sync_count_${count.id}.jpg", requestFile)
                    }

                    if (imagePart != null) {
                        val response = RetrofitClient.instance.syncCount(userIdBody, totalBody, breakdownBody, confBody, imagePart)
                        if (response.isSuccessful) {
                            databaseHelper.markCountRecordSynced(count.id)
                            successCount++
                        } else {
                            failCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Sync", "Count Sync Error: ${e.message}")
                    failCount++
                }
            }

            withContext(Dispatchers.Main) {
                binding.btnSync.isEnabled = true
                binding.syncProgress.visibility = View.GONE
                
                if (successCount > 0 || failCount > 0) {
                    val msg = "Sync complete: $successCount uploaded, $failCount failed"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    loadHistory() // Refresh UI to update sync indicators
                } else {
                    Toast.makeText(requireContext(), "Everything is up to date!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear History")
            .setMessage("Are you sure you want to delete all history records? This action cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            databaseHelper.deleteAllPestRecords()
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show()
                loadHistory()
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            allItems = databaseHelper.getAllHistoryItems()
            withContext(Dispatchers.Main) {
                filterList(binding.toggleGroupFilter.checkedButtonId)
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }
}
