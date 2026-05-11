package com.cvsuagritech.spim.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.adapters.ReferenceImageAdapter
import com.cvsuagritech.spim.utils.PestDictionary

class PestLibraryDetailFragment : Fragment() {

    private var pestLabel: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pestLabel = it.getString(ARG_PEST_LABEL)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pest_library_detail, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val label = pestLabel ?: "unknown"
        val pestInfo = PestDictionary.lookup(label)

        // Setup Toolbar & Navigation
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_back) // Assuming ic_back exists, or use androidx.appcompat.R.drawable.abc_ic_ab_back_material
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        
        // Disable toolbar title to use our own custom collapsing title or just keep it blank
        toolbar.title = ""

        // Bind Headers
        view.findViewById<TextView>(R.id.tv_pest_name)?.text = PestDictionary.getDisplayName(requireContext(), label)
        view.findViewById<TextView>(R.id.tv_scientific_name)?.text = "Family: ${pestInfo.scientificName}"

        // Hero Image (Use the first reference image, or placeholder)
        val heroImageView = view.findViewById<ImageView>(R.id.iv_pest_hero)
        val allReferences = getReferenceDrawables(label)
        val imageResIds = allReferences.mapNotNull { name ->
            val id = resources.getIdentifier(name, "drawable", requireContext().packageName)
            if (id != 0) id else null
        }
        
        if (imageResIds.isNotEmpty()) {
            heroImageView?.setImageResource(imageResIds.first())
        } else {
            heroImageView?.setImageResource(R.drawable.place_holder)
        }

        // Setup Reference Gallery
        val rvGallery = view.findViewById<RecyclerView>(R.id.rv_reference_gallery)
        if (imageResIds.isNotEmpty()) {
            rvGallery?.visibility = View.VISIBLE
            val galleryAdapter = ReferenceImageAdapter(imageResIds)
            rvGallery?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            rvGallery?.adapter = galleryAdapter
        } else {
            rvGallery?.visibility = View.GONE
        }

        // Bind Detailed Text Sections
        view.findViewById<TextView>(R.id.tv_general_info)?.text = if (pestInfo.generalInfoRes != 0) getString(pestInfo.generalInfoRes) else "No information available."
        view.findViewById<TextView>(R.id.tv_life_cycle)?.text = if (pestInfo.lifeCycleRes != 0) getString(pestInfo.lifeCycleRes) else "No information available."
        view.findViewById<TextView>(R.id.tv_bio_control)?.text = if (pestInfo.bioControlsRes != 0) getString(pestInfo.bioControlsRes) else "No information available."
        view.findViewById<TextView>(R.id.tv_cultural)?.text = if (pestInfo.culturalRes != 0) getString(pestInfo.culturalRes) else "No information available."
        view.findViewById<TextView>(R.id.tv_chemical)?.text = if (pestInfo.chemicalRes != 0) getString(pestInfo.chemicalRes) else "No information available."
    }

    /**
     * Finds generic drawables matching the naming convention `ref_[label]_[number]`.
     * Up to 5 images.
     */
    private fun getReferenceDrawables(label: String): List<String> {
        val foundDrawables = mutableListOf<String>()
        val packageName = context?.packageName ?: return emptyList()
        val safeLabel = label.trim().lowercase().replace(" ", "_").replace("-", "")

        for (i in 1..5) {
            val drawableName = "ref_${safeLabel}_$i"
            val resId = resources.getIdentifier(drawableName, "drawable", packageName)
            if (resId != 0) {
                foundDrawables.add(drawableName)
            } else {
                break
            }
        }
        return foundDrawables
    }

    companion object {
        const val ARG_PEST_LABEL = "arg_pest_label"

        @JvmStatic
        fun newInstance(label: String) =
            PestLibraryDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PEST_LABEL, label)
                }
            }
    }
}
