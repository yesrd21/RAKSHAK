package com.rakshak.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.rakshak.database.RakshakDatabase
import com.rakshak.database.entities.SmsLogEntry
import kotlinx.coroutines.launch

class SmsHistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = RakshakDatabase.getDatabase(app).smsLogDao()
    private val allLogs: LiveData<List<SmsLogEntry>> = dao.getAllSmsLogs()

    // Current active filter — "ALL", "High Risk", "Suspicious", "Safe"
    private val _filter = MutableLiveData("ALL")
    val currentFilter: LiveData<String> = _filter

    // Filtered list — reacts whenever allLogs or _filter changes
    val filteredLogs: LiveData<List<SmsLogEntry>> = allLogs.map { list ->
        when (_filter.value) {
            "High Risk"  -> list.filter { it.riskLevel == "High Risk" }
            "Suspicious" -> list.filter { it.riskLevel == "Suspicious" }
            "Safe"       -> list.filter { it.riskLevel == "Safe" }
            else         -> list   // ALL
        }
    }

    // Counts for the 4 filter badge numbers
    val totalCount      = allLogs.map { it.size }
    val highRiskCount   = allLogs.map { l -> l.count { it.riskLevel == "High Risk" } }
    val suspiciousCount = allLogs.map { l -> l.count { it.riskLevel == "Suspicious" } }
    val safeCount       = allLogs.map { l -> l.count { it.riskLevel == "Safe" } }

    fun setFilter(filter: String) {
        _filter.value = filter
    }

    fun clearLog() = viewModelScope.launch {
        dao.clearAll()
    }
}
