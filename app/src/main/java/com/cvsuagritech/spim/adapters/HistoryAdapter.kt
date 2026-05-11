package com.cvsuagritech.spim.adapters

import android.content.Intent
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cvsuagritech.spim.FullScreenImageActivity
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.databinding.ItemHistoryBinding
import com.cvsuagritech.spim.databinding.ItemHistoryCountBinding
import com.cvsuagritech.spim.models.HistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private var items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit,
    val onSelectionChanged: (count: Int) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private lateinit var databaseHelper: PestDatabaseHelper
    private val adapterScope = CoroutineScope(Dispatchers.Main)

    // Selection state
    private val selectedItems = mutableSetOf<HistoryItem>()
    var isSelectionMode = false
        private set

    companion object {
        private const val TYPE_IDENTIFICATION = 0
        private const val TYPE_COUNT = 1
        private const val TYPE_DATE_HEADER = 2
    }

    fun updateData(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<HistoryItem> =
        selectedItems.toList()

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    private fun toggleSelection(item: HistoryItem) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }
        if (selectedItems.isEmpty()) {
            isSelectionMode = false
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    private fun enterSelectionMode(item: HistoryItem) {
        isSelectionMode = true
        selectedItems.add(item)
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HistoryItem.IdentificationItem -> TYPE_IDENTIFICATION
            is HistoryItem.CountItem -> TYPE_COUNT
            is HistoryItem.DateHeader -> TYPE_DATE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (!::databaseHelper.isInitialized) {
            databaseHelper = PestDatabaseHelper(parent.context)
        }
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_IDENTIFICATION -> {
                val binding = ItemHistoryBinding.inflate(inflater, parent, false)
                IdentificationViewHolder(binding)
            }
            TYPE_COUNT -> {
                val binding = ItemHistoryCountBinding.inflate(inflater, parent, false)
                CountViewHolder(binding)
            }
            TYPE_DATE_HEADER -> {
                val view = inflater.inflate(R.layout.item_history_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is IdentificationViewHolder -> holder.bind(item as HistoryItem.IdentificationItem)
            is CountViewHolder -> holder.bind(item as HistoryItem.CountItem)
            is DateHeaderViewHolder -> holder.bind(item as HistoryItem.DateHeader)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun applySelectionHighlight(root: View, item: HistoryItem) {
        val isSelected = selectedItems.contains(item)
        root.alpha = if (isSelectionMode && !isSelected) 0.5f else 1.0f
        root.isSelected = isSelected
    }

    // ── Date Header ViewHolder ──────────────────────────────────

    inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDateHeader: TextView = itemView.findViewById(R.id.tv_date_header)

        fun bind(item: HistoryItem.DateHeader) {
            tvDateHeader.text = item.label
        }
    }

    // ── Identification ViewHolder ───────────────────────────────

    inner class IdentificationViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem.IdentificationItem) {
            binding.tvPestName.text = item.insectName
            binding.tvConfidence.text = "${(item.confidence * 100).toInt()}%"
            binding.tvDateTime.text = formatDate(item.timestamp)
            binding.ivSyncStatus.visibility = if (item.isSynced) View.GONE else View.VISIBLE
            binding.ivPestImage.setImageResource(R.drawable.place_holder)

            applySelectionHighlight(binding.root, item)

            adapterScope.launch {
                val blob = withContext(Dispatchers.IO) { databaseHelper.getPestImage(item.id) }
                if (blob != null) {
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(blob, 0, blob.size)
                    }
                    binding.ivPestImage.setImageBitmap(bitmap)
                }
            }

            // Full-screen image on thumbnail tap
            binding.ivPestImage.setOnClickListener {
                adapterScope.launch {
                    binding.ivPestImage.isEnabled = false
                    val blob = withContext(Dispatchers.IO) { databaseHelper.getPestImage(item.id) }
                    if (blob != null) {
                        val ctx = binding.root.context
                        withContext(Dispatchers.IO) {
                            val file = java.io.File(ctx.cacheDir, "history_temp_${item.id}.jpg")
                            java.io.FileOutputStream(file).use { it.write(blob) }
                            withContext(Dispatchers.Main) {
                                binding.ivPestImage.isEnabled = true
                                val intent = Intent(ctx, FullScreenImageActivity::class.java)
                                intent.putExtra(FullScreenImageActivity.EXTRA_IMAGE_PATH, file.absolutePath)
                                ctx.startActivity(intent)
                            }
                        }
                    } else {
                        binding.ivPestImage.isEnabled = true
                    }
                }
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) toggleSelection(item) else onItemClick(item)
            }
            binding.root.setOnLongClickListener {
                if (!isSelectionMode) enterSelectionMode(item) else toggleSelection(item)
                true
            }
        }
    }

    // ── Count ViewHolder ────────────────────────────────────────

    inner class CountViewHolder(private val binding: ItemHistoryCountBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem.CountItem) {
            binding.tvTotalCount.text = item.totalCount.toString()
            binding.tvCountDate.text = formatDate(item.timestamp)
            binding.ivSyncStatus.visibility = if (item.isSynced) View.GONE else View.VISIBLE

            val (colorRes, label) = when (item.severityLevel) {
                HistoryItem.CountItem.Severity.LOW -> R.color.success_green to "LOW"
                HistoryItem.CountItem.Severity.MEDIUM -> R.color.warning_orange to "MED"
                HistoryItem.CountItem.Severity.HIGH -> R.color.error_red to "HIGH"
            }
            binding.tvSeverityLabel.text = label
            binding.tvSeverityLabel.backgroundTintList =
                ContextCompat.getColorStateList(binding.root.context, colorRes)

            binding.ivCountImage.setImageResource(R.drawable.place_holder)

            applySelectionHighlight(binding.root, item)

            adapterScope.launch {
                val blob = withContext(Dispatchers.IO) { databaseHelper.getCountImage(item.id) }
                if (blob != null) {
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(blob, 0, blob.size)
                    }
                    binding.ivCountImage.setImageBitmap(bitmap)
                }
            }

            // Full-screen image on thumbnail tap
            binding.ivCountImage.setOnClickListener {
                adapterScope.launch {
                    binding.ivCountImage.isEnabled = false
                    val blob = withContext(Dispatchers.IO) { databaseHelper.getCountImage(item.id) }
                    if (blob != null) {
                        val ctx = binding.root.context
                        withContext(Dispatchers.IO) {
                            val file = java.io.File(ctx.cacheDir, "history_temp_${item.id}.jpg")
                            java.io.FileOutputStream(file).use { it.write(blob) }
                            withContext(Dispatchers.Main) {
                                binding.ivCountImage.isEnabled = true
                                val intent = Intent(ctx, FullScreenImageActivity::class.java)
                                intent.putExtra(FullScreenImageActivity.EXTRA_IMAGE_PATH, file.absolutePath)
                                ctx.startActivity(intent)
                            }
                        }
                    } else {
                        binding.ivCountImage.isEnabled = true
                    }
                }
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) toggleSelection(item) else onItemClick(item)
            }
            binding.root.setOnLongClickListener {
                if (!isSelectionMode) enterSelectionMode(item) else toggleSelection(item)
                true
            }
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
