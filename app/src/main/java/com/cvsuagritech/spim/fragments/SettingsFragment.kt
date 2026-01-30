package com.cvsuagritech.spim.fragments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.MainNavActivity
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.WelcomeActivity
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.databinding.FragmentSettingsBinding
import com.cvsuagritech.spim.models.HistoryItem
import com.cvsuagritech.spim.utils.LanguageManager
import com.cvsuagritech.spim.utils.SessionManager
import com.cvsuagritech.spim.utils.ThemeManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var databaseHelper: PestDatabaseHelper
    private lateinit var sessionManager: SessionManager
    private var currentLanguage = "en"
    private var currentTheme = ThemeManager.THEME_SYSTEM

    private val beneficialInsects = listOf("pygmygrasshopper", "pygmy grasshopper")

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
        sessionManager = SessionManager(requireContext())
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
            exportDataToCSV()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
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
        LanguageManager.setLanguage(requireContext(), languageCode)
        restartActivity()
    }

    private fun changeTheme(themeMode: String) {
        ThemeManager.setThemeMode(requireContext(), themeMode)
        restartActivity()
    }

    private fun restartActivity() {
        val intent = Intent(requireContext(), MainNavActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        requireActivity().finish()
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

    private fun exportDataToCSV() {
        val farmerName = sessionManager.getUsername() ?: "Guest"
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val historyItems = databaseHelper.getAllHistoryItems()
                if (historyItems.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "No data to export", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Path to public Downloads folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fileName = "SPIM_Pest_Report_${System.currentTimeMillis()}.csv"
                val file = File(downloadsDir, fileName)
                
                FileOutputStream(file).use { out ->
                    // Standard CSV header - Removed ImageStatus
                    out.write("Date,Farmer,Insect,Type,Count,Confidence\n".toByteArray())
                    
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    
                    historyItems.forEach { item ->
                        val dateFormatted = sdf.format(Date(item.timestamp))
                        // Wrap date in double quotes to prevent Excel from showing hashtags
                        val date = "\"$dateFormatted\"" 
                        
                        when (item) {
                            is HistoryItem.IdentificationItem -> {
                                val type = if (isBeneficial(item.insectName.lowercase())) "Beneficial" else "Pest"
                                val conf = "\"${(item.confidence * 100).toInt()}%\""
                                out.write("$date,$farmerName,${item.insectName},$type,1,$conf\n".toByteArray())
                            }
                            is HistoryItem.CountItem -> {
                                val breakdown = item.getBreakdownMapDetailed()
                                if (breakdown.isEmpty()) {
                                    out.write("$date,$farmerName,Unknown,Pest,${item.totalCount},\"N/A\"\n".toByteArray())
                                } else {
                                    breakdown.forEach { (insect, details) ->
                                        val type = if (isBeneficial(insect.lowercase())) "Beneficial" else "Pest"
                                        val count = details["count"]?.toInt() ?: 0
                                        val confVal = details["confidence"]
                                        val conf = if (confVal != null) "\"${(confVal * 100).toInt()}%\"" else "\"N/A\""
                                        out.write("$date,$farmerName,$insect,$type,$count,$conf\n".toByteArray())
                                    }
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Report saved to Downloads folder", Toast.LENGTH_LONG).show()
                    openFile(file)
                }
            } catch (e: Exception) {
                Log.e("Export", "Error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Export failed. Please check app permissions.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isBeneficial(name: String): Boolean {
        return beneficialInsects.any { name.contains(it) }
    }

    private fun HistoryItem.CountItem.getBreakdownMapDetailed(): Map<String, Map<String, Float>> {
        val raw = breakdown ?: return emptyMap()
        val gson = Gson()
        return try {
            // New format: {"name": {"count": X, "confidence": Y}}
            val type = object : TypeToken<Map<String, Map<String, Float>>>() {}.type
            gson.fromJson(raw, type)
        } catch (e: Exception) {
            // Old format: "Name:Count,Name:Count"
            try {
                raw.split(",").associate {
                    val parts = it.split(":")
                    parts[0] to mapOf("count" to parts[1].toFloat())
                }
            } catch (e2: Exception) {
                emptyMap()
            }
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No app found to open CSV files.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_logout_title))
            .setMessage(getString(R.string.dialog_logout_message))
            .setPositiveButton(getString(R.string.btn_logout)) { _, _ ->
                logout()
            }
            .setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun logout() {
        sessionManager.logout()
        val intent = Intent(requireContext(), WelcomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onResume() {
        super.onResume()
        loadAppInfo()
    }
}
