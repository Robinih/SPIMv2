package com.cvsuagritech.spim.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.cvsuagritech.spim.R

/**
 * Adapter for displaying reference images in the pest detail bottom sheet.
 * Takes a list of drawable resource IDs.
 */
class ReferenceImageAdapter(
    private val drawableResIds: List<Int>
) : RecyclerView.Adapter<ReferenceImageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.iv_reference)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reference_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.imageView.setImageResource(drawableResIds[position])
    }

    override fun getItemCount(): Int = drawableResIds.size
}
