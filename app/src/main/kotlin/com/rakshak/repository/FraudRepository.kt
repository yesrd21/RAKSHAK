package com.rakshak.repository

import androidx.lifecycle.LiveData
import com.rakshak.database.dao.FraudReportDao
import com.rakshak.database.dao.ScanStatsDao
import com.rakshak.database.entities.FraudReport
import com.rakshak.database.entities.ScanStats

/**
 * FraudRepository
 *
 * Single source of truth for all data operations.
 * ViewModels interact ONLY through this repository.
 */
class FraudRepository(
    private val fraudReportDao: FraudReportDao,
    private val scanStatsDao: ScanStatsDao
) {

    // ── Fraud Reports ───────────────────────────────────────────────────────

    val allReports: LiveData<List<FraudReport>> = fraudReportDao.getAllReports()
    val recentReports: LiveData<List<FraudReport>> = fraudReportDao.getRecentReports()

    suspend fun insertReport(report: FraudReport): Long {
        // If source already exists, increment count instead of duplicate
        val existing = fraudReportDao.getReportBySource(report.sourceIdentifier)
        return if (existing != null) {
            fraudReportDao.incrementReportCount(report.sourceIdentifier)
            existing.id
        } else {
            fraudReportDao.insertFraudReport(report)
        }
    }

    suspend fun searchReports(query: String): List<FraudReport> {
        return fraudReportDao.searchReports(query)
    }

    suspend fun getTotalReportCount(): Int {
        return fraudReportDao.getTotalReportCount()
    }

    suspend fun deleteReport(report: FraudReport) {
        fraudReportDao.deleteFraudReport(report)
    }

    // ── Scan Statistics ─────────────────────────────────────────────────────

    val scanStats: LiveData<ScanStats> = scanStatsDao.getStats()

    suspend fun initStats() {
        val existing = scanStatsDao.getStatsSync()
        if (existing == null) {
            scanStatsDao.insertStats(ScanStats(id = 1))
        }
    }

    suspend fun updateStatsForScan(isHighRisk: Boolean, isSuspicious: Boolean) {
        scanStatsDao.incrementStats(
            highRisk   = if (isHighRisk) 1 else 0,
            suspicious = if (isSuspicious) 1 else 0,
            safe       = if (!isHighRisk && !isSuspicious) 1 else 0
        )
    }
}
