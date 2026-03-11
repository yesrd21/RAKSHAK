package com.rakshak.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rakshak.database.entities.FirestoreFraudReport
import com.rakshak.repository.FirestoreRepository
import kotlinx.coroutines.launch

/**
 * SearchViewModel
 *
 * Queries Firebase Firestore for fraud history.
 * Loads all reports on init; filters via Firestore prefix queries on search.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val firestoreRepo = FirestoreRepository()

    private val _searchResults = MutableLiveData<List<FirestoreFraudReport>>(emptyList())
    val searchResults: LiveData<List<FirestoreFraudReport>> = _searchResults

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isEmpty = MutableLiveData(false)
    val isEmpty: LiveData<Boolean> = _isEmpty

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        loadAllReports()
    }

    fun loadAllReports() {
        _isLoading.value = true
        viewModelScope.launch {
            val all = firestoreRepo.getAllReports()
            _searchResults.postValue(all)
            _isEmpty.postValue(all.isEmpty())
            _isLoading.postValue(false)
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            loadAllReports()
            return
        }

        _isLoading.value = true
        _isEmpty.value   = false
        viewModelScope.launch {
            try {
                val results = firestoreRepo.searchFraudReports(query.trim())
                _searchResults.postValue(results)
                _isEmpty.postValue(results.isEmpty())
            } catch (e: Exception) {
                _error.postValue("Search failed: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun clearSearch() {
        loadAllReports()
    }
}
