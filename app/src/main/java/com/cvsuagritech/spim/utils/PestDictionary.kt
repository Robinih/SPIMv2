package com.cvsuagritech.spim.utils

import androidx.annotation.StringRes
import android.content.Context
import com.cvsuagritech.spim.R

/**
 * Localization dictionary mapping YOLOv8 scientific family labels
 * to user-friendly common names, Tagalog translations, and detailed info.
 */
object PestDictionary {

    data class PestInfo(
        val scientificName: String,
        val commonName: String,
        val tagalogName: String,
        @StringRes val nameRes: Int,
        @StringRes val generalInfoRes: Int,
        @StringRes val lifeCycleRes: Int,
        @StringRes val bioControlsRes: Int,
        @StringRes val culturalRes: Int,
        @StringRes val chemicalRes: Int
    )



    private val dictionary: Map<String, PestInfo> = mapOf(
        "crambidae" to PestInfo(
            scientificName = "Crambidae",
            commonName = "Stem Borers / Moths",
            tagalogName = "Aksip / Kuwong",
            nameRes = R.string.crambidae_name,
            generalInfoRes = R.string.crambidae_general_desc,
            lifeCycleRes = R.string.crambidae_lifecycle,
            bioControlsRes = R.string.crambidae_bio_controls,
            culturalRes = R.string.crambidae_cultural,
            chemicalRes = R.string.crambidae_chemical
        ),
        "noctuidae" to PestInfo(
            scientificName = "Noctuidae",
            commonName = "Armyworms",
            tagalogName = "Harabas / Uod militar",
            nameRes = R.string.noctuidae_name,
            generalInfoRes = R.string.noctuidae_general_desc,
            lifeCycleRes = R.string.noctuidae_lifecycle,
            bioControlsRes = R.string.noctuidae_bio_controls,
            culturalRes = R.string.noctuidae_cultural,
            chemicalRes = R.string.noctuidae_chemical
        ),
        "delphacidae" to PestInfo(
            scientificName = "Delphacidae",
            commonName = "Planthoppers",
            tagalogName = "Ngusong kabayo - Brown",
            nameRes = R.string.delphacidae_name,
            generalInfoRes = R.string.delphacidae_general_desc,
            lifeCycleRes = R.string.delphacidae_lifecycle,
            bioControlsRes = R.string.delphacidae_bio_controls,
            culturalRes = R.string.delphacidae_cultural,
            chemicalRes = R.string.delphacidae_chemical
        ),
        "cicadellidae" to PestInfo(
            scientificName = "Cicadellidae",
            commonName = "Leafhoppers",
            tagalogName = "Ngusong kabayo - Green",
            nameRes = R.string.cicadellidae_name,
            generalInfoRes = R.string.cicadellidae_general_desc,
            lifeCycleRes = R.string.cicadellidae_lifecycle,
            bioControlsRes = R.string.cicadellidae_bio_controls,
            culturalRes = R.string.cicadellidae_cultural,
            chemicalRes = R.string.cicadellidae_chemical
        ),
        "hesperiidae" to PestInfo(
            scientificName = "Hesperiidae",
            commonName = "Skippers",
            tagalogName = "Paruparo / Kinikiling",
            nameRes = R.string.hesperiidae_name,
            generalInfoRes = R.string.hesperiidae_general_desc,
            lifeCycleRes = R.string.hesperiidae_lifecycle,
            bioControlsRes = R.string.hesperiidae_bio_controls,
            culturalRes = R.string.hesperiidae_cultural,
            chemicalRes = R.string.hesperiidae_chemical
        ),
        "ampullariidae" to PestInfo(
            scientificName = "Ampullariidae",
            commonName = "Golden Apple Snail - Eggs",
            tagalogName = "Itlog ng Kuhol",
            nameRes = R.string.ampullariidae_name,
            generalInfoRes = R.string.ampullariidae_general_desc,
            lifeCycleRes = R.string.ampullariidae_lifecycle,
            bioControlsRes = R.string.ampullariidae_bio_controls,
            culturalRes = R.string.ampullariidae_cultural,
            chemicalRes = R.string.ampullariidae_chemical
        ),
        "pentatomidae" to PestInfo(
            scientificName = "Pentatomidae",
            commonName = "Rice Black Bug",
            tagalogName = "Itim na atangya",
            nameRes = R.string.pentatomidae_name,
            generalInfoRes = R.string.pentatomidae_general_desc,
            lifeCycleRes = R.string.pentatomidae_lifecycle,
            bioControlsRes = R.string.pentatomidae_bio_controls,
            culturalRes = R.string.pentatomidae_cultural,
            chemicalRes = R.string.pentatomidae_chemical
        ),
        "pyrgomorphidae" to PestInfo(
            scientificName = "Pyrgomorphidae",
            commonName = "Slant-faced Grasshopper",
            tagalogName = "Tipaklong",
            nameRes = R.string.pyrgomorphidae_name,
            generalInfoRes = R.string.pyrgomorphidae_general_desc,
            lifeCycleRes = R.string.pyrgomorphidae_lifecycle,
            bioControlsRes = R.string.pyrgomorphidae_bio_controls,
            culturalRes = R.string.pyrgomorphidae_cultural,
            chemicalRes = R.string.pyrgomorphidae_chemical
        ),
        "chrysomelidae" to PestInfo(
            scientificName = "Chrysomelidae",
            commonName = "Leaf Beetles",
            tagalogName = "Salagubang / Hispa",
            nameRes = R.string.chrysomelidae_name,
            generalInfoRes = R.string.chrysomelidae_general_desc,
            lifeCycleRes = R.string.chrysomelidae_lifecycle,
            bioControlsRes = R.string.chrysomelidae_bio_controls,
            culturalRes = R.string.chrysomelidae_cultural,
            chemicalRes = R.string.chrysomelidae_chemical
        ),
        "acrididae" to PestInfo(
            scientificName = "Acrididae",
            commonName = "Locusts / Grasshoppers",
            tagalogName = "Balang",
            nameRes = R.string.acrididae_name,
            generalInfoRes = R.string.acrididae_general_desc,
            lifeCycleRes = R.string.acrididae_lifecycle,
            bioControlsRes = R.string.acrididae_bio_controls,
            culturalRes = R.string.acrididae_cultural,
            chemicalRes = R.string.acrididae_chemical
        ),
        "coreidae" to PestInfo(
            scientificName = "Coreidae",
            commonName = "Horned Coreid Bugs",
            tagalogName = "Atangya",
            nameRes = R.string.coreidae_name,
            generalInfoRes = R.string.coreidae_general_desc,
            lifeCycleRes = R.string.coreidae_lifecycle,
            bioControlsRes = R.string.coreidae_bio_controls,
            culturalRes = R.string.coreidae_cultural,
            chemicalRes = R.string.coreidae_chemical
        )
    )

