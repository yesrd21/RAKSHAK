package com.rakshak.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * CallLogEntry — local Room entity.
 * Stores one flagged call event. Never stores audio or call content.
 */
@Entity(tableName = "call_log")
data class CallLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val callerNumber: String,       // "Hidden" if unavailable
    val riskScore: Int,
    val riskLevel: String,          // "Suspicious" or "High Risk"
    val signals: String,            // pipe-separated list of detected signals
    val isCorrelated: Boolean,      // true if matched a recent suspicious SMS
    val correlatedSmsScore: Int,    // riskScore of the matched SMS (0 if none)
    val timestamp: Long = System.currentTimeMillis()
)
