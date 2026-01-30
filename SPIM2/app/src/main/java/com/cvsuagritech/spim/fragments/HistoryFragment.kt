package com.cvsuagritech.spim.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.adapters.HistoryAdapter
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.databinding.FragmentHistoryBinding
import com.cvsuagritech.spim.models.PestRecord

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var databaseHelper: PestDatabaseHelper
    private lateinit var historyAdapter: HistoryAdapter
    private var pestRecords = mutableListOf<PestRecord>()

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
        setupRecyclerView()
        setupClickListeners()
        loadHistory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(pestRecords) { pestRecord ->
            // Handle item click - show details or navigate to detail screen
            showRecordDetails(pestRecord)
        }
        
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = historyAdapter
    }

    private fun setupClickListeners() {
        binding.fabClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }
    }

    private fun loadHistory() {
        pestRecords.clear()
        pestRecords.addAll(databaseHelper.getAllPestRecords())
        
        if (pestRecords.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerHistory.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerHistory.visibility = View.VISIBLE
            historyAdapter.notifyDataSetChanged()
        }
    }

    private fun showRecordDetails(pestRecord: PestRecord) {
        val message = buildString {
            appendLine("Pest: ${pestRecord.pestName}")
            appendLine("Confidence: ${pestRecord.getConfidencePercentage()}%")
            appendLine("Date: ${pestRecord.getFormattedDate()}")
            appendLine("Time: ${pestRecord.getFormattedTime()}")
            if (!pestRecord.notes.isNullOrEmpty()) {
                appendLine("Notes: ${pestRecord.notes}")
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Record Details")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Delete") { _, _ ->
                showDeleteRecordDialog(pestRecord)
            }
            .show()
    }

    private fun showDeleteRecordDialog(pestRecord: PestRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Record")
            .setMessage("Are you sure you want to delete this record?")
            .setPositiveButton("Delete") { _, _ ->
                if (databaseHelper.deletePestRecord(pestRecord.id)) {
                    loadHistory()
                    Toast.makeText(requireContext(), "Record deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to delete record", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear All History")
            .setMessage("Are you sure you want to delete all pest identification records? This action cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                val deletedCount = databaseHelper.deleteAllPestRecords()
                if (deletedCount > 0) {
                    loadHistory()
                    Toast.makeText(requireContext(), "Cleared $deletedCount records", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "No records to clear", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this fragment
        loadHistory()
    }
}
