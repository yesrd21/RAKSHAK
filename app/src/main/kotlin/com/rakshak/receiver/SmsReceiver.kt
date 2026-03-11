package com.rakshak.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.rakshak.analyzer.BehavioralFraudAnalyzer
import com.rakshak.analyzer.RiskLevel
import com.rakshak.database.RakshakDatabase
import com.rakshak.database.entities.ScanStats
import com.rakshak.database.entities.SmsLogEntry
import com.rakshak.notifications.RakshakNotificationManager
import com.rakshak.repository.FirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * SmsReceiver — unchanged flow, sms_log persistence added at Step 6.
 *
 *  1. Receive SMS
 *  2. Check FraudSequenceStore
 *  3. Firestore community lookup
 *  4. Full behavioral analysis
 *  5. Update ScanStats
 *  6. *** NEW *** Save result to sms_log Room table
 *  7. Record in CallCorrelationStore
 *  8. Notify if suspicious/high-risk
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    private val firestoreRepo = FirestoreRepository()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "Unknown"
        val body   = messages.joinToString("") { it.messageBody ?: "" }

        Log.d(TAG, "SMS from: $sender")

        CoroutineScope(Dispatchers.IO).launch {
            processMessage(context, sender, body)
        }
    }

    private suspend fun processMessage(context: Context, sender: String, body: String) {
        // Step 1: Sequence check
        val seqMatch = FraudSequenceStore.onSmsReceived(
            sender    = sender,
            body      = body,
            riskScore = 0
        )
        if (seqMatch != null) {
            Log.d(TAG, "Sequence detected: ${seqMatch.patternName} bonus=${seqMatch.bonusScore}")
        }

        // Step 2: Firestore community lookup
        val communityCount = firestoreRepo.getReportCountForSender(sender)
        Log.d(TAG, "Community count for $sender: $communityCount")

        // Step 3: Full analysis
        val result = BehavioralFraudAnalyzer.analyze(
            message              = body,
            sender               = sender,
            communityReportCount = communityCount,
            sequenceBonus        = seqMatch?.bonusScore ?: 0,
            sequencePatternName  = seqMatch?.patternName
        )

        Log.d(TAG, "Score: ${result.riskScore} " +
            "(behavioral=${result.behavioralScore} + community=${result.communityScore} " +
            "+ sequence=${result.sequenceBonus}) → ${result.riskLevel.label}")

        // Step 4: Update ScanStats
        val db       = RakshakDatabase.getDatabase(context)
        val statsDao = db.scanStatsDao()
        if (statsDao.getStatsSync() == null) {
            statsDao.insertStats(ScanStats(id = 1))
        }
        statsDao.incrementStats(
            highRisk   = if (result.riskLevel == RiskLevel.HIGH_RISK) 1 else 0,
            suspicious = if (result.riskLevel == RiskLevel.SUSPICIOUS) 1 else 0,
            safe       = if (result.riskLevel == RiskLevel.SAFE) 1 else 0
        )

        // Step 5 (NEW): Save full result to sms_log for Message History screen
        db.smsLogDao().insertSmsLog(
            SmsLogEntry(
                sender               = sender,
                riskScore            = result.riskScore,
                riskLevel            = result.riskLevel.label,
                detectedPatterns     = result.detectedPatterns.joinToString("|"),
                hasUrl               = result.hasUrl,
                sequencePattern      = result.sequencePatternName,
                communityReportCount = result.communityReportCount
            )
        )
        Log.d(TAG, "SMS log saved for $sender — ${result.riskLevel.label} ${result.riskScore}")

        // Step 6: Record in CallCorrelationStore
        if (result.riskLevel != RiskLevel.SAFE) {
            CallCorrelationStore.recordSuspiciousSms(
                sender    = sender,
                riskScore = result.riskScore,
                riskLabel = result.riskLevel.label
            )
        }

        // Step 7: Notify
        if (result.riskLevel != RiskLevel.SAFE || seqMatch != null) {
            RakshakNotificationManager.showFraudAlert(
                context  = context,
                sender   = sender,
                result   = result,
                seqMatch = seqMatch
            )
        }
    }
}
