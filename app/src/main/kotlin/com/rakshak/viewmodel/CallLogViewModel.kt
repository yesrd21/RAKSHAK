package com.rakshak.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rakshak.analyzer.CallRiskAnalyzer
import com.rakshak.database.RakshakDatabase
import com.rakshak.database.entities.CallLogEntry
import com.rakshak.database.entities.FirestoreFraudReport
import com.rakshak.repository.FirestoreRepository
import kotlinx.coroutines.launch

class CallLogViewModel(application: Application) : AndroidViewModel(application) {

    private val callLogDao    = RakshakDatabase.getDatabase(application).callLogDao()
    private val firestoreRepo = FirestoreRepository()

    val callLogs: LiveData<List<CallLogEntry>> = callLogDao.getAllCallLogs()

    private val _testResult = MutableLiveData<CallRiskAnalyzer.CallAnalysisResult?>()
    val testResult: LiveData<CallRiskAnalyzer.CallAnalysisResult?> = _testResult

    private val _totalCount      = MutableLiveData(0)
    val totalCount: LiveData<Int> = _totalCount

    private val _correlatedCount      = MutableLiveData(0)
    val correlatedCount: LiveData<Int> = _correlatedCount

    // Community search
    private val _communityResults = MutableLiveData<List<FirestoreFraudReport>>(emptyList())
    val communityResults: LiveData<List<FirestoreFraudReport>> = _communityResults

    private val _communityLoading = MutableLiveData(false)
    val communityLoading: LiveData<Boolean> = _communityLoading

    init {
        refreshCounts()
    }

    fun testNumber(number: String) {
        val result = CallRiskAnalyzer.analyze(number.trim())
        _testResult.value = result
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    fun searchCommunity(number: String) {
        if (number.isBlank()) return
        _communityLoading.value = true
        viewModelScope.launch {
            val results = firestoreRepo.searchFraudReports(number.trim())
            _communityResults.postValue(results)
            _communityLoading.postValue(false)
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            callLogDao.clearAll()
            refreshCounts()
        }
    }

    private fun refreshCounts() {
        viewModelScope.launch {
            _totalCount.postValue(callLogDao.getTotalCount())
            _correlatedCount.postValue(callLogDao.getCorrelatedCount())
        }
    }
}
