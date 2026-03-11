package com.rakshak.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.rakshak.database.entities.ScanStats

/**
 * Data Access Object for ScanStats table.
 */
@Dao
interface ScanStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: ScanStats)

    @Update
    suspend fun updateStats(stats: ScanStats)

    @Query("SELECT * FROM scan_stats WHERE id = 1")
    fun getStats(): LiveData<ScanStats>

    @Query("SELECT * FROM scan_stats WHERE id = 1")
    suspend fun getStatsSync(): ScanStats?

    @Query("""
        UPDATE scan_stats 
        SET totalScanned = totalScanned + 1,
            highRiskCount = highRiskCount + :highRisk,
            suspiciousCount = suspiciousCount + :suspicious,
            safeCount = safeCount + :safe
        WHERE id = 1
    """)
    suspend fun incrementStats(highRisk: Int, suspicious: Int, safe: Int)
}
