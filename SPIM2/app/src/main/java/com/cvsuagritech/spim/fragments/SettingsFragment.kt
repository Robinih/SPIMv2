package com.cvsuagritech.spim.fragments

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.databinding.FragmentSettingsBinding
import com.cvsuagritech.spim.utils.LanguageManager
import com.cvsuagritech.spim.utils.ThemeManager
import java.util.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var databaseHelper: PestDatabaseHelper
    private var currentLanguage = "en"
    private var currentTheme = ThemeManager.THEME_SYSTEM

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        databaseHelper = PestDatabaseHelper(requireContext())
        setupUI()
        setupClickListeners()
        loadAppInfo()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupUI() {
        // Set app version
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.tvAppVersion.text = packageInfo.versionName
        } catch (e: Exception) {
            binding.tvAppVersion.text = "1.0.0"
        }

        // Set database status
        binding.tvDatabaseStatus.text = getString(R.string.settings_database_ready)
        binding.tvDatabaseStatus.setTextColor(requireContext().getColor(R.color.success_green))

        // Set current language
        currentLanguage = LanguageManager.getCurrentLanguage(requireContext())
        binding.btnLanguage.text = LanguageManager.getLanguageDisplayName(currentLanguage)
        
        // Set current theme
        currentTheme = ThemeManager.getCurrentThemeMode(requireContext())
        binding.btnTheme.text = ThemeManager.getThemeDisplayName(currentTheme)
    }

    private fun setupClickListeners() {
        binding.btnLanguage.setOnClickListener {
            showLanguageDialog()
        }

        binding.btnTheme.setOnClickListener {
            showThemeDialog()
        }

        binding.btnClearAllHistory.setOnClickListener {
            showClearAllHistoryDialog()
        }

        binding.btnExportData.setOnClickListener {
            showExportDataDialog()
        }
    }

    private fun loadAppInfo() {
        val totalRecords = databaseHelper.getTotalRecordsCount()
        binding.tvTotalRecords.text = totalRecords.toString()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Tagalog")
        val currentIndex = if (currentLanguage == "tl") 1 else 0

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_language))
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val selectedLanguage = if (which == 0) "en" else "tl"
                if (selectedLanguage != currentLanguage) {
                    changeLanguage(selectedLanguage)
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Light", "Dark", "System")
        val currentIndex = when (currentTheme) {
            ThemeManager.THEME_LIGHT -> 0
            ThemeManager.THEME_DARK -> 1
            ThemeManager.THEME_SYSTEM -> 2
            else -> 2
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_theme))
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                val selectedTheme = when (which) {
                    0 -> ThemeManager.THEME_LIGHT
                    1 -> ThemeManager.THEME_DARK
                    2 -> ThemeManager.THEME_SYSTEM
                    else -> ThemeManager.THEME_SYSTEM
                }
                if (selectedTheme != currentTheme) {
                    changeTheme(selectedTheme)
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun changeLanguage(languageCode: String) {
        // Use LanguageManager to change language
        LanguageManager.setLanguage(requireContext(), languageCode)
        
        currentLanguage = languageCode
        binding.btnLanguage.text = LanguageManager.getLanguageDisplayName(languageCode)
        
        // Restart the activity to apply language changes
        requireActivity().recreate()
    }

    private fun changeTheme(themeMode: String) {
        // Use ThemeManager to change theme
        ThemeManager.setThemeMode(requireContext(), themeMode)
        
        currentTheme = themeMode
        binding.btnTheme.text = ThemeManager.getThemeDisplayName(themeMode)
        
        // Show toast message
        val themeName = ThemeManager.getThemeDisplayName(themeMode)
        Toast.makeText(requireContext(), "Theme changed to $themeName", Toast.LENGTH_SHORT).show()
        
        // Recreate activity to apply theme changes
        requireActivity().recreate()
    }


    private fun showClearAllHistoryDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear All History")
            .setMessage("Are you sure you want to delete all pest identification records? This action cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                val deletedCount = databaseHelper.deleteAllPestRecords()
                if (deletedCount > 0) {
                    loadAppInfo()
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

    private fun showExportDataDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Export Data")
            .setMessage("This feature will be available in a future update. Data is currently stored locally in the app.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this fragment
        loadAppInfo()
    }
}
