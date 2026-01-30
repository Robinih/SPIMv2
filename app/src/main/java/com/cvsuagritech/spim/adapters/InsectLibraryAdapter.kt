package com.cvsuagritech.spim.adapters

import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.databinding.ItemInsectCardBinding

data class Insect(val name: String, val imageResId: Int, val isRecommendation: Boolean = false)

class InsectLibraryAdapter(
    private val insects: List<Insect>,
    private val onItemClick: (Insect) -> Unit
) : RecyclerView.Adapter<InsectLibraryAdapter.InsectViewHolder>() {

    class InsectViewHolder(val binding: ItemInsectCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InsectViewHolder {
        val binding = ItemInsectCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return InsectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InsectViewHolder, position: Int) {
        val insect = insects[position]
        val context = holder.itemView.context
        
        holder.binding.tvInsectName.text = insect.name
        holder.binding.ivInsect.setImageResource(insect.imageResId)

        if (insect.isRecommendation) {
            // Recommendation styling: use a theme-aware green tint if possible, or keep the distinctive light green
            holder.binding.cardInsect.setCardBackgroundColor(Color.parseColor("#E8F5E9")) 
            holder.binding.tvInsectName.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            // Standard styling: use theme colors
            val typedValue = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
            holder.binding.cardInsect.setCardBackgroundColor(typedValue.data)
            
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            holder.binding.tvInsectName.setTextColor(typedValue.data)
        }

        holder.itemView.setOnClickListener { onItemClick(insect) }
    }

    override fun getItemCount() = insects.size
}
