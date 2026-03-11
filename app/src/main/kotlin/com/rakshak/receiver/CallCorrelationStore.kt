package com.rakshak.receiver

/**
 * CallCorrelationStore
 *
 * In-memory store that tracks the last high-risk/suspicious SMS received
 * per sender, so that when a call arrives we can check if it falls within
 * the 10-minute correlation window.
 *
 * No persistent storage — intentionally ephemeral (cleared on app restart).
 * No call content is recorded; only sender + timestamp are kept.
 */
object CallCorrelationStore {

    /** How long (ms) after a suspicious SMS we still flag a follow-up call. */
    const val CORRELATION_WINDOW_MS = 10 * 60 * 1000L  // 10 minutes

    data class SmsEvent(
        val sender: String,
        val riskScore: Int,
        val riskLabel: String,
        val receivedAt: Long = System.currentTimeMillis()
    )

    // Map of normalised sender → last suspicious/high-risk SMS event
    private val recentSuspiciousSmsList = mutableMapOf<String, SmsEvent>()

    /**
     * Called by SmsReceiver after analysis.
     * Only stores events that are SUSPICIOUS or HIGH_RISK.
     */
    fun recordSuspiciousSms(sender: String, riskScore: Int, riskLabel: String) {
        val key = normaliseSender(sender)
        recentSuspiciousSmsList[key] = SmsEvent(
            sender     = sender,
            riskScore  = riskScore,
            riskLabel  = riskLabel,
            receivedAt = System.currentTimeMillis()
        )
        // Clean up stale entries while we're here
        pruneExpired()
    }

    /**
     * Called by CallReceiver when an incoming call arrives.
     * Returns the matching SmsEvent if one exists within the correlation window,
     * or null if no recent suspicious SMS from this caller.
     */
    fun getCorrelatedSms(incomingNumber: String): SmsEvent? {
        pruneExpired()
        val key = normaliseSender(incomingNumber)
        return recentSuspiciousSmsList[key]
    }

    /**
     * Returns ALL active (non-expired) suspicious SMS events — used to
     * check if ANY recent suspicious SMS exists even if sender numbers differ
     * (scammers sometimes use different numbers for SMS vs call).
     */
    fun hasAnyRecentSuspiciousSms(): Boolean {
        pruneExpired()
        return recentSuspiciousSmsList.isNotEmpty()
    }

    /**
     * How many seconds ago the SMS arrived (for notification message).
     */
    fun secondsAgo(event: SmsEvent): Long =
        (System.currentTimeMillis() - event.receivedAt) / 1000L

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun normaliseSender(sender: String): String =
        sender.trim().replace(" ", "").replace("-", "")

    private fun pruneExpired() {
        val cutoff = System.currentTimeMillis() - CORRELATION_WINDOW_MS
        recentSuspiciousSmsList.entries.removeAll { it.value.receivedAt < cutoff }
    }
}
