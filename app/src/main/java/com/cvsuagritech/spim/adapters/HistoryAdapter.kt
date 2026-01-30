package com.cvsuagritech.spim.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.databinding.ItemHistoryBinding
import com.cvsuagritech.spim.databinding.ItemHistoryCountBinding
import com.cvsuagritech.spim.models.HistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private var items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private lateinit var databaseHelper: PestDatabaseHelper
    private val adapterScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TYPE_IDENTIFICATION = 0
        private const val TYPE_COUNT = 1
    }

    fun updateData(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HistoryItem.IdentificationItem -> TYPE_IDENTIFICATION
            is HistoryItem.CountItem -> TYPE_COUNT
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
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is IdentificationViewHolder -> holder.bind(item as HistoryItem.IdentificationItem)
            is CountViewHolder -> holder.bind(item as HistoryItem.CountItem)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class IdentificationViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem.IdentificationItem) {
            binding.tvPestName.text = item.insectName
            binding.tvConfidence.text = "${(item.confidence * 100).toInt()}%"
            binding.tvDateTime.text = formatDate(item.timestamp)

            // Show sync status icon if NOT synced
            binding.ivSyncStatus.visibility = if (item.isSynced) View.GONE else View.VISIBLE

            binding.ivPestImage.setImageResource(R.drawable.place_holder)
            
            adapterScope.launch {
                val blob = withContext(Dispatchers.IO) {
                    databaseHelper.getPestImage(item.id)
                }
                if (blob != null) {
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(blob, 0, blob.size)
                    }
                    binding.ivPestImage.setImageBitmap(bitmap)
                }
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    inner class CountViewHolder(private val binding: ItemHistoryCountBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem.CountItem) {
            binding.tvTotalCount.text = item.totalCount.toString()
            binding.tvCountDate.text = formatDate(item.timestamp)
            
            // Show sync status icon if NOT synced
            binding.ivSyncStatus.visibility = if (item.isSynced) View.GONE else View.VISIBLE
            
            val (colorRes, label) = when (item.severityLevel) {
                HistoryItem.CountItem.Severity.LOW -> R.color.success_green to "LOW"
                HistoryItem.CountItem.Severity.MEDIUM -> R.color.warning_orange to "MED"
                HistoryItem.CountItem.Severity.HIGH -> R.color.error_red to "HIGH"
            }
            
            val color = ContextCompat.getColor(binding.root.context, colorRes)
            binding.severityIndicator.setBackgroundColor(color)
            binding.tvSeverityLabel.text = label
            binding.tvSeverityLabel.backgroundTintList = ContextCompat.getColorStateList(binding.root.context, colorRes)

            binding.ivCountImage.setImageResource(R.drawable.place_holder)
            
            adapterScope.launch {
                val blob = withContext(Dispatchers.IO) {
                    databaseHelper.getCountImage(item.id)
                }
                if (blob != null) {
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(blob, 0, blob.size)
                    }
                    binding.ivCountImage.setImageBitmap(bitmap)
                }
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
