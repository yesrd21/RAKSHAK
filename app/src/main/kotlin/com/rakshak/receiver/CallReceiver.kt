package com.rakshak.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import com.rakshak.analyzer.CallRiskAnalyzer
import com.rakshak.calls.AutoCallTerminator
import com.rakshak.analyzer.RiskLevel
import com.rakshak.database.RakshakDatabase
import com.rakshak.database.entities.CallLogEntry
import com.rakshak.notifications.CallAlertManager
import com.rakshak.repository.FirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.rakshak.utils.FlashAlertManager

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
    }

    private val firestoreRepo = FirestoreRepository()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        if (state == TelephonyManager.EXTRA_STATE_IDLE) {
            FlashAlertManager.stopBlinking(context)
            return
        }

        if (state != TelephonyManager.EXTRA_STATE_RINGING) return

        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            ?: "Unknown"
        Log.d(TAG, "Incoming call from: $incomingNumber")

        CoroutineScope(Dispatchers.IO).launch {
            processCall(context, incomingNumber)
        }
    }

    private suspend fun processCall(context: Context, incomingNumber: String) {
        val isHidden = incomingNumber.isBlank() ||
                incomingNumber == "Unknown" ||
                incomingNumber == "Private" ||
                incomingNumber == "Withheld" ||
                incomingNumber == "-1"

        // Skip known contacts
        if (!isHidden) {
            val contactName = getContactName(context, incomingNumber)
            if (contactName != null) {
                Log.d(TAG, "Known contact: $contactName — skipping fraud analysis")
                return
            }
        }

        FraudSequenceStore.recordCall(
            callerNumber = incomingNumber,
            isUnknown    = isHidden,
            isHidden     = isHidden
        )

        val communityCount = firestoreRepo.getReportCountForSender(incomingNumber)
        val result = CallRiskAnalyzer.analyze(incomingNumber, communityCount)

        Log.d(TAG, "Call risk: ${result.riskScore} → ${result.riskLevel.label}")

        val terminationResult = if (result.riskScore >= AutoCallTerminator.TERMINATION_THRESHOLD) {
            AutoCallTerminator.evaluate(context, result)
        } else null

        if (result.riskLevel != RiskLevel.SAFE) {
            CallAlertManager.showCallAlert(context, incomingNumber, result, terminationResult)
            FlashAlertManager.startBlinking(context)

            val db = RakshakDatabase.getDatabase(context)
            db.callLogDao().insertCallLog(
                CallLogEntry(
                    callerNumber       = if (result.isHidden) "Hidden/Private Number" else incomingNumber,
                    riskScore          = result.riskScore,
                    riskLevel          = result.riskLevel.label,
                    signals            = result.detectedSignals.joinToString("|"),
                    isCorrelated       = result.correlatedSms != null || result.sequenceMatch != null,
                    correlatedSmsScore = result.correlatedSms?.riskScore
                        ?: result.sequenceMatch?.smsEvent?.riskScore ?: 0
                )
            )
        }
    }

    private fun getContactName(context: Context, number: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Contact lookup failed: ${e.message}")
            null
        }
    }
}