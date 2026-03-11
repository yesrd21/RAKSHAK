package com.rakshak.receiver

import android.util.Log

/**
 * FraudSequenceStore
 *
 * Cross-signal behavioral sequence detection engine.
 *
 * The key insight: real scams use DIFFERENT numbers for calls vs SMS.
 *   Call from +91-98765-43210  →  OTP from VK-SBIOTP
 *   Call from unknown          →  phishing link from AD-HDFCBK
 *
 * Old approach (same-number only) misses these entirely.
 * This engine detects by TIME PROXIMITY + CONTENT TYPE, not sender match.
 *
 * ══════════════════════════════════════════════════════════════
 *  Three patterns detected:
 *
 *  Pattern 1 — SMS → Call  (scammer warned "expect our call")
 *    Suspicious SMS arrives, then a call comes within 10 min
 *    Bonus score: +20
 *
 *  Pattern 2 — Call → OTP SMS  (classic "I'll send you a verification code")
 *    Unknown call, then OTP SMS within 3 min
 *    Bonus score: +30
 *
 *  Pattern 3 — Call → Phishing Link SMS  ("download the KYC form I send")
 *    Unknown call, then link/URL SMS within 3 min
 *    Bonus score: +25
 *
 *  Pattern 4 — Multi-Actor  (3+ distinct actors in 5 min)
 *    Coordinated gang activity
 *    Bonus score: +20 (applied as extra signal)
 * ══════════════════════════════════════════════════════════════
 *
 * All data is in-memory only — never persisted, clears on restart.
 */
object FraudSequenceStore {

    private const val TAG = "FraudSequenceStore"

    // ── Time windows ──────────────────────────────────────────────────────
    private const val CALL_BEFORE_OTP_WINDOW_MS   = 3L  * 60_000  // 3 min
    private const val CALL_BEFORE_LINK_WINDOW_MS  = 3L  * 60_000  // 3 min
    private const val CALL_BEFORE_ANY_WINDOW_MS   = 5L  * 60_000  // 5 min
    private const val SMS_BEFORE_CALL_WINDOW_MS   = 10L * 60_000  // 10 min
    private const val MULTI_ACTOR_WINDOW_MS        = 5L  * 60_000  // 5 min
    private const val STORE_RETENTION_MS           = 15L * 60_000  // 15 min (max store age)

    // ── Data classes ──────────────────────────────────────────────────────

