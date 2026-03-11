package com.rakshak.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ScanStats Entity - Tracks aggregate scanning statistics for the dashboard.
 * Single row table (id=1) updated on each scan.
 */
@Entity(tableName = "scan_stats")
data class ScanStats(
    @PrimaryKey
    val id: Int = 1,
    val totalScanned: Int = 0,
    val highRiskCount: Int = 0,
    val suspiciousCount: Int = 0,
    val safeCount: Int = 0
)