    /**
     * Look up pest info by the raw label string from the YOLO model.
     * Returns a fallback PestInfo if not found.
     */
    fun lookup(rawLabel: String): PestInfo {
        val key = rawLabel.trim().lowercase()
        
        // 1. Direct key match (e.g. from model)
        dictionary[key]?.let { return it }

        // 2. Reverse lookup by Display Name, Common Name or Scientific Name (e.g. from History DB)
        for ((_, info) in dictionary) {
            val displayName = "${info.commonName} (${info.tagalogName})".lowercase()
            if (displayName == key || 
                info.commonName.lowercase() == key || 
                info.scientificName.lowercase() == key) {
                return info
            }
        }

        // Fallback
        return PestInfo(
            scientificName = rawLabel,
            commonName = rawLabel.replaceFirstChar { it.uppercase() },
            tagalogName = "Hindi kilala",
            nameRes = 0,
            generalInfoRes = 0,
            lifeCycleRes = 0,
            bioControlsRes = 0,
            culturalRes = 0,
            chemicalRes = 0
        )
    }

    /**
     * Get the localized display name directly from a raw label.
     */
    fun getDisplayName(context: Context, rawLabel: String): String {
        val info = lookup(rawLabel)
        return if (info.nameRes != 0) {
            context.getString(info.nameRes)
        } else {
            "${info.commonName} (${info.tagalogName})"
        }
    }

    /**
     * Get a web-search-friendly query string.
     */
    fun getSearchQuery(rawLabel: String): String {
        return "${lookup(rawLabel).commonName} agricultural pest"
    }

    /**
     * Get all known pest entries.
     */
    fun getAllEntries(): Map<String, PestInfo> = dictionary
}