    data class RecentCallEvent(
        val callerNumber: String,
        val isUnknown: Boolean,   // number not in contacts / unavailable
        val isHidden: Boolean,    // explicitly hidden/private
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class SmsType { OTP, PHISHING_LINK, SUSPICIOUS_OTHER, SAFE }

    data class RecentSmsEvent(
        val sender: String,
        val smsType: SmsType,
        val riskScore: Int,
        val bodySnippet: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class SequenceMatch(
        val patternName: String,
        val bonusScore: Int,
        val callEvent: RecentCallEvent?,
        val smsEvent: RecentSmsEvent?,
        val gapSeconds: Long,
        val description: String,
        val isMultiActor: Boolean = false
    )

    // ── In-memory stores ──────────────────────────────────────────────────
    private val recentCalls    = mutableListOf<RecentCallEvent>()
    private val recentSmsList  = mutableListOf<RecentSmsEvent>()

    // ── Public: record every incoming call ────────────────────────────────

    /**
     * Must be called for EVERY incoming call (not just suspicious ones)
     * so we can later detect Call → SMS sequences.
     * isUnknown = number is blank, "Unknown", or not in contacts.
     */
    fun recordCall(callerNumber: String, isUnknown: Boolean, isHidden: Boolean) {
        pruneAll()
        recentCalls.add(RecentCallEvent(callerNumber, isUnknown, isHidden))
        Log.d(TAG, "Call recorded: '$callerNumber' unknown=$isUnknown hidden=$isHidden")
    }

    /**
     * Called by SmsReceiver for every SMS.
     * Returns a SequenceMatch if this SMS COMPLETES a fraud sequence
     * (i.e. an unknown call came first), else null.
     *
     * Detects Pattern 2 (Call → OTP) and Pattern 3 (Call → Link).
     */
    fun onSmsReceived(sender: String, body: String, riskScore: Int): SequenceMatch? {
        pruneAll()

        val smsType  = classifySms(body)
        val snippet  = body.take(80)
        val smsEvent = RecentSmsEvent(sender, smsType, riskScore, snippet)
        recentSmsList.add(smsEvent)

        // Only check sequence if this SMS has something worth correlating
        if (smsType == SmsType.SAFE && riskScore < 20) return null

        // Choose the right time window based on SMS type
        val window = when (smsType) {
            SmsType.OTP           -> CALL_BEFORE_OTP_WINDOW_MS
            SmsType.PHISHING_LINK -> CALL_BEFORE_LINK_WINDOW_MS
            else                  -> CALL_BEFORE_ANY_WINDOW_MS
        }

        // Find most recent unknown/hidden call that happened BEFORE this SMS
        val matchingCall = recentCalls
            .filter { it.isUnknown || it.isHidden }
            .filter { smsEvent.timestamp - it.timestamp in 1..window }
            .maxByOrNull { it.timestamp }

        val match = if (matchingCall != null) {
            buildSmsAfterCallMatch(matchingCall, smsEvent)
        } else null

        // Also check multi-actor
        val multi = detectMultiActorPattern()
        if (match != null && multi) {
            return match.copy(
                isMultiActor = true,
                bonusScore   = (match.bonusScore + 20).coerceAtMost(50),
                description  = match.description + " ⚠️ Multiple actors detected."
            )
        }
        return match
    }

    /**
     * Called by CallReceiver AFTER a call is recorded.
     * Checks if a suspicious SMS arrived before this call (Pattern 1: SMS → Call).
     * Returns SequenceMatch if detected, else null.
     */
    fun checkPatternAfterCall(): SequenceMatch? {
        pruneAll()

        // Find most recent suspicious/OTP SMS that arrived BEFORE the latest call
        val lastCall = recentCalls.maxByOrNull { it.timestamp } ?: return null

        val priorSuspSms = recentSmsList
            .filter { it.smsType != SmsType.SAFE || it.riskScore >= 30 }
            .filter { lastCall.timestamp - it.timestamp in 1..SMS_BEFORE_CALL_WINDOW_MS }
            .maxByOrNull { it.timestamp }
            ?: return null

        val gapSec = (lastCall.timestamp - priorSuspSms.timestamp) / 1000

        return SequenceMatch(
            patternName  = "SMS → Call Sequence",
            bonusScore   = 20,
            callEvent    = lastCall,
            smsEvent     = priorSuspSms,
            gapSeconds   = gapSec,
            description  = "Suspicious SMS arrived ${gapSec}s before this call — " +
                           "scammer may be following up on: \"${priorSuspSms.bodySnippet}\""
        )
    }

    /**
     * True if 3+ distinct actors (callers + SMS senders) in last 5 minutes.
     */
    fun detectMultiActorPattern(): Boolean {
        val now     = System.currentTimeMillis()
        val callers = recentCalls
            .filter { now - it.timestamp <= MULTI_ACTOR_WINDOW_MS }
            .map { it.callerNumber }.toSet()
        val senders = recentSmsList
            .filter { now - it.timestamp <= MULTI_ACTOR_WINDOW_MS }
            .filter { it.smsType != SmsType.SAFE }
            .map { it.sender }.toSet()
        val total = (callers + senders).size
        Log.d(TAG, "Multi-actor check: $total distinct actors in last 5 min")
        return total >= 3
    }

    // ── SMS type classifier ───────────────────────────────────────────────

    fun classifySms(body: String): SmsType {
        val lower = body.lowercase()
        val otpKeywords = listOf(
            "otp", "one time password", "verification code", "your code is",
            "enter code", "do not share", "do not disclose", "passcode",
            "pin is", "your pin", "authentication code", "login code",
            "security code", "2fa", "two factor", "confirm your"
        )
        val linkKeywords = listOf(
            "http://", "https://", "bit.ly", "www.", "click here", "tap here",
            "download", "kyc", "verify now", "update your", "login at",
            "form link", "fill the form", "complete kyc", "link below"
        )
        return when {
            otpKeywords.any  { lower.contains(it) } -> SmsType.OTP
            linkKeywords.any { lower.contains(it) } -> SmsType.PHISHING_LINK
            else                                    -> SmsType.SUSPICIOUS_OTHER
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun buildSmsAfterCallMatch(
        call: RecentCallEvent,
        sms: RecentSmsEvent
    ): SequenceMatch {
        val gapSec = (sms.timestamp - call.timestamp) / 1000

        return when (sms.smsType) {
            SmsType.OTP -> SequenceMatch(
                patternName = "Call → OTP Scam",
                bonusScore  = 30,
                callEvent   = call,
                smsEvent    = sms,
                gapSeconds  = gapSec,
                description = "Unknown call ${gapSec}s ago, then OTP SMS now — " +
                              "caller likely asked you to read this OTP aloud. " +
                              "⛔ DO NOT share this code with anyone."
            )
            SmsType.PHISHING_LINK -> SequenceMatch(
                patternName = "Call → Phishing Link",
                bonusScore  = 25,
                callEvent   = call,
                smsEvent    = sms,
                gapSeconds  = gapSec,
                description = "Unknown call ${gapSec}s ago, then link SMS now — " +
                              "possible KYC/download scam. " +
                              "⛔ DO NOT tap the link."
            )
            else -> SequenceMatch(
                patternName = "Coordinated Call + SMS",
                bonusScore  = 15,
                callEvent   = call,
                smsEvent    = sms,
                gapSeconds  = gapSec,
                description = "Unknown call ${gapSec}s before this suspicious message — " +
                              "possible coordinated scam attempt."
            )
        }
    }

    private fun pruneAll() {
        val cutoff = System.currentTimeMillis() - STORE_RETENTION_MS
        recentCalls.removeAll   { it.timestamp < cutoff }
        recentSmsList.removeAll { it.timestamp < cutoff }
    }
}
