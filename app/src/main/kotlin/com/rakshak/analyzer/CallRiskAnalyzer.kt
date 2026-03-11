package com.rakshak.analyzer

import com.rakshak.receiver.CallCorrelationStore
import com.rakshak.receiver.FraudSequenceStore

/**
 * CallRiskAnalyzer
 *
 * Privacy-safe call analysis — NEVER records or listens to call audio.
 * Only analyses the incoming caller ID (number/string).
 *
 * Five independent signals:
 *  1. Country code risk          — international / known scam-origin codes
 *  2. Hidden/private number      — no caller ID shown
 *  3. SMS–Call correlation       — same-number match (existing)
 *  4. Behavioral sequence        — different-number sequence (NEW: Pattern 1 SMS→Call)
 *  5. Community report count     — Firestore reports for this number
 *
 * Scoring:
 *   Hidden number                     +25
 *   Known high-risk country code      +30
 *   Other international (non-India)   +20
 *   Same-sender SMS correlation       +40
 *   Different-number sequence bonus   +15–30  ← NEW
 *   Community  1–4  reports           +10
 *   Community  5–9  reports           +20
 *   Community 10–19 reports           +30
 *   Community 20–49 reports           +35
 *   Community 50+   reports           +40
 */
object CallRiskAnalyzer {

    private val HIGH_RISK_COUNTRY_CODES = mapOf(
        "+1"   to "USA/Canada (frequent scam origin)",
        "+44"  to "UK (frequent scam origin)",
        "+61"  to "Australia (frequent scam origin)",
        "+92"  to "Pakistan",
        "+86"  to "China",
        "+234" to "Nigeria (fraud hotspot)",
        "+255" to "Tanzania",
        "+260" to "Zambia",
        "+216" to "Tunisia",
        "+212" to "Morocco",
        "+380" to "Ukraine",
        "+375" to "Belarus"
    )

    private const val INDIA_CODE = "+91"

    private fun communityScore(reportCount: Int): Int = when {
        reportCount >= 50 -> 40
        reportCount >= 20 -> 35
        reportCount >= 10 -> 30
        reportCount >= 5  -> 20
        reportCount >= 1  -> 10
        else              -> 0
    }

    data class CallAnalysisResult(
        val riskScore: Int,
        val riskLevel: RiskLevel,
        val isInternational: Boolean,
        val isHidden: Boolean,
        val countryDescription: String?,
        val correlatedSms: CallCorrelationStore.SmsEvent?,    // same-number match
        val sequenceMatch: FraudSequenceStore.SequenceMatch?, // different-number sequence
        val communityReportCount: Int,
        val communityScore: Int,
        val detectedSignals: List<String>,
        val summary: String
    )

