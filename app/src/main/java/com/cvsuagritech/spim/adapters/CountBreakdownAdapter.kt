package com.cvsuagritech.spim.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cvsuagritech.spim.R

data class BreakdownItem(val name: String, val count: Int, val colorResId: Int)

class CountBreakdownAdapter(
    private val breakdownList: List<BreakdownItem>,
    private val onItemClick: ((BreakdownItem) -> Unit)? = null
) : RecyclerView.Adapter<CountBreakdownAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val vColorIndicator: View = view.findViewById(R.id.v_color_indicator)
        val tvName: TextView = view.findViewById(R.id.tv_species_name)
        val tvCount: TextView = view.findViewById(R.id.tv_species_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_count_breakdown, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = breakdownList[position]
        holder.tvName.text = item.name
        holder.tvCount.text = "x ${item.count}"
        
        val context = holder.itemView.context
        val boxColor = ContextCompat.getColor(context, item.colorResId)

        holder.vColorIndicator.setBackgroundColor(boxColor)
        
        // Give the count badge a solid color matching the box, with white text for massive pop
        holder.tvCount.backgroundTintList = ColorStateList.valueOf(boxColor)
        holder.tvCount.setTextColor(Color.WHITE)
        
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    override fun getItemCount(): Int = breakdownList.size
}
