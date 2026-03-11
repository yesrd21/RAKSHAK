package com.rakshak.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SmsLogEntry — stores every analysed SMS result locally.
 * Message body is NEVER stored — only metadata and scores (privacy safe).
 */
@Entity(tableName = "sms_log")
data class SmsLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sender: String,
    val riskScore: Int,
    val riskLevel: String,              // "Safe" / "Suspicious" / "High Risk"
    val detectedPatterns: String,       // pipe-separated e.g. "Fear/Threat|Urgency"
    val hasUrl: Boolean,
    val sequencePattern: String?,       // e.g. "Call → OTP Scam" or null
    val communityReportCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
