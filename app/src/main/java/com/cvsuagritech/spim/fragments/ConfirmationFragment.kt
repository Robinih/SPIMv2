package com.cvsuagritech.spim.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.MainNavActivity
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.databinding.ConfirmationBinding
import com.cvsuagritech.spim.models.PestRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class ConfirmationFragment : Fragment() {

    private var _binding: ConfirmationBinding? = null
    private val binding get() = _binding!!
    private lateinit var databaseHelper: PestDatabaseHelper
    private var userImageBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ConfirmationBinding.inflate(inflater, container, false)
        databaseHelper = PestDatabaseHelper(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imagePath = arguments?.getString("imagePath")
        val pestName = arguments?.getString("pestName")
        val confidence = arguments?.getFloat("confidence")

        if (pestName == null || imagePath == null || confidence == null) {
            Log.e("ConfirmationFragment", "Critical data missing. PestName, ImagePath or Confidence is null.")
            Toast.makeText(requireContext(), "Error loading confirmation data.", Toast.LENGTH_LONG).show()
            activity?.supportFragmentManager?.popBackStack()
            return
        }

        val imageFile = File(imagePath)
        if (imageFile.exists()) {
            userImageBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            binding.userImageView.setImageBitmap(userImageBitmap)
        } else {
            Log.e("ConfirmationFragment", "Image file not found at path: $imagePath")
            binding.userImageView.setImageResource(R.drawable.place_holder)
        }

        binding.referencePestName.text = "${getString(R.string.result_detected_pest)} $pestName"
        
        // Handle Beneficial status for Pygmy Grasshopper
        if (pestName.lowercase().replace(" ", "") == "pygmygrasshopper") {
            binding.tvConfirmationTitle.text = getString(R.string.conf_title_beneficial)
            binding.tvConfirmationTitle.setTextColor(Color.parseColor("#4CAF50")) // Green for beneficial
            binding.btnYes.setBackgroundColor(Color.parseColor("#4CAF50"))
            binding.btnYes.text = getString(R.string.conf_btn_save)
        } else {
            binding.tvConfirmationTitle.text = getString(R.string.conf_title_match)
            binding.tvConfirmationTitle.setTextColor(Color.BLACK)
            binding.btnYes.setBackgroundColor(requireContext().getColor(R.color.primary_green))
            binding.btnYes.text = getString(R.string.conf_btn_yes)
        }
        
        binding.btnNo.text = getString(R.string.conf_btn_no)

        val resourceId = when (pestName.lowercase().replace(" ", "")) {
            "leafbeetle" -> R.drawable.leafbettle
            "leafhopper", "aphids" -> R.drawable.aphids
            "pygmygrasshopper" -> R.drawable.pygmy
            "slantfacedgrasshopper" -> R.drawable.slantfaced
            else -> R.drawable.place_holder
        }
        binding.referenceImageView.setImageResource(resourceId)

        binding.btnYes.setOnClickListener {
             if (userImageBitmap == null) {
                Toast.makeText(requireContext(), "Cannot save record without an image.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val imageByteArray = withContext(Dispatchers.IO) {
                    val stream = ByteArrayOutputStream()
                    userImageBitmap?.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                    stream.toByteArray()
                }

                val pestRecord = PestRecord(
                    pestName = pestName,
                    confidence = confidence,
                    imagePath = null,
                    imageBlob = imageByteArray
                )
                val recordId = withContext(Dispatchers.IO) {
                    databaseHelper.insertPestRecord(pestRecord)
                }

                withContext(Dispatchers.Main) {
                    if (recordId > -1) {
                        Toast.makeText(requireContext(), "Record saved to history.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to save record. Please try again.", Toast.LENGTH_SHORT).show()
                    }

                    (activity as? MainNavActivity)?.navigateToPestPage(pestName)
                }
            }
        }

        binding.btnNo.setOnClickListener {
            activity?.supportFragmentManager?.popBackStack()
        }
    }

    override fun onDestroyView() {
        arguments?.getString("imagePath")?.let {
            try {
                val file = File(it)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("ConfirmationFragment", "Error cleaning up temp image file", e)
            }
        }
        super.onDestroyView()
        _binding = null
    }
}
