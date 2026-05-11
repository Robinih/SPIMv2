package com.cvsuagritech.spim.models

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

sealed class HistoryItem {
    abstract val id: Long
    abstract val timestamp: Long
    abstract val isSynced: Boolean

    /** Section header shown between date groups in the history list */
    data class DateHeader(
        val label: String,
        override val id: Long = -1,
        override val timestamp: Long = 0,
        override val isSynced: Boolean = true
    ) : HistoryItem()

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

        fun getBreakdownMap(): Map<String, Int> {
            val raw = breakdown ?: return emptyMap()
            
            // Try handling legacy non-JSON formats like "5 Rice Bug, 3 Leafhopper" or "Rice Bug:5"
            if (!raw.trim().startsWith("{")) {
                val map = mutableMapOf<String, Int>()
                try {
                    val parts = raw.split(",")
                    for (p in parts) {
                        val piece = p.trim()
                        if (piece.contains(":")) {
                            // "Rice Bug:5"
                            val split = piece.split(":")
                            map[split[0].trim()] = split[1].trim().toInt()
                        } else {
                            // "5 Rice Bug"
                            val firstSpace = piece.indexOf(" ")
                            if (firstSpace != -1) {
                                val countStr = piece.substring(0, firstSpace).trim()
                                val nameStr = piece.substring(firstSpace + 1).trim()
                                map[nameStr] = countStr.toIntOrNull() ?: 1
                            } else {
                                map[piece] = 1
                            }
                        }
                    }
                    if (map.isNotEmpty()) return map
                } catch (e: Exception) {
                    // Fallthrough to JSON parsing if it fails
                }
            }

            val gson = Gson()
            return try {
                // Try parsing new format: {"insect": {"count": X, "confidence": Y}}
                val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                val complexMap: Map<String, Map<String, Any>> = gson.fromJson(raw, type)
                
                // We must handle cases where someone legitimately saves {"Rice Bug": 5} and Gson mistakenly passes it as Map<String, Any> 
                // but value['count'] will be null cause value is just a Double 5.0
                val result = mutableMapOf<String, Int>()
                var allParsedSuccessfully = true
                
                for ((key, value) in complexMap) {
                    if (value is Map<*, *>) {
                        result[key] = (value["count"] as? Double)?.toInt() ?: 0
                    } else if (value is Double) {
                        result[key] = value.toInt()
                    } else {
                        allParsedSuccessfully = false
                    }
                }
                
                if (allParsedSuccessfully && result.isNotEmpty()) result else throw Exception("Format mismatch")
            } catch (e: Exception) {
                try {
                    // Try parsing intermediate format: {"insect": count}
                    val type = object : TypeToken<Map<String, Int>>() {}.type
                    gson.fromJson(raw, type)
                } catch (e2: Exception) {
                    emptyMap()
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
