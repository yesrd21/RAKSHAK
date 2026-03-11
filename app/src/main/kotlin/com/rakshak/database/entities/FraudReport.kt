package com.rakshak.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FraudReport Entity - Represents a reported fraud source stored in Room DB.
 * Only stored when user MANUALLY reports fraud (privacy preserved).
 */
@Entity(tableName = "fraud_reports")
data class FraudReport(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceIdentifier: String,       // Phone number or email
    val messagePattern: String,         // The fraud message text
    val category: String,               // e.g. Phishing, OTP Scam, etc.
    val reportCount: Int = 1,           // How many times this source was reported
    val timestamp: Long = System.currentTimeMillis(),
    val riskScore: Int = 0              // Calculated risk score (0–100)
)