    fun analyze(
        incomingNumber: String,
        communityReportCount: Int = 0
    ): CallAnalysisResult {

        val signals = mutableListOf<String>()
        var score   = 0

        val number   = incomingNumber.trim()
        val isHidden = number.isBlank() ||
                number == "Unknown" ||
                number == "Private" ||
                number == "Withheld" ||
                number == "-1"

        // ── Signal 1: Hidden number ───────────────────────────────
        if (isHidden) {
            score += 25
            signals.add("Hidden/Private caller ID")
        }

        // ── Signal 2: Country code risk ───────────────────────────
        var countryDesc: String? = null
        var isInternational      = false

        if (!isHidden) {
            val normalised  = normaliseNumber(number)
            val matchedCode = HIGH_RISK_COUNTRY_CODES.keys
                .sortedByDescending { it.length }
                .firstOrNull { normalised.startsWith(it) }

            if (matchedCode != null) {
                countryDesc     = HIGH_RISK_COUNTRY_CODES[matchedCode]
                score          += 30
                isInternational = true
                signals.add("High-risk country code $matchedCode — $countryDesc")
            } else if (normalised.startsWith("+") && !normalised.startsWith(INDIA_CODE)) {
                isInternational = true
                score          += 20
                val code        = normalised.take(4).trimEnd { it.isDigit() }
                countryDesc     = "International number ($code)"
                signals.add("International call — $countryDesc")
            }
        }

        // ── Signal 3: Same-number SMS correlation (existing) ──────
        val correlatedSms = if (!isHidden) {
            CallCorrelationStore.getCorrelatedSms(number)
        } else null

        if (correlatedSms != null) {
            val minsAgo = CallCorrelationStore.secondsAgo(correlatedSms) / 60
            score += 40
            signals.add(
                "⚠️ Suspicious SMS from same number ${minsAgo}min ago " +
                "(score: ${correlatedSms.riskScore} — ${correlatedSms.riskLabel})"
            )
        } else if (isHidden && CallCorrelationStore.hasAnyRecentSuspiciousSms()) {
            score += 20
            signals.add("Hidden caller shortly after a recent suspicious SMS")
        }

        // ── Signal 4: Behavioral sequence — different-number ──────
        // (Call was already recorded in CallReceiver before this runs)
        val seqMatch = FraudSequenceStore.checkPatternAfterCall()
        if (seqMatch != null && correlatedSms == null) {
            // Don't double-count if same-number match already fired
            score += seqMatch.bonusScore
            signals.add("🔗 ${seqMatch.patternName}: ${seqMatch.description}")
            if (seqMatch.isMultiActor) {
                signals.add("🚨 Multi-actor pattern: ${seqMatch.description}")
            }
        }

        // ── Signal 5: Community reports ───────────────────────────
        val commScore = communityScore(communityReportCount)
        if (communityReportCount > 0) {
            score += commScore
            val label = when {
                communityReportCount >= 50 -> "🚨 Known fraud number"
                communityReportCount >= 20 -> "⚠️ Widely reported"
                communityReportCount >= 10 -> "⚠️ Frequently reported"
                communityReportCount >= 5  -> "Reported multiple times"
                else                       -> "Reported by community"
            }
            signals.add("$label — $communityReportCount user report${if (communityReportCount > 1) "s" else ""}")
        }

        val finalScore = score.coerceAtMost(100)
        val riskLevel  = when {
            finalScore <= 30 -> RiskLevel.SAFE
            finalScore <= 65 -> RiskLevel.SUSPICIOUS
            else             -> RiskLevel.HIGH_RISK
        }

        return CallAnalysisResult(
            riskScore            = finalScore,
            riskLevel            = riskLevel,
            isInternational      = isInternational,
            isHidden             = isHidden,
            countryDescription   = countryDesc,
            correlatedSms        = correlatedSms,
            sequenceMatch        = seqMatch,
            communityReportCount = communityReportCount,
            communityScore       = commScore,
            detectedSignals      = signals,
            summary              = buildSummary(riskLevel, signals, finalScore, correlatedSms, seqMatch, communityReportCount)
        )
    }

    private fun normaliseNumber(number: String): String {
        val digits = number.replace(Regex("[^+\\d]"), "")
        return when {
            digits.startsWith("+")  -> digits
            digits.startsWith("00") -> "+" + digits.drop(2)
            digits.length == 10     -> "+91$digits"
            else                    -> digits
        }
    }

    private fun buildSummary(
        level: RiskLevel,
        signals: List<String>,
        score: Int,
        correlatedSms: CallCorrelationStore.SmsEvent?,
        seqMatch: FraudSequenceStore.SequenceMatch?,
        communityCount: Int
    ): String {
        if (signals.isEmpty()) return "No suspicious signals detected."
        return when {
            communityCount >= 20 ->
                "🚨 Known fraud number — reported $communityCount times. Do NOT answer."
            seqMatch?.smsEvent?.smsType == FraudSequenceStore.SmsType.OTP ->
                "🚨 ${seqMatch.patternName} — Do NOT share this OTP with anyone."
            seqMatch?.smsEvent?.smsType == FraudSequenceStore.SmsType.PHISHING_LINK ->
                "🚨 ${seqMatch.patternName} — Do NOT tap the link sent to you."
            seqMatch != null ->
                "🚨 ${seqMatch.patternName} — Coordinated scam attempt detected."
            correlatedSms != null ->
                "🚨 Follow-up scam call — suspicious SMS ${
                    CallCorrelationStore.secondsAgo(correlatedSms) / 60
                }min ago."
            else ->
                "${level.label} ($score%) — ${signals.first()}"
        }
    }
}
