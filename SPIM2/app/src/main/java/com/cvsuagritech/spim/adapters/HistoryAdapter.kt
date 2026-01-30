package com.cvsuagritech.spim.adapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.models.PestRecord

class HistoryAdapter(
    private val pestRecords: List<PestRecord>,
    private val onItemClick: (PestRecord) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private lateinit var databaseHelper: PestDatabaseHelper

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPestImage: ImageView = itemView.findViewById(R.id.iv_pest_image)
        val tvPestName: TextView = itemView.findViewById(R.id.tv_pest_name)
        val tvConfidence: TextView = itemView.findViewById(R.id.tv_confidence)
        val tvDateTime: TextView = itemView.findViewById(R.id.tv_date_time)
        val btnViewDetails: View = itemView.findViewById(R.id.btn_view_details)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val pestRecord = pestRecords[position]
        
        // Set pest name
        holder.tvPestName.text = pestRecord.pestName
        
        // Set confidence
        holder.tvConfidence.text = "${pestRecord.getConfidencePercentage()}%"
        
        // Set date and time
        holder.tvDateTime.text = pestRecord.getFormattedDateTime()
        
        // Set image
        if (pestRecord.imageBlob != null) {
            val bitmap = BitmapFactory.decodeByteArray(pestRecord.imageBlob, 0, pestRecord.imageBlob.size)
            if (bitmap != null) {
                holder.ivPestImage.setImageBitmap(bitmap)
            } else {
                holder.ivPestImage.setImageResource(R.drawable.place_holder)
            }
        } else {
            holder.ivPestImage.setImageResource(R.drawable.place_holder)
        }
        
        // Set click listeners
        holder.itemView.setOnClickListener {
            onItemClick(pestRecord)
        }
        
        holder.btnViewDetails.setOnClickListener {
            onItemClick(pestRecord)
        }
    }

    override fun getItemCount(): Int = pestRecords.size
}
