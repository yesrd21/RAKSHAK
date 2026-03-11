package com.rakshak.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rakshak.analyzer.AnalysisResult
import com.rakshak.analyzer.BehavioralFraudAnalyzer
import com.rakshak.database.RakshakDatabase
import com.rakshak.database.entities.ScanStats
import kotlinx.coroutines.launch

/**
 * DashboardViewModel
 *
 * - Reads ScanStats from Room (local, offline)
 * - Runs behavioral-only test analysis (no Firestore lookup in test mode)
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db       = RakshakDatabase.getDatabase(application)
    private val statsDao = db.scanStatsDao()

    val scanStats: LiveData<ScanStats> = statsDao.getStats()

    private val _testResult = MutableLiveData<AnalysisResult?>()
    val testResult: LiveData<AnalysisResult?> = _testResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            // Ensure the stats row exists
            if (statsDao.getStatsSync() == null) {
                statsDao.insertStats(ScanStats(id = 1))
            }
        }
    }

    /**
     * Test a message manually from the Dashboard.
     * Uses behavioral analysis only (community score = 0 for instant local feedback).
     */
    fun testMessage(message: String, sender: String = "Unknown") {
        _isLoading.value = true
        viewModelScope.launch {
            val result = BehavioralFraudAnalyzer.analyze(message, sender, communityReportCount = 0)

            statsDao.incrementStats(
                highRisk   = if (result.riskScore > 70) 1 else 0,
                suspicious = if (result.riskScore in 31..70) 1 else 0,
                safe       = if (result.riskScore <= 30) 1 else 0
            )

            _testResult.postValue(result)
            _isLoading.postValue(false)
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }
}
