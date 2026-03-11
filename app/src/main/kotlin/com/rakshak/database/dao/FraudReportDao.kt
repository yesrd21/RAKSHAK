package com.rakshak.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.rakshak.database.entities.FraudReport

/**
 * Data Access Object for FraudReport table.
 */
@Dao
interface FraudReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFraudReport(report: FraudReport): Long

    @Update
    suspend fun updateFraudReport(report: FraudReport)

    @Delete
    suspend fun deleteFraudReport(report: FraudReport)

    @Query("SELECT * FROM fraud_reports ORDER BY timestamp DESC")
    fun getAllReports(): LiveData<List<FraudReport>>

    @Query("SELECT * FROM fraud_reports ORDER BY timestamp DESC")
    suspend fun getAllReportsSync(): List<FraudReport>

    @Query("""
        SELECT * FROM fraud_reports 
        WHERE sourceIdentifier LIKE '%' || :query || '%' 
        OR messagePattern LIKE '%' || :query || '%'
        OR category LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    suspend fun searchReports(query: String): List<FraudReport>

    @Query("SELECT * FROM fraud_reports WHERE sourceIdentifier = :source LIMIT 1")
    suspend fun getReportBySource(source: String): FraudReport?

    @Query("UPDATE fraud_reports SET reportCount = reportCount + 1 WHERE sourceIdentifier = :source")
    suspend fun incrementReportCount(source: String)

    @Query("SELECT COUNT(*) FROM fraud_reports")
    suspend fun getTotalReportCount(): Int

    @Query("SELECT * FROM fraud_reports ORDER BY timestamp DESC LIMIT 10")
    fun getRecentReports(): LiveData<List<FraudReport>>
}
