package com.cvsuagritech.spim.models

import java.text.SimpleDateFormat
import java.util.*

data class PestRecord(
    val id: Long = 0,
    val pestName: String,
    val confidence: Float,
    val imagePath: String? = null,
    val imageBlob: ByteArray? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null
) {
    // Helper function to get formatted date and time
    fun getFormattedDateTime(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    // Helper function to get formatted date only
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    // Helper function to get formatted time only
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    // Helper function to get confidence percentage
    fun getConfidencePercentage(): Int {
        return (confidence * 100).toInt()
    }

    // Helper function to get confidence level description
    fun getConfidenceLevel(): String {
        return when {
            confidence >= 0.8f -> "High"
            confidence >= 0.6f -> "Medium"
            confidence >= 0.4f -> "Low"
            else -> "Very Low"
        }
    }

    // Helper function to check if record has image
    fun hasImage(): Boolean {
        return !imagePath.isNullOrEmpty() || imageBlob != null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PestRecord

        if (id != other.id) return false
        if (pestName != other.pestName) return false
        if (confidence != other.confidence) return false
        if (imagePath != other.imagePath) return false
        if (!imageBlob.contentEquals(other.imageBlob)) return false
        if (timestamp != other.timestamp) return false
        if (notes != other.notes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + pestName.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + (imagePath?.hashCode() ?: 0)
        result = 31 * result + (imageBlob?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (notes?.hashCode() ?: 0)
        return result
    }
}
