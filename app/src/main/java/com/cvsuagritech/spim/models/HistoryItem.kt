package com.cvsuagritech.spim.models

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

sealed class HistoryItem {
    abstract val id: Long
    abstract val timestamp: Long
    abstract val isSynced: Boolean

    data class IdentificationItem(
        override val id: Long,
        val insectName: String,
        val confidence: Float,
        val imagePath: String? = null,
        val imageBlob: ByteArray? = null,
        override val timestamp: Long,
        override val isSynced: Boolean = false
    ) : HistoryItem()

    data class CountItem(
        override val id: Long,
        val totalCount: Int,
        val breakdown: String? = null, // Stores JSON or CSV of insect types
        val imagePath: String? = null,
        val imageBlob: ByteArray? = null,
        override val timestamp: Long,
        override val isSynced: Boolean = false
    ) : HistoryItem() {
        val severityLevel: Severity
            get() = when {
                totalCount < 5 -> Severity.LOW
                totalCount <= 20 -> Severity.MEDIUM
                else -> Severity.HIGH
            }

        enum class Severity { LOW, MEDIUM, HIGH }

        // Helper to parse breakdown string into a map of counts
        fun getBreakdownMap(): Map<String, Int> {
            val raw = breakdown ?: return emptyMap()
            val gson = Gson()
            
            return try {
                // Try parsing new format: {"insect": {"count": X, "confidence": Y}}
                val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                val complexMap: Map<String, Map<String, Any>> = gson.fromJson(raw, type)
                complexMap.mapValues { (_, value) -> (value["count"] as? Double)?.toInt() ?: 0 }
            } catch (e: Exception) {
                try {
                    // Try parsing intermediate format: {"insect": count}
                    val type = object : TypeToken<Map<String, Int>>() {}.type
                    gson.fromJson(raw, type)
                } catch (e2: Exception) {
                    // Legacy parsing: "Name:Count,Name:Count"
                    try {
                        raw.split(",").associate {
                            val parts = it.split(":")
                            parts[0] to parts[1].toInt()
                        }
                    } catch (e3: Exception) {
                        emptyMap()
                    }
                }
            }
        }

        // Helper to get detailed breakdown including confidence
        fun getDetailedBreakdown(): Map<String, Map<String, Float>> {
            val raw = breakdown ?: return emptyMap()
            val gson = Gson()
            return try {
                val type = object : TypeToken<Map<String, Map<String, Float>>>() {}.type
                gson.fromJson(raw, type)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}
