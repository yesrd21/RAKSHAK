package com.rakshak.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rakshak.analyzer.BehavioralFraudAnalyzer
import com.rakshak.database.entities.FirestoreFraudReport
import com.rakshak.repository.FirestoreRepository
import kotlinx.coroutines.launch

/**
 * RegisterFraudViewModel
 *
 * Submits fraud reports (SMS, Call, or Email) to Firebase Firestore.
 * Call reports don't require a message — description is optional.
 */
class RegisterFraudViewModel(application: Application) : AndroidViewModel(application) {

    private val firestoreRepo = FirestoreRepository()

    private val _submitStatus = MutableLiveData<SubmitStatus?>(null)
    val submitStatus: LiveData<SubmitStatus?> = _submitStatus

    sealed class SubmitStatus {
        object Success : SubmitStatus()
        data class Error(val message: String) : SubmitStatus()
        object Loading : SubmitStatus()
    }

    val fraudCategories = listOf(
        "Phishing",
        "OTP Scam",
        "Loan Scam",
        "KYC Scam",
        "Bank Fraud",
        "Investment Scam",
        "Lottery Scam",
        "Call Fraud",
        "Other"
    )

    /**
     * @param sourceType  "sms" | "call" | "email"
     * @param messagePattern  For SMS: message text. For call: optional description.
     */
    fun submitFraudReport(
        sourceNumber: String,
        sourceEmail: String,
        messagePattern: String,
        category: String,
        sourceType: String = "sms"
    ) {
        val identifier = sourceNumber.trim().ifBlank { sourceEmail.trim() }

        if (identifier.isBlank()) {
            _submitStatus.value = SubmitStatus.Error("Please enter a phone number or email.")
            return
        }

        // Message is required for SMS/email reports but optional for calls
        if (sourceType != "call" && messagePattern.isBlank()) {
            _submitStatus.value = SubmitStatus.Error("Please enter the fraud message or description.")
            return
        }

        if (category.isBlank()) {
            _submitStatus.value = SubmitStatus.Error("Please select a fraud category.")
            return
        }

        _submitStatus.value = SubmitStatus.Loading

        viewModelScope.launch {
            // For call reports, run a basic analysis on the description (or blank)
            val analysis = BehavioralFraudAnalyzer.analyze(
                message = messagePattern.ifBlank { "call fraud" },
                sender  = identifier
            )

            val report = FirestoreFraudReport(
                sourceIdentifier = identifier,
                email            = sourceEmail.trim(),
                messagePattern   = messagePattern.trim(),
                category         = category,
                sourceType       = sourceType,
                reportCount      = 1L,
                timestamp        = System.currentTimeMillis(),
                riskScore        = analysis.riskScore
            )

            val result = firestoreRepo.addFraudReport(report)

            if (result.isSuccess) {
                _submitStatus.postValue(SubmitStatus.Success)
            } else {
                _submitStatus.postValue(
                    SubmitStatus.Error("Failed to submit: ${result.exceptionOrNull()?.message}")
                )
            }
        }
    }

    fun resetStatus() {
        _submitStatus.value = null
    }
}
