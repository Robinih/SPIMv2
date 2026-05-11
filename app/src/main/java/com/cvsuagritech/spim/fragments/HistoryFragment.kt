package com.cvsuagritech.spim.fragments

import android.content.Intent
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
import com.cvsuagritech.spim.FullScreenImageActivity
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.PestDetailBottomSheet
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
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var databaseHelper: PestDatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var historyAdapter: HistoryAdapter
    private var allItems = listOf<HistoryItem>()

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
        historyAdapter = HistoryAdapter(
            items = listOf(),
            onItemClick = { item ->
                when (item) {
                    is HistoryItem.IdentificationItem -> showIdentificationDetail(item)
                    is HistoryItem.CountItem -> showCountDetailDialog(item)
                    is HistoryItem.DateHeader -> { /* No-op for headers */ }
                }
            },
            onSelectionChanged = { count ->
                updateSelectionBar(count)
            }
        )
        
        binding.recyclerHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    // ── Selection Mode ────────────────────────────────────────────

    private fun updateSelectionBar(count: Int) {
        if (count == 0) {
            binding.selectionActionBar.visibility = View.GONE
            binding.fabClearHistory.visibility = View.VISIBLE
            return
        }
        binding.selectionActionBar.visibility = View.VISIBLE
        binding.fabClearHistory.visibility = View.GONE
        binding.tvSelectionCount.text = "$count selected"
    }

    private fun exitSelectionMode() {
        historyAdapter.clearSelection()
    }

    fun onBackPressed(): Boolean {
        return if (historyAdapter.isSelectionMode) {
            exitSelectionMode()
            true
        } else false
    }

    private fun deleteSelectedItems() {
        val selected = historyAdapter.getSelectedItems()
        if (selected.isEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${selected.size} item(s)?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    selected.forEach { item ->
                        when (item) {
                            is HistoryItem.IdentificationItem -> databaseHelper.deletePestRecord(item.id)
                            is HistoryItem.CountItem -> databaseHelper.deleteCountRecord(item.id)
                            is HistoryItem.DateHeader -> { /* Can't delete headers */ }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Deleted ${selected.size} item(s)", Toast.LENGTH_SHORT).show()
                        exitSelectionMode()
                        loadHistory()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Detail Views ──────────────────────────────────────────────

    private fun showIdentificationDetail(item: HistoryItem.IdentificationItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            val imageBlob = databaseHelper.getPestImage(item.id)
            withContext(Dispatchers.Main) {
                val bottomSheet = PestDetailBottomSheet.newInstance(
                    label = item.insectName,
                    confidence = item.confidence,
                    imageBytes = imageBlob,
                    isFromHistory = true
                )
                bottomSheet.show(parentFragmentManager, "PestDetailBottomSheet")
            }
        }
    }

    private fun showCountDetailDialog(item: HistoryItem.CountItem) {
        val breakdownMap = item.getBreakdownMap()
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_count_details, null)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tv_detail_total)
        val container = dialogView.findViewById<ViewGroup>(R.id.breakdown_container)
        val cvImageContainer = dialogView.findViewById<View>(R.id.cv_image_container)
        val ivCountImage = dialogView.findViewById<android.widget.ImageView>(R.id.iv_count_captured_image)
        
        tvTotal.text = getString(R.string.count_total_label, item.totalCount)

        lifecycleScope.launch(Dispatchers.IO) {
            val imageBlob = databaseHelper.getCountImage(item.id)
            if (imageBlob != null) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBlob, 0, imageBlob.size)
                withContext(Dispatchers.Main) {
                    ivCountImage.setImageBitmap(bitmap)
                    cvImageContainer.visibility = View.VISIBLE

                    // Full-screen image on tap
                    ivCountImage.setOnClickListener {
                        ivCountImage.isEnabled = false
                        lifecycleScope.launch(Dispatchers.IO) {
                            val ctx = requireContext()
                            val file = java.io.File(ctx.cacheDir, "history_temp_${item.id}.jpg")
                            java.io.FileOutputStream(file).use { it.write(imageBlob) }
                            withContext(Dispatchers.Main) {
                                ivCountImage.isEnabled = true
                                val intent = Intent(ctx, FullScreenImageActivity::class.java)
                                intent.putExtra(FullScreenImageActivity.EXTRA_IMAGE_PATH, file.absolutePath)
                                startActivity(intent)
                            }
                        }
                    }
                }
            }
        }
        
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
                tvType.text = getString(R.string.stats_pests)
                tvType.setTextColor(ContextCompat.getColor(requireContext(), R.color.error_red))
                
                row.setOnClickListener {
                    val bottomSheet = PestDetailBottomSheet.newInstance(
                        label = name,
                        confidence = 0f,
                        imageBytes = null,
                        isFromHistory = true
                    )
                    bottomSheet.show(parentFragmentManager, "PestDetailBottomSheet")
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

    // ── Time-Based Filters ────────────────────────────────────────

    private fun setupFilters() {
        binding.toggleGroupFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) filterList(checkedId)
        }
    }

    private fun filterList(checkedId: Int) {
        val now = Calendar.getInstance()
        val filtered = when (checkedId) {
            R.id.btn_filter_today -> {
                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                allItems.filter { it.timestamp >= startOfDay }
            }
            R.id.btn_filter_week -> {
                val startOfWeek = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                allItems.filter { it.timestamp >= startOfWeek }
            }
            R.id.btn_filter_month -> {
                val startOfMonth = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                allItems.filter { it.timestamp >= startOfMonth }
            }
            else -> allItems // All Time
        }

        val grouped = groupByDate(filtered)
        historyAdapter.updateData(grouped)
        updateEmptyState(filtered.isEmpty())
    }

    /**
     * Groups history items by calendar day and inserts DateHeader items.
     * Labels: "Today", "Yesterday", or formatted date (e.g., "April 14, 2026").
     */
    private fun groupByDate(items: List<HistoryItem>): List<HistoryItem> {
        if (items.isEmpty()) return emptyList()

        val result = mutableListOf<HistoryItem>()
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        val todayCal = Calendar.getInstance()
        val todayKey = dayFormat.format(todayCal.time)

        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayKey = dayFormat.format(yesterdayCal.time)

        var lastDayKey = ""

        for (item in items) {
            val itemDate = Date(item.timestamp)
            val itemDayKey = dayFormat.format(itemDate)

            if (itemDayKey != lastDayKey) {
                val label = when (itemDayKey) {
                    todayKey -> "Today"
                    yesterdayKey -> "Yesterday"
                    else -> dateFormat.format(itemDate)
                }
                result.add(HistoryItem.DateHeader(label = label))
                lastDayKey = itemDayKey
            }

            result.add(item)
        }

        return result
    }

    // ── Click Listeners ───────────────────────────────────────────

    private fun setupClickListeners() {
        binding.fabClearHistory.setOnClickListener { showClearHistoryDialog() }

        binding.btnCancelSelection.setOnClickListener { exitSelectionMode() }
        binding.btnDeleteSelected.setOnClickListener { deleteSelectedItems() }

        binding.btnSync.setOnClickListener {
            if (sessionManager.isLoggedIn()) performSync()
            else Toast.makeText(requireContext(), "Please login to sync data", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Sync ──────────────────────────────────────────────────────

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
                        if (response.isSuccessful) { databaseHelper.markPestRecordSynced(pest.id); successCount++ }
                        else failCount++
                    }
                } catch (e: Exception) { Log.e("Sync", "Pest Sync Error: ${e.message}"); failCount++ }
            }

            unsyncedCounts.forEach { count ->
                try {
                    val userIdBody = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val totalBody = count.totalCount.toString().toRequestBody("text/plain".toMediaTypeOrNull())
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
                        if (response.isSuccessful) { databaseHelper.markCountRecordSynced(count.id); successCount++ }
                        else failCount++
                    }
                } catch (e: Exception) { Log.e("Sync", "Count Sync Error: ${e.message}"); failCount++ }
            }

            withContext(Dispatchers.Main) {
                binding.btnSync.isEnabled = true
                binding.syncProgress.visibility = View.GONE
                if (successCount > 0 || failCount > 0) {
                    Toast.makeText(requireContext(), "Sync complete: $successCount uploaded, $failCount failed", Toast.LENGTH_LONG).show()
                    loadHistory()
                } else {
                    Toast.makeText(requireContext(), "Everything is up to date!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Clear All ─────────────────────────────────────────────────

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear History")
            .setMessage("Delete all history records? This cannot be undone.")
            .setPositiveButton("Clear All") { _, _ -> clearHistory() }
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

    // ── Load ──────────────────────────────────────────────────────

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
